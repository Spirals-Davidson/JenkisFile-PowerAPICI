package com.powerapi

import groovy.json.JsonBuilder
import javax.xml.xpath.*
import javax.xml.parsers.DocumentBuilderFactory
import groovy.json.JsonOutput


/**
 * Transform TestCSV to Json and send data on testdata index
 * @param testDataCSV test CSV string
 */
def static mapPowerapiCItoJson(PowerapiCI powerapiCI) {
    def content = new JsonBuilder()
    content(
            power: powerapiCI.power,
            timestamp: powerapiCI.timestamp,
            appName: powerapiCI.appName,
            testName: powerapiCI.testName,
            timeBeginTest: powerapiCI.timeBeginTest,
            timeEndTest: powerapiCI.timeEndTest,
            commitName: powerapiCI.commitName,
            testDuration: powerapiCI.testDuration,
            energy: powerapiCI.energy
    )
    return content.toString() + '\n'
}

/**
 * Send Post data to an url
 * @param url the target to send
 * @param queryString the query to send
 */
def sendPOSTMessage(String url, String queryString) {
    def baseUrl = new URL(url)

    HttpURLConnection connection = (HttpURLConnection) baseUrl.openConnection()
    connection.setRequestProperty("Content-Type", "application/x-ndjson")

    connection.requestMethod = 'POST'
    connection.doOutput = true

    byte[] postDataBytes = queryString.getBytes("UTF-8")
    connection.getOutputStream().write(postDataBytes)


    if (!(connection.responseCode < HttpURLConnection.HTTP_BAD_REQUEST)) {
        println("Youps.. Une erreur est survenue lors de l'envoie d'une donnée!")
    }
}

/**
 * Aggregate two lists to return list<PowerapiCI>
 * @param powerapiList list of PowerapiData
 * @param testList list of TestData
 * @param commitName the name of current commit
 * @return list <PowerapiCI>
 */
def
static findListPowerapiCI(List<PowerapiData> powerapiList, List<TestData> testList, String commitName, String appName) {
    List<PowerapiCI> powerapiCIList = new ArrayList<>()
    def powerList = new ArrayList<>()

    while (!testList.isEmpty()) {
        powerList.clear()
        def endTest = testList.pop()
        def beginTest = testList.find { it.testName == endTest.testName }
        testList.remove(beginTest)

        if (beginTest.timestamp > endTest.timestamp) {
            def tmp = beginTest
            beginTest = endTest
            endTest = tmp
        }

        def testDurationInMs = endTest.timestamp - beginTest.timestamp

        def allPowerapi = powerapiList.findAll({
            it.timestamp >= beginTest.timestamp && it.timestamp <= endTest.timestamp
        })

        for (PowerapiData papiD : allPowerapi) {
            powerList.add(papiD.power)
        }

        if (powerList.size() != 0) {
            def sumPowers = 0
            for (def power : powerList) {
                sumPowers += power
            }

            def averagePowerInMilliWatts = sumPowers / powerList.size()
            def energy = convertToJoule(averagePowerInMilliWatts, (double) testDurationInMs)

            for (PowerapiData papiD : allPowerapi) {
                powerapiCIList.add(new PowerapiCI(papiD.power, papiD.timestamp, appName, beginTest.testName, commitName, beginTest.timestamp, endTest.timestamp, testDurationInMs, energy))
            }
        } else { /* Si aucune mesure n'a été prise pour ce test */
            powerapiCIList.add(new PowerapiCI(0d, beginTest.timestamp, appName, beginTest.testName, commitName, beginTest.timestamp, endTest.timestamp, 0l, 0d))
        }
    }
    addEstimatedPowersFormTests(powerapiCIList, powerapiList)

    return powerapiCIList
}

