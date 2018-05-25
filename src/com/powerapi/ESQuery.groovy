package com.powerapi

import com.powerapi.converter.Converter
import com.powerapi.json.ResultatApplication
import com.powerapi.math.Math
import groovy.json.JsonBuilder

import javax.xml.xpath.*
import javax.xml.parsers.DocumentBuilderFactory

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

    println("reponseCode: " + connection.responseCode)
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
static findListPowerapiCI(List<PowerapiData> powerapiList, List<TestData> testList) {
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
            def energy = Math.convertToJoule((double) averagePowerInMilliWatts, (double) testDurationInMs)

            for (PowerapiData papiD : allPowerapi) {
                powerapiCIList.add(new PowerapiCI(papiD.power, papiD.timestamp, beginTest.testName, beginTest.timestamp, endTest.timestamp, testDurationInMs, energy))
            }
        } else { /* Si aucune mesure n'a été prise pour ce test */
            powerapiCIList.add(new PowerapiCI(0d, beginTest.timestamp, beginTest.testName, beginTest.timestamp, endTest.timestamp, 0l, 0d))
        }
    }
    def newpowerapiCIList = addEstimatedEnergyFormTests(powerapiCIList, powerapiList)

    return newpowerapiCIList
}

def
static addEstimatedEnergyFormTests(List<PowerapiCI> powerapiCIList, List<PowerapiData> powerapiList) {
    def lastTestName = ""
    double timeBefore = 0
    double timeAfter = 0
    double timeFirst = 0
    double timeLast = 0
    Double powerBefore
    Double powerAfter
    Double powerFirst
    Double powerLast
    def powerList = new ArrayList<>()
    def timeList = new ArrayList<>()
    def totalEnergy, estimatedEnergyFromBeforeToFirst, estimatedEnergyFromLastToAfter, estimatedEnergyFromBeginToFirst, estimatedEnergyFromLastToEnd, estimatedEnergyFromFirstToAfter, estimatedEnergyFromLFirstToEnd, estimatedEnergyFromBeforeToAfter, estimatedEnergyFromBeginToEnd, allPowerapi

    powerapiList.sort()
    for (def test : powerapiCIList) {
        if (test.testName != lastTestName) {
            powerBefore = 0
            powerAfter = Long.MAX_VALUE
            powerList.clear()
            timeList.clear()

            for (def papid : powerapiList) {
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
            allPowerapi = powerapiList.findAll({
                it.timestamp >= test.timeBeginTest && it.timestamp <= test.timeEndTest
            })

            if (allPowerapi.isEmpty()) {
                println("Nom du test vide: " + test.testName)
            }

            for (PowerapiData papiD : allPowerapi) {
                powerList.add(papiD.power)
                timeList.add(papiD.timestamp)
            }

            if (powerList.size() != 0) {

                timeFirst = (double) timeList.first()
                powerFirst = (double) powerList.first()
                timeLast = (double) timeList.last()
                powerLast = (double) powerList.last()

                estimatedEnergyFromBeforeToFirst = Math.convertToJoule((powerBefore + powerFirst) / 2, (double) timeFirst - timeBefore)
                estimatedEnergyFromLastToAfter = Math.convertToJoule((powerLast + powerAfter) / 2, (double) timeAfter - timeLast)
                estimatedEnergyFromBeginToFirst = (estimatedEnergyFromBeforeToFirst * (timeFirst - test.timeBeginTest)) / Constants.FREQUENCY
                estimatedEnergyFromLastToEnd = (estimatedEnergyFromLastToAfter * (test.timeEndTest - timeLast)) / Constants.FREQUENCY
                totalEnergy = estimatedEnergyFromBeginToFirst + test.energy + estimatedEnergyFromLastToEnd

            }
            else if(powerList.size() == 1){

                timeFirst = (double) timeList.first()
                powerFirst = (double) powerList.first()

                estimatedEnergyFromBeforeToFirst = Math.convertToJoule((powerBefore + powerFirst) / 2, (double) timeFirst - timeBefore)
                estimatedEnergyFromFirstToAfter = Math.convertToJoule((powerFirst + powerAfter) / 2, (double) timeAfter - timeFirst)

                estimatedEnergyFromBeginToFirst = (estimatedEnergyFromBeforeToFirst * (timeFirst - test.timeBeginTest)) / Constants.FREQUENCY
                estimatedEnergyFromLFirstToEnd = (estimatedEnergyFromFirstToAfter * (test.timeEndTest - timeFirst)) / Constants.FREQUENCY
                totalEnergy = estimatedEnergyFromBeginToFirst + estimatedEnergyFromLFirstToEnd

            }
            else{
                estimatedEnergyFromBeforeToAfter = Math.convertToJoule((powerBefore + powerAfter) / 2, (double) timeAfter - timeBefore)
                estimatedEnergyFromBeginToEnd = (estimatedEnergyFromBeforeToAfter * (test.timeEndTest - test.timeBeginTest)) / Constants.FREQUENCY

                totalEnergy = estimatedEnergyFromBeginToEnd
            }

            for (def testid : powerapiCIList) {
                if (testid.testName == test.testName) {
                    testid.energy = totalEnergy
                }
            }
            lastTestName = test.testName
        }

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

//processXml("<somthing name='appName'></somthing>", "//@name")

def sendPowerapiciData(long debutApp, String branch, String buildName, String commitName, String appNameXML, List<String> powerapiCSV, List<String> testCSV) {
    if (powerapiCSV.isEmpty() || testCSV.isEmpty() || testCSV.size() != powerapiCSV.size()) {
        println "Listes vides ou pas de la même taille"
        return
    }
    List<List<PowerapiCI>> powerapiCIList = new ArrayList<>()

    for (int i = 0; i < powerapiCSV.size(); i++) {
        def powerapi = powerapiCSV.get(i).split("mW").toList()
        List<PowerapiData> powerapiList = new ArrayList<>()
        powerapi.stream().each({ powerapiList.add(new PowerapiData(it)) })

        def test = testCSV.get(i).split("\n").toList()
        List<TestData> testList = new ArrayList<>()
        test.stream().each({ testList.add(new TestData(it)) })

        powerapiCIList.add(findListPowerapiCI(powerapiList, testList))
    }

    ResultatApplication resultatApplication = new ResultatApplication(debutApp, branch, buildName, commitName, processXml(appNameXML, "//@name"))
    resultatApplication = Converter.fillResultatApplication(resultatApplication, powerapiCIList)
    sendResultat(Constants.ACTUAL_INDEX, resultatApplication)
    println("Data correctly sent")
}

def sendResultat(String index, ResultatApplication resultatApplication) {
    /* Create header to send data */
    def header = new JsonBuilder()
    header.index(
            _index: index,
            _type: "doc"
    )

    def jsonToSend = ""

    jsonToSend += header.toString() + '\n'
    jsonToSend += Converter.resultatApplicationToJson(resultatApplication).toString()

    //println JsonOutput.prettyPrint(jsonToSend.toString())
    //sendPOSTMessage(Constants.ELASTIC_BULK_PATH, jsonToSend)
}

List<String> dataCSV = new ArrayList<>()
dataCSV.add("muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233205777;targets=45658;devices=cpu;power=12250.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233205837;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233205906;targets=45658;devices=cpu;power=17500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233205927;targets=45658;devices=cpu;power=0.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233205987;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233206037;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233206087;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233206138;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233206188;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233206237;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233206287;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233206337;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233206387;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233206437;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233206487;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233206537;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233206587;targets=45658;devices=cpu;power=14700.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233206637;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233206687;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233206737;targets=45658;devices=cpu;power=16333.333333333332 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233206787;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233206837;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233206887;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233206937;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233206987;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233207037;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233207087;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233207137;targets=45658;devices=cpu;power=14700.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233207187;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233207237;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233207287;targets=45658;devices=cpu;power=14700.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233207337;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233207387;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233207437;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233207487;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233207537;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233207587;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233207637;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233207688;targets=45658;devices=cpu;power=16333.333333333332 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233207737;targets=45658;devices=cpu;power=30625.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233207787;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233207837;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233207887;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233207937;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233207987;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233208037;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233208087;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233208151;targets=45658;devices=cpu;power=29400.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233208177;targets=45658;devices=cpu;power=12250.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233208237;targets=45658;devices=cpu;power=29400.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233208287;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233208337;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233208387;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233208437;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233208487;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233208537;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233208587;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233208637;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233208687;targets=45658;devices=cpu;power=16333.333333333332 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233208737;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233208787;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233208837;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233208887;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233208937;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233208988;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233209037;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233209087;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233209137;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233209187;targets=45658;devices=cpu;power=20416.666666666668 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233209237;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233209287;targets=45658;devices=cpu;power=20416.666666666668 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233209337;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233209387;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233209437;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233209487;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233209537;targets=45658;devices=cpu;power=20416.666666666668 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233209587;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233209637;targets=45658;devices=cpu;power=20416.666666666668 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233209687;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233209737;targets=45658;devices=cpu;power=20416.666666666668 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233209787;targets=45658;devices=cpu;power=30625.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233209837;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233209887;targets=45658;devices=cpu;power=20416.666666666668 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233209937;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233209987;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233210037;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233210087;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233210137;targets=45658;devices=cpu;power=29400.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233210187;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233210237;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233210287;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233210337;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233210387;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233210437;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233210487;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233210537;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233210587;targets=45658;devices=cpu;power=30625.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233210637;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233210687;targets=45658;devices=cpu;power=20416.666666666668 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233210737;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233210787;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233210837;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233210887;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233210937;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233210987;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233211037;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233211087;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233211137;targets=45658;devices=cpu;power=20416.666666666668 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233211187;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233211237;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233211287;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233211337;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233211387;targets=45658;devices=cpu;power=14700.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233211437;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233211487;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233211537;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233211588;targets=45658;devices=cpu;power=20416.666666666668 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233211637;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233211687;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233211737;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233211787;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233211838;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233211887;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233211937;targets=45658;devices=cpu;power=20416.666666666668 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233211987;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233212037;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233212087;targets=45658;devices=cpu;power=30625.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233212137;targets=45658;devices=cpu;power=16333.333333333332 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233212187;targets=45658;devices=cpu;power=30625.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233212237;targets=45658;devices=cpu;power=16333.333333333332 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233212287;targets=45658;devices=cpu;power=30625.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233212337;targets=45658;devices=cpu;power=20416.666666666668 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233212387;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233212437;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233212487;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233212537;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233212587;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233212637;targets=45658;devices=cpu;power=20416.666666666668 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233212687;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233212738;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233212787;targets=45658;devices=cpu;power=29400.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233212837;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233212887;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233212937;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233212987;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233213037;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233213087;targets=45658;devices=cpu;power=20416.666666666668 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233213137;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233213187;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233213237;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233213287;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233213337;targets=45658;devices=cpu;power=16333.333333333332 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233213387;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233213437;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233213487;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233213537;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233213587;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233213637;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233213687;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233213737;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233213787;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233213837;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233213887;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233213937;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233213987;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233214037;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233214087;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233214137;targets=45658;devices=cpu;power=30625.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233214187;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233214237;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233214288;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233214337;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233214387;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233214437;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233214487;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233214537;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233214587;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233214638;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233214687;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233214737;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233214787;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233214840;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233214877;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233214937;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233214987;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233215037;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233215087;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233215137;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233215187;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233215242;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233215277;targets=45658;devices=cpu;power=32666.666666666664 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233215337;targets=45658;devices=cpu;power=20416.666666666668 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233215387;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233215437;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233215487;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233215537;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233215587;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233215637;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233215687;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233215740;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233215777;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233215837;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233215887;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233215937;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233215987;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233216037;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233216087;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233216137;targets=45658;devices=cpu;power=16333.333333333332 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233216187;targets=45658;devices=cpu;power=30625.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233216237;targets=45658;devices=cpu;power=20416.666666666668 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233216287;targets=45658;devices=cpu;power=30625.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233216337;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233216387;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233216437;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233216487;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233216537;targets=45658;devices=cpu;power=16333.333333333332 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233216587;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233216637;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233216687;targets=45658;devices=cpu;power=30625.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233216737;targets=45658;devices=cpu;power=20416.666666666668 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233216787;targets=45658;devices=cpu;power=30625.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233216837;targets=45658;devices=cpu;power=20416.666666666668 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233216887;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233216937;targets=45658;devices=cpu;power=20416.666666666668 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233216987;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233217037;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233217087;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233217137;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233217187;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233217237;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233217287;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233217337;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233217387;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233217437;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233217487;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233217537;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233217587;targets=45658;devices=cpu;power=36750.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233217637;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233217687;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233217739;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233217777;targets=45658;devices=cpu;power=14700.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233217837;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233217887;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233217937;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233217987;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233218037;targets=45658;devices=cpu;power=20416.666666666668 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233218087;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233218137;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233218188;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233218237;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233218287;targets=45658;devices=cpu;power=30625.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233218337;targets=45658;devices=cpu;power=16333.333333333332 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233218387;targets=45658;devices=cpu;power=30625.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233218437;targets=45658;devices=cpu;power=20416.666666666668 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233218487;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233218537;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233218587;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233218637;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233218687;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233218747;targets=45658;devices=cpu;power=34300.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233218787;targets=45658;devices=cpu;power=18375.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233218837;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233218887;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233218937;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233218987;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233219037;targets=45658;devices=cpu;power=16333.333333333332 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233219087;targets=45658;devices=cpu;power=36750.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233219137;targets=45658;devices=cpu;power=16333.333333333332 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233219187;targets=45658;devices=cpu;power=30625.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233219237;targets=45658;devices=cpu;power=20416.666666666668 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233219287;targets=45658;devices=cpu;power=30625.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233219337;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233219387;targets=45658;devices=cpu;power=30625.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233219437;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233219487;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233219537;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233219587;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233219637;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233219687;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233219737;targets=45658;devices=cpu;power=12250.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233219788;targets=45658;devices=cpu;power=14700.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233219837;targets=45658;devices=cpu;power=14700.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233219887;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233219937;targets=45658;devices=cpu;power=14700.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233219988;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233220037;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233220087;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233220137;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233220187;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233220240;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233220277;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233220337;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233220387;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233220437;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233220487;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233220537;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233220587;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233220637;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233220687;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233220736;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233220787;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233220837;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233220887;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233220937;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233220987;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233221037;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233221087;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233221137;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233221187;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233221237;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233221287;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233221337;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233221387;targets=45658;devices=cpu;power=29400.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233221437;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233221487;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233221537;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233221587;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233221637;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233221687;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233221737;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233221787;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233221837;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233221887;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233221937;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233221987;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233222037;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233222087;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233222137;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233222187;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233222237;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233222287;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233222337;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233222387;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233222437;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233222487;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233222537;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233222587;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233222637;targets=45658;devices=cpu;power=20416.666666666668 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233222687;targets=45658;devices=cpu;power=30625.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233222737;targets=45658;devices=cpu;power=20416.666666666668 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233222787;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233222837;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233222887;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233222937;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233222987;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233223037;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233223087;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233223137;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233223187;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233223237;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233223287;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233223337;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233223387;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233223437;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233223487;targets=45658;devices=cpu;power=30625.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233223537;targets=45658;devices=cpu;power=20416.666666666668 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233223587;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233223637;targets=45658;devices=cpu;power=20416.666666666668 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233223687;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233223737;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233223787;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233223837;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233223887;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233223937;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233223987;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233224037;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233224087;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233224137;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233224187;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233224237;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233224287;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233224337;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233224387;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233224437;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233224487;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233224537;targets=45658;devices=cpu;power=20416.666666666668 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233224587;targets=45658;devices=cpu;power=18375.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233224637;targets=45658;devices=cpu;power=20416.666666666668 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233224687;targets=45658;devices=cpu;power=30625.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233224737;targets=45658;devices=cpu;power=16333.333333333332 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233224787;targets=45658;devices=cpu;power=30625.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233224837;targets=45658;devices=cpu;power=20416.666666666668 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233224887;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233224937;targets=45658;devices=cpu;power=20416.666666666668 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233224988;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233225037;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233225087;targets=45658;devices=cpu;power=24500.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233225137;targets=45658;devices=cpu;power=19600.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233225187;targets=45658;devices=cpu;power=0.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233225237;targets=45658;devices=cpu;power=0.0 mW muid=d3bd441b-8c6d-464a-8c23-4d3a2b6a5d48;timestamp=1527233225287;targets=45658;devices=cpu;power=0.0 mW")

List<String> testCSV = new ArrayList<>()
testCSV.add("timestamp=1527233209600;testname=should_test_suite_fibonacci_use_puissance;startorend=start\n" +
        "timestamp=1527233224191;testname=should_test_suite_fibonacci_use_puissance;startorend=end\n" +
        "timestamp=1527233224191;testname=should_test_suite_fibonacci_courte;startorend=start\n" +
        "timestamp=1527233224235;testname=should_test_suite_fibonacci_courte;startorend=end\n" +
        "timestamp=1527233224235;testname=should_update_existing_hotel;startorend=start\n" +
        "timestamp=1527233224518;testname=should_update_existing_hotel;startorend=end\n" +
        "timestamp=1527233224518;testname=should_return_all_paginated_hotel;startorend=start\n" +
        "timestamp=1527233224646;testname=should_return_all_paginated_hotel;startorend=end\n" +
        "timestamp=1527233224647;testname=should_find_existing_hotel;startorend=start\n" +
        "timestamp=1527233224696;testname=should_find_existing_hotel;startorend=end\n" +
        "timestamp=1527233224697;testname=should_return_hotel_find_by_city;startorend=start\n" +
        "timestamp=1527233224783;testname=should_return_hotel_find_by_city;startorend=end\n" +
        "timestamp=1527233224789;testname=should_create_hotel;startorend=start\n" +
        "timestamp=1527233224830;testname=should_create_hotel;startorend=end\n" +
        "timestamp=1527233224830;testname=should_delete_existing_hotel;startorend=start\n" +
        "timestamp=1527233224879;testname=should_delete_existing_hotel;startorend=end")


sendPowerapiciData(130l, "master", "1870", "test", "<somthing name='appName'></somthing>", dataCSV, testCSV)