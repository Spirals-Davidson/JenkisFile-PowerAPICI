package com.powerapi

import com.powerapi.converter.Converter
import com.powerapi.json.ResultatApplication
import com.powerapi.math.Math
import groovy.json.JsonBuilder

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
    double timeFirst
    double timeLast
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

def sendPowerapiciData(long debutApp, String branch, String buildName, String commitName, String urlScm, List<String> powerapiCSV, List<String> testCSV, String XMLClasses) {
    if (powerapiCSV.isEmpty() || testCSV.isEmpty() || testCSV.size() != powerapiCSV.size()) {
        println "Listes vides ou pas de la même taille"
        return
    }

    String appName = urlScm.substring(urlScm.lastIndexOf("/")+1, urlScm.length()-4)
    Map<String, String> classes = parseSurefireXML(XMLClasses)

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

    ResultatApplication resultatApplication = new ResultatApplication(debutApp, branch, buildName, commitName, appName, urlScm)
    resultatApplication = Converter.fillResultatApplication(resultatApplication, powerapiCIList, classes)
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

    sendPOSTMessage(Constants.ELASTIC_BULK_PATH, jsonToSend)
}

def static parseSurefireXML(String xml){
    xml = "<test>"+xml+"</test>"

    Map<String, String> classes = new HashMap<>()
    def list = new XmlParser().parseText(xml)

    list.each {
        String classeName = it.attributes().name
        it.each {
            classes.put((String)it.attributes().name, classeName)
        }
    }

    return classes
}
String XML_report = "" +
        "<test>" +
        "<testsuite name=\"com.khoubyari.example.test.FiboTest\" time=\"19.331\" tests=\"2\" errors=\"0\" skipped=\"0\" failures=\"0\">\n" +
        "  <testcase name=\"should_test_suite_fibonacci_use_puissance\" classname=\"com.khoubyari.example.test.FiboTest\" time=\"19.315\"/>\n" +
        "  <testcase name=\"should_test_suite_fibonacci_courte\" classname=\"com.khoubyari.example.test.FiboTest\" time=\"0.016\"/>\n" +
        "</testsuite>\n" +
        "<testsuite name=\"com.khoubyari.example.test.HotelControllerTest\" time=\"0.741\" tests=\"6\" errors=\"0\" skipped=\"0\" failures=\"0\">\n" +
        "  <testcase name=\"should_update_existing_hotel\" classname=\"com.khoubyari.example.test.HotelControllerTest\" time=\"0.463\"/>\n" +
        "  <testcase name=\"should_return_all_paginated_hotel\" classname=\"com.khoubyari.example.test.HotelControllerTest\" time=\"0.099\"/>\n" +
        "  <testcase name=\"should_find_existing_hotel\" classname=\"com.khoubyari.example.test.HotelControllerTest\" time=\"0.026\"/>\n" +
        "  <testcase name=\"should_return_hotel_find_by_city\" classname=\"com.khoubyari.example.test.HotelControllerTest\" time=\"0.082\"/>\n" +
        "  <testcase name=\"should_create_hotel\" classname=\"com.khoubyari.example.test.HotelControllerTest\" time=\"0.034\"/>\n" +
        "  <testcase name=\"should_delete_existing_hotel\" classname=\"com.khoubyari.example.test.HotelControllerTest\" time=\"0.037\"/>\n" +
        "</testsuite>" +
        "</test>"

parseSurefireXML(XML_report)