def static addEstimatedPowersFormTests(List<PowerapiCI> powerapiCIList, List<PowerapiData> powerapiList) {
    def lastTestName = "begin"
    long timeBefore
    long timeAfter
    long timeFirst
    long timeLast
    Double powerBefore
    Double powerAfter
    Double powerFirst
    Double powerLast

    def powerList = new ArrayList<>()
    def timeList = new ArrayList<>()

    //powerapiList.forEach({println powerapiList.timestamp})
    powerapiList.sort()
    for (def test : powerapiCIList) {
        if (test.testName != lastTestName) {
            println test.testName
            powerBefore = 0
            powerAfter = Long.MAX_VALUE
            powerFirst = 0
            powerLast = 0
            powerList.clear()
            timeList.clear()

            for(def papid : powerapiList) {
                if (papid.timestamp < test.timeBeginTest) {
                    powerBefore = papid.power
                    timeBefore = papid.timestamp
                }
                if (papid.timestamp > test.timeEndTest) {
                    powerAfter = papid.power
                    timeAfter = papid.timestamp
                    break
                }
            }
            def allPowerapi = powerapiList.findAll({
                it.timestamp >= test.timeBeginTest && it.timestamp <= test.timeEndTest
            })

            for (PowerapiData papiD : allPowerapi) {
                powerList.add(papiD.power)
                timeList.add(papiD.timestamp)
            }

            timeFirst = timeList.first()
            powerFirst = powerList.first()

            timeLast = timeList.last()
            powerLast = powerList.last()
/*
            powerBefore = (Double)((PowerapiData)Collections.max(powerapiList.findAll { it.timestamp < test.timeBeginTest })).power
            powerAfter = (Double)((PowerapiData)Collections.min(powerapiList.findAll { it.timestamp > test.timeBeginTest })).power
*/
            // println "la valeur de temps precedente est " + timeBefore
            // println "le debut du test " + test.timeBeginTest
            // println "la premiere valeur de temps  est " + timeFirst
            // println "la derniere valeur de temps est " + timeLast
            // println "la fin du test " + test.timeEndTest
            // println "la valeur de temps suivante est " + timeAfter

            if(timeBefore <= test.timeBeginTest && test.timeBeginTest <= timeFirst && timeFirst <= timeLast && timeLast <= test.timeEndTest && test.timeEndTest <= timeAfter){
                println "before to begin " + (test.timeBeginTest - timeBefore)
                println "begin to first " + (timeFirst - test.timeBeginTest)
                println "first to last " + (timeLast - timeFirst)
                println "last to end " + (test.timeEndTest - timeLast)
                println "end to after " + (timeAfter - test.timeEndTest)
            }else{
                println "NOPE"
            }

            //application de la formule
            def averagePowerFromBeforeToFirstInMW = (powerBefore + powerFirst) / 2
            def durationFromBeforeToFirstInMs = timeFirst - timeBefore
            def averagePowerFromLastToAfterInMW = (powerLast + powerAfter) / 2
            def durationFromLastToAfterInMs = timeAfter - timeLast

            def estimatedEnergyFromBeforeToFirst = convertToJoule(averagePowerFromBeforeToFirstInMW, durationFromBeforeToFirstInMs)
            def estimatedEnergyFromLastToAfter = convertToJoule(averagePowerFromLastToAfterInMW, durationFromLastToAfterInMs)


            println "EstimatedEnergyFromBeforeToFirst " + estimatedEnergyFromBeforeToFirst
            println "EstimatedEnergyFromLastToAfter " + estimatedEnergyFromLastToAfter

            def durationFromBeginToFirst = timeFirst-test.timeBeginTest
            def durationFromLastToEnd = test.timeEndTest-timeLast

            println "durationFromBeginToFirst " + durationFromBeginToFirst
            println "durationFromLastToEnd " + durationFromLastToEnd

            def estimatedEnergyFromBeginToFirst = (estimatedEnergyFromBeforeToFirst*durationFromBeginToFirst) / 50
            def estimatedEnergyFromLastToEnd = (estimatedEnergyFromLastToAfter*durationFromLastToEnd) / 50

            println "estimatedEnergyFromBeginToFirst " + estimatedEnergyFromBeginToFirst
            println "estimatedEnergyFromLastToEnd " + estimatedEnergyFromLastToEnd

            def totalEnergy = estimatedEnergyFromBeginToFirst + test.energy + estimatedEnergyFromLastToEnd

            println "Old Energy : " + test.energy + " --- New Energy estimated : " + totalEnergy

            println "------------------------------------------------------------------"

        }
        lastTestName = test.testName
       // powerapiCIList.add(new PowerapiCI(papid.power, test.timestamp, appName, test.testName, commitName, test.timeBeginTest, test.timeEndTest, test.testDuration, test.energy))

    }

    return powerapiCIList
}

