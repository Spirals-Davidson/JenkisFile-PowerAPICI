package com.powerapi

import com.powerapi.converter.Converter
import com.powerapi.json.Iteration
import com.powerapi.json.Methods
import com.powerapi.json.PowerData
import com.powerapi.json.ResultatApplication
import com.powerapi.math.Math
import groovy.json.JsonBuilder
import groovy.json.JsonOutput

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
    double timeBefore
    double timeAfter
    double timeFirst
    double timeLast
    Double powerBefore
    Double powerAfter
    Double powerFirst
    Double powerLast
    def powerList = new ArrayList<>()
    def timeList = new ArrayList<>()
    def totalEnergy, estimatedEnergyFromBeforeToFirst, estimatedEnergyFromLastToAfter, estimatedEnergyFromBeginToFirst, estimatedEnergyFromLastToEnd, allPowerapi

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

            if(allPowerapi.isEmpty()){
                println("Nom du test vide: "+ test.testName)
            }

            for (PowerapiData papiD : allPowerapi) {
                powerList.add(papiD.power)
                timeList.add(papiD.timestamp)
            }

            timeFirst = timeList.first()
            powerFirst = powerList.first()
            timeLast = timeList.last()
            powerLast = powerList.last()

            //application de la formule
            estimatedEnergyFromBeforeToFirst = Math.convertToJoule((powerBefore + powerFirst) / 2, (double) timeFirst - timeBefore)
            estimatedEnergyFromLastToAfter = Math.convertToJoule((powerLast + powerAfter) / 2, (double) timeAfter - timeLast)
            estimatedEnergyFromBeginToFirst = (estimatedEnergyFromBeforeToFirst * (timeFirst - test.timeBeginTest)) / Constants.FREQUENCY
            estimatedEnergyFromLastToEnd = (estimatedEnergyFromLastToAfter * (test.timeEndTest - timeLast)) / Constants.FREQUENCY
            totalEnergy = estimatedEnergyFromBeginToFirst + test.energy + estimatedEnergyFromLastToEnd
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

    for(int i=0; i<powerapiCSV.size(); i++){
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
    sendPOSTMessage(Constants.ELASTIC_BULK_PATH, jsonToSend)
}