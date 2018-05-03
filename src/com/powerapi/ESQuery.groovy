package com.powerapi

import groovy.json.JsonBuilder
import groovy.json.JsonOutput

import java.sql.Timestamp
import java.text.DateFormat

/**
 * Transform PowerapiCSV to Json and send data on powerapi index
 * @param powerapiDataCSV powerapi CSV string
 */
def csv2jsonPowerapidata(String powerapiDataCSV) {
    def powerapiData = new PowerapiData(powerapiDataCSV)

    def content = new JsonBuilder()

    content(
            timestamp: powerapiData.timestamp,
            muid: powerapiData.muid,
            devices: powerapiData.devices,
            targets: powerapiData.targets,
            power: powerapiData.power
    )

    return content.toString() + '\n'
}

/**
 * Transform TestCSV to Json and send data on testdata index
 * @param testDataCSV test CSV string
 */
def csv2jsonTestdata(String testDataCSV) {
    def testData = new TestData(testDataCSV)

    def content = new JsonBuilder()
    content(
            timestamp: testData.timestamp,
            testname: testData.testName,
            startOrEnd: testData.startOrEnd
    )

    return content.toString() + '\n'
}

/**
 * Transform TestCSV to Json and send data on testdata index
 * @param testDataCSV test CSV string
 */
def mapPowerapiCItoJson(PowerapiCI powerapiCI) {
    def content = new JsonBuilder()
    content(
            power: powerapiCI.power,
            timestamp: powerapiCI.timestamp,
            appName: powerapiCI.appName,
            testName: powerapiCI.testName,
            timeBeginTest: powerapiCI.timeBeginTest,
            timeEndTest: powerapiCI.timeEndTest,
            commitName: powerapiCI.commitName
    )
    return content.toString() + '\n'
}

/**
 * Send data to ElasticSearch separatly by Constants.NB_PAQUET package
 * @param functionConvert the function to convert csv, the function need to wait String and return String
 * @param index the index where send the data
 * @param csvL csv to send
 */
def sendDataCSVByPackage(def functionConvert, String index, List<String> csvL) {
    /* Create header to send data */
    def header = new JsonBuilder()
    header.index(
            _index: index,
            _type: "doc"
    )

    while (!csvL.isEmpty()) {
        def jsonToSend = ""
        def toSend = csvL.take(Constants.NB_PAQUET)
        csvL = csvL.drop(Constants.NB_PAQUET)

        for (def cvsData : toSend) {
            jsonToSend += header.toString() + '\n'
            jsonToSend += functionConvert(cvsData)
        }
        sendPOSTMessage(Constants.ELASTIC_BULK_PATH, jsonToSend)
    }
}

/**
 * parse and send powerapi data to ElasticSearch
 * @param csvString the CSV to send
 */
def sendPowerapiCSV2ES(String csvString) {
    def csvFile = csvString.split("mW").toList()
    sendDataCSVByPackage({ String s -> csv2jsonPowerapidata(s) }, "powerapi", csvFile)
    println("Data of powerapi are correctly send")
}

//sendPowerapiCSV2ES("muid=test;timestamp=1524489876820;targets=10991;devices=cpu;power=4900.0mWmuid=testing;timestamp=1524489876921;targets=10991;devices=cpu;power=4900.0mW") //muid=testing;timestamp=1524489876921;targets=10991;devices=cpu;power=4900.0mW

/**
 * parse and send test data to ElasticSearch
 * @param csvString the CSV to send
 */
def sendTestCSV2ES(String csvString) {
    def csvFile = csvString.split("\n").toList()
    sendDataCSVByPackage({ String s -> csv2jsonTestdata(s) }, "testdata", csvFile)
    println("Data of test are correctly send")
}

/**
 * Send Post data to an url
 * @param url the target to send
 * @param queryString the query to send
 */
def sendPOSTMessage(String url, String queryString) {
    def baseUrl = new URL(url)

    HttpURLConnection connection = baseUrl.openConnection()
    connection.setRequestProperty("Content-Type", "application/x-ndjson")

    connection.requestMethod = 'POST'
    connection.doOutput = true

    byte[] postDataBytes = queryString.getBytes("UTF-8")
    connection.getOutputStream().write(postDataBytes)


    if (connection.responseCode < HttpURLConnection.HTTP_BAD_REQUEST) {
    } else {
        println("Youps.. Une erreur est survenu lors de l'envoie d'un donnée!")
    }
}

/**
 * Aggregate two lists to return list<PowerapiCI>
 * @param powerapiList list of PowerapiData
 * @param testList list of TestData
 * @param commitName the name of current commit
 * @return list<PowerapiCI>
 */
def findListPowerapiCI(List<PowerapiData> powerapiList, List<TestData> testList, String commitName, String appName) {
    List<PowerapiCI> powerapiCIList = new ArrayList<>()

    while (!testList.isEmpty()) {
        def endTest = testList.pop()
        def beginTest = testList.find { it.testName == endTest.testName }
        testList.remove(beginTest)

        if (beginTest.timestamp > endTest.timestamp) {
            def tmp = beginTest
            beginTest = endTest
            endTest = tmp
        }

        def allPowerapi = powerapiList.findAll({
            it.timestamp >= beginTest.timestamp && it.timestamp <= endTest.timestamp
        })

        def bool = false
        for (PowerapiData papiD : allPowerapi) {
            bool = true
            powerapiCIList.add(new PowerapiCI(papiD.power, papiD.timestamp, appName, beginTest.testName, commitName, beginTest.timestamp, endTest.timestamp))
        }
        if(!bool){
            powerapiCIList.add(new PowerapiCI(0, beginTest.timestamp, appName, beginTest.testName, commitName, beginTest.timestamp, endTest.timestamp))
        }
    }

    return powerapiCIList
}

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

def sendPowerapiAndTestCSV(String powerapiCSV, String testCSV, String commitName, String appName) {
    def powerapi = powerapiCSV.split("mW").toList()
    List<PowerapiData> powerapiList = new ArrayList<>()
    powerapi.stream().each({ powerapiList.add(new PowerapiData(it)) })

    def test = testCSV.split("\n").toList()
    List<TestData> testList = new ArrayList<>()
    test.stream().each({ testList.add(new TestData(it)) })

    List<PowerapiCI> powerapiCIList = findListPowerapiCI(powerapiList, testList, commitName, appName)
    for(PowerapiCI papici : powerapiCIList){
        println(papici.testName+ ": "+papici.power)
    }
    sendDataByPackage({ PowerapiCI p -> mapPowerapiCItoJson(p) }, "powerapici", powerapiCIList)
    println("Data correctly send")
}
//sendPowerapiAndTestCSV("muid=test;timestamp=1525336448587;targets=10991;devices=cpu;power=4900.0mWmuid=testing;timestamp=1524489876928;targets=10991;devices=cpu;power=4900.0mW", "timestamp=1525336448586;testname=test1\ntimestamp=1525336448588;testname=test1\ntimestamp=1524489877110;testname=test2\ntimestamp=1524489877119;testname=test2", "commit")