/**
 * Send data to ES at an index
 * @param functionConvert function witch convert List<?> to JSon
 * @param index the index where send data
 * @param list the list List<?> of your data to be send
 */
def sendDataByPackage(def functionConvert, String index, List list) {
    /* Create header to send data */
    def header = new JsonBuilder()
    header.index(
            _index: index,
            _type: "doc"
    )

    while (!list.isEmpty()) {
        def jsonToSend = ""
        def toSend = list.take(Constants.NB_PAQUET)
        list = list.drop(Constants.NB_PAQUET)

        for (def cvsData : toSend) {
            jsonToSend += header.toString() + '\n'
            jsonToSend += functionConvert(cvsData)
        }
        sendPOSTMessage(Constants.ELASTIC_BULK_PATH, jsonToSend)
    }
}

/**
 * Search into and XML String a query
 * @param xml The xml String
 * @param xpathQuery The query to search
 * @return
 */
def static processXml(String xml, String xpathQuery) {
    def xpath = XPathFactory.newInstance().newXPath()
    def builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    def inputStream = new ByteArrayInputStream(xml.bytes)
    def records = builder.parse(inputStream).documentElement
    xpath.evaluate(xpathQuery, records)
}

/**
 * Send data to elasticSearch
 * @param powerapiCSV
 * @param testCSV
 * @param commitName
 * @param appNameXML
 * @return
 */
def sendPowerapiAndTestCSV(String powerapiCSV, String testCSV, String commitName, String appNameXML) {
    def powerapi = powerapiCSV.split("mW").toList()
    List<PowerapiData> powerapiList = new ArrayList<>()
    powerapi.stream().each({ powerapiList.add(new PowerapiData(it)) })

    def test = testCSV.split("\n").toList()
    List<TestData> testList = new ArrayList<>()
    test.stream().each({ testList.add(new TestData(it)) })

    def appName = processXml(appNameXML, "//@name")
    def powerapiCIList = findListPowerapiCI(powerapiList, testList, commitName, appName)

    //sendDataByPackage({ PowerapiCI p -> mapPowerapiCItoJson(p) }, "powerapici", powerapiCIList)
    println("Data correctly sent")

}

def static convertToJoule(double averagePowerInMilliWatts, double testDurationInMs) {

    double averagePowerInWatt = averagePowerInMilliWatts / 1000
    double durationInSec = testDurationInMs / 1000

    return averagePowerInWatt * durationInSec
}

sendPowerapiAndTestCSV("muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393604583;targets=50434;devices=cpu;power=9800.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393604642;targets=50434;devices=cpu;power=20416.666666666668 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393604734;targets=50434;devices=cpu;power=16333.333333333332 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393604743;targets=50434;devices=cpu;power=0.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393604793;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393604843;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393604893;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393604943;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393604993;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393605043;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393605092;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393605143;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393605193;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393605243;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393605293;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393605343;targets=50434;devices=cpu;power=14700.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393605392;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393605442;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393605493;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393605542;targets=50434;devices=cpu;power=14700.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393605593;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393605643;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393605695;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393605743;targets=50434;devices=cpu;power=14700.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393605792;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393605843;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393605893;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393605943;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393605992;targets=50434;devices=cpu;power=29400.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393606043;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393606095;targets=50434;devices=cpu;power=16333.333333333332 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393606142;targets=50434;devices=cpu;power=30625.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393606192;targets=50434;devices=cpu;power=20416.666666666668 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393606243;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393606292;targets=50434;devices=cpu;power=16333.333333333332 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393606343;targets=50434;devices=cpu;power=30625.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393606393;targets=50434;devices=cpu;power=20416.666666666668 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393606442;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393606493;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393606542;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393606593;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393606642;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393606693;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393606742;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393606793;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393606842;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393606893;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393606943;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393606992;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393607042;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393607096;targets=50434;devices=cpu;power=29400.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393607143;targets=50434;devices=cpu;power=14700.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393607192;targets=50434;devices=cpu;power=20416.666666666668 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393607243;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393607292;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393607343;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393607393;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393607443;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393607492;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393607543;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393607592;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393607643;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393607693;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393607743;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393607793;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393607843;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393607893;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393607942;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393607993;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393608042;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393608093;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393608143;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393608193;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393608242;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393608294;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393608342;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393608393;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393608442;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393608493;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393608542;targets=50434;devices=cpu;power=29400.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393608593;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393608642;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393608693;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393608742;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393608793;targets=50434;devices=cpu;power=20416.666666666668 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393608843;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393608893;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393608942;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393608993;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393609043;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393609092;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393609142;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393609193;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393609243;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393609292;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393609343;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393609392;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393609443;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393609492;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393609543;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393609598;targets=50434;devices=cpu;power=29400.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393609643;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393609692;targets=50434;devices=cpu;power=16333.333333333332 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393609743;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393609793;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393609843;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393609893;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393609942;targets=50434;devices=cpu;power=30625.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393609993;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393610043;targets=50434;devices=cpu;power=16333.333333333332 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393610092;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393610143;targets=50434;devices=cpu;power=30625.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393610193;targets=50434;devices=cpu;power=20416.666666666668 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393610242;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393610293;targets=50434;devices=cpu;power=16333.333333333332 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393610343;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393610393;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393610442;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393610493;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393610543;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393610593;targets=50434;devices=cpu;power=30625.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393610643;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393610693;targets=50434;devices=cpu;power=20416.666666666668 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393610742;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393610793;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393610842;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393610893;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393610942;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393610993;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393611042;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393611093;targets=50434;devices=cpu;power=20416.666666666668 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393611143;targets=50434;devices=cpu;power=30625.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393611193;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393611242;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393611293;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393611342;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393611395;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393611442;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393611493;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393611542;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393611593;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393611642;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393611693;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393611742;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393611793;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393611842;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393611893;targets=50434;devices=cpu;power=29400.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393611942;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393611992;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393612043;targets=50434;devices=cpu;power=30625.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393612093;targets=50434;devices=cpu;power=20416.666666666668 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393612142;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393612193;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393612242;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393612293;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393612343;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393612392;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393612443;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393612493;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393612543;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393612593;targets=50434;devices=cpu;power=30625.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393612643;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393612692;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393612743;targets=50434;devices=cpu;power=14700.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393612792;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393612843;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393612893;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393612943;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393612992;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393613043;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393613092;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393613143;targets=50434;devices=cpu;power=29400.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393613193;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393613242;targets=50434;devices=cpu;power=30625.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393613293;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393613343;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393613392;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393613443;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393613492;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393613543;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393613592;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393613643;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393613692;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393613743;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393613792;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393613843;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393613892;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393613943;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393613992;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393614043;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393614092;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393614142;targets=50434;devices=cpu;power=20416.666666666668 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393614193;targets=50434;devices=cpu;power=30625.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393614243;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393614292;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393614343;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393614392;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393614443;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393614492;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393614543;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393614592;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393614643;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393614692;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393614743;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393614792;targets=50434;devices=cpu;power=16333.333333333332 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393614843;targets=50434;devices=cpu;power=30625.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393614892;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393614942;targets=50434;devices=cpu;power=29400.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393614993;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393615043;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393615092;targets=50434;devices=cpu;power=20416.666666666668 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393615143;targets=50434;devices=cpu;power=30625.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393615192;targets=50434;devices=cpu;power=16333.333333333332 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393615243;targets=50434;devices=cpu;power=30625.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393615292;targets=50434;devices=cpu;power=20416.666666666668 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393615343;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393615392;targets=50434;devices=cpu;power=29400.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393615443;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393615492;targets=50434;devices=cpu;power=16333.333333333332 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393615543;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393615592;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393615643;targets=50434;devices=cpu;power=30625.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393615692;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393615743;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393615792;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393615843;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393615893;targets=50434;devices=cpu;power=29400.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393615942;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393615993;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393616042;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393616093;targets=50434;devices=cpu;power=29400.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393616142;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393616193;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393616243;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393616296;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393616343;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393616392;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393616443;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393616496;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393616542;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393616593;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393616642;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393616693;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393616742;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393616793;targets=50434;devices=cpu;power=20416.666666666668 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393616843;targets=50434;devices=cpu;power=30625.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393616892;targets=50434;devices=cpu;power=20416.666666666668 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393616943;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393616992;targets=50434;devices=cpu;power=20416.666666666668 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393617043;targets=50434;devices=cpu;power=30625.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393617092;targets=50434;devices=cpu;power=20416.666666666668 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393617143;targets=50434;devices=cpu;power=30625.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393617192;targets=50434;devices=cpu;power=16333.333333333332 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393617243;targets=50434;devices=cpu;power=30625.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393617293;targets=50434;devices=cpu;power=20416.666666666668 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393617343;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393617392;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393617443;targets=50434;devices=cpu;power=20416.666666666668 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393617493;targets=50434;devices=cpu;power=30625.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393617542;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393617593;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393617642;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393617693;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393617742;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393617792;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393617845;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393617893;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393617942;targets=50434;devices=cpu;power=30625.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393617993;targets=50434;devices=cpu;power=20416.666666666668 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393618043;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393618093;targets=50434;devices=cpu;power=20416.666666666668 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393618143;targets=50434;devices=cpu;power=18375.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393618192;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393618243;targets=50434;devices=cpu;power=14700.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393618292;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393618343;targets=50434;devices=cpu;power=14700.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393618393;targets=50434;devices=cpu;power=16333.333333333332 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393618442;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393618493;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393618543;targets=50434;devices=cpu;power=36750.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393618592;targets=50434;devices=cpu;power=16333.333333333332 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393618644;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393618693;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393618743;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393618792;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393618843;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393618892;targets=50434;devices=cpu;power=29400.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393618943;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393618992;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393619043;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393619092;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393619142;targets=50434;devices=cpu;power=36750.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393619193;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393619243;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393619294;targets=50434;devices=cpu;power=14700.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393619342;targets=50434;devices=cpu;power=12250.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393619393;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393619443;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393619493;targets=50434;devices=cpu;power=20416.666666666668 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393619543;targets=50434;devices=cpu;power=30625.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393619592;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393619643;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393619693;targets=50434;devices=cpu;power=20416.666666666668 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393619742;targets=50434;devices=cpu;power=30625.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393619793;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393619843;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393619893;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393619942;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393619993;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393620043;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393620092;targets=50434;devices=cpu;power=20416.666666666668 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393620143;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393620193;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393620242;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393620293;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393620342;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393620393;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393620443;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393620493;targets=50434;devices=cpu;power=20416.666666666668 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393620542;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393620593;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393620642;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393620693;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393620742;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393620793;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393620842;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393620893;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393620942;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393620992;targets=50434;devices=cpu;power=29400.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393621042;targets=50434;devices=cpu;power=20416.666666666668 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393621093;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393621143;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393621192;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393621243;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393621293;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393621342;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393621393;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393621442;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393621493;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393621542;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393621593;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393621642;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393621693;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393621742;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393621793;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393621842;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393621893;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393621942;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393621993;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393622042;targets=50434;devices=cpu;power=30625.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393622093;targets=50434;devices=cpu;power=16333.333333333332 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393622142;targets=50434;devices=cpu;power=36750.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393622193;targets=50434;devices=cpu;power=16333.333333333332 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393622244;targets=50434;devices=cpu;power=30625.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393622293;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393622343;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393622392;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393622443;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393622492;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393622543;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393622592;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393622642;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393622693;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393622742;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393622793;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393622842;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393622893;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393622943;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393622992;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393623043;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393623092;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393623143;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393623192;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393623242;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393623293;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393623342;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393623393;targets=50434;devices=cpu;power=14700.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393623443;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393623492;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393623543;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393623592;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393623643;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393623693;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393623742;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393623793;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393623842;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393623893;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393623942;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393623993;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393624042;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393624093;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393624143;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393624192;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393624243;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393624292;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393624343;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393624392;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393624443;targets=50434;devices=cpu;power=30625.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393624492;targets=50434;devices=cpu;power=16333.333333333332 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393624543;targets=50434;devices=cpu;power=36750.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393624593;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393624642;targets=50434;devices=cpu;power=30625.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393624693;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393624743;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393624792;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393624843;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393624892;targets=50434;devices=cpu;power=19600.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393624943;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393624993;targets=50434;devices=cpu;power=24500.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393625042;targets=50434;devices=cpu;power=0.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393625093;targets=50434;devices=cpu;power=0.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393625143;targets=50434;devices=cpu;power=0.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393625192;targets=50434;devices=cpu;power=0.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393625243;targets=50434;devices=cpu;power=0.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393625293;targets=50434;devices=cpu;power=0.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393625342;targets=50434;devices=cpu;power=0.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393625393;targets=50434;devices=cpu;power=0.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393625443;targets=50434;devices=cpu;power=0.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393625492;targets=50434;devices=cpu;power=0.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393625543;targets=50434;devices=cpu;power=0.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393625593;targets=50434;devices=cpu;power=0.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393625642;targets=50434;devices=cpu;power=0.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393625693;targets=50434;devices=cpu;power=0.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393625743;targets=50434;devices=cpu;power=0.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393625792;targets=50434;devices=cpu;power=0.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393625843;targets=50434;devices=cpu;power=0.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393625893;targets=50434;devices=cpu;power=0.0 mW muid=a1e1ba62-512b-4a6b-9214-6d5b5ec34346;timestamp=1526393625942;targets=50434;devices=cpu;power=0.0 mW",
        "timestamp=1526393608113;testname=should_test_suite_fibonacci_use_puissance;startorend=start\n" +
                "timestamp=1526393624165;testname=should_test_suite_fibonacci_use_puissance;startorend=end\n" +
                "timestamp=1526393624165;testname=should_test_suite_fibonacci_courte;startorend=start\n" +
                "timestamp=1526393624220;testname=should_test_suite_fibonacci_courte;startorend=end\n" +
                "timestamp=1526393624221;testname=should_update_existing_hotel;startorend=start\n" +
                "timestamp=1526393624478;testname=should_update_existing_hotel;startorend=end\n" +
                "timestamp=1526393624478;testname=should_return_all_paginated_hotel;startorend=start\n" +
                "timestamp=1526393624592;testname=should_return_all_paginated_hotel;startorend=end\n" +
                "timestamp=1526393624593;testname=should_find_existing_hotel;startorend=start\n" +
                "timestamp=1526393624634;testname=should_find_existing_hotel;startorend=end\n" +
                "timestamp=1526393624634;testname=should_return_hotel_find_by_city;startorend=start\n" +
                "timestamp=1526393624728;testname=should_return_hotel_find_by_city;startorend=end\n" +
                "timestamp=1526393624728;testname=should_create_hotel;startorend=start\n" +
                "timestamp=1526393624781;testname=should_create_hotel;startorend=end\n" +
                "timestamp=1526393624782;testname=should_delete_existing_hotel;startorend=start\n" +
                "timestamp=1526393624829;testname=should_delete_existing_hotel;startorend=end",
        "commit",
        "<somthing name='coucou'></somthing>")

