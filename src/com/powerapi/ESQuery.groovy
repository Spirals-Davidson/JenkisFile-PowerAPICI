package com.powerapi

import groovy.json.JsonBuilder
import groovy.json.JsonOutput

class PowerapiData {
    String muid
    String devices
    String targets
    long timestamp
    Double power

    PowerapiData(String powerapiDataCSV) {
        String[] parsingCSV = powerapiDataCSV.split(";")
        for (String st : parsingCSV) {
            String[] secParsing = st.split("=")
            switch (secParsing[0]) {
                case "muid":
                    muid = secParsing[1]
                    break
                case "devices":
                    devices = secParsing[1]
                    break
                case "targets":
                    targets = secParsing[1]
                    break
                case "timestamp":
                    timestamp = Long.parseLong(secParsing[1])
                    break
                case "power":
                    power = Double.parseDouble(secParsing[1])
                    break
                case "default":
                    println("Default: " + secParsing[0])
            }
        }
    }
}

class TestData {
    long timestamp
    String testName
    String startOrEnd

    TestData(String testDataCSV) {
        String[] parsingCSV = testDataCSV.split(";")
        for (String st : parsingCSV) {
            String[] secParsing = st.split("=")
            switch (secParsing[0]) {
                case "timestamp":
                    timestamp = Long.parseLong(secParsing[1])
                    break
                case "testname":
                    testName = secParsing[1]
                    break
                case "startorend":
                    startOrEnd = secParsing[1]
                    break
            }
        }
    }
}

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
            timeBeginApp: powerapiCI.timeBeginApp,
            timeEndApp: powerapiCI.timeEndApp,
            timeBeginTest: powerapiCI.timeBeginTest,
            timeEndTest: powerapiCI.timeEndTest
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

def findListPowerapiCI(List<PowerapiData> powerapiCSV, List<TestData> testCSV) {
    List<PowerapiCI> powerapiCIList = new ArrayList<>()

    while (!testCSV.isEmpty()) {
        def beginTest = testCSV.pop()
        def endTest = testCSV.find { it.testName == beginTest.testName }
        testCSV.remove(endTest)

        if (beginTest.timestamp > endTest.timestamp) {
            def tmp = beginTest
            beginTest = endTest
            endTest = tmp
        }

        def allPowerapi = powerapiCSV.findAll({
            it.timestamp >= beginTest.timestamp && it.timestamp <= endTest.timestamp
        })

        //TODO : Add name app and timeBeginApp and End
        for (PowerapiData papiD : allPowerapi) {
            powerapiCIList.add(new PowerapiCI(papiD.power, papiD.timestamp, "appName", beginTest.testName, 0, 0, beginTest.timestamp, endTest.timestamp))
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

def sendPowerapiAndTestCSV(String powerapiCSV, String testCSV) {
    def powerapi = powerapiCSV.split("mW").toList()
    List<PowerapiData> powerapiList = new ArrayList<>()
    powerapi.stream().each({ powerapiList.add(new PowerapiData(it)) })

    def test = testCSV.split("\n").toList()
    List<TestData> testList = new ArrayList<>()
    test.stream().each({ testList.add(new TestData(it)) })

    List<PowerapiCI> powerapiCIList = findListPowerapiCI(powerapiList, testList)
    sendDataByPackage({ PowerapiCI p -> mapPowerapiCItoJson(p) }, "powerapiCI", powerapiCIList)
    println("Data correctly send")
}

sendPowerapiAndTestCSV("muid=test;timestamp=1524489877117;targets=10991;devices=cpu;power=4900.0mWmuid=testing;timestamp=1524489876928;targets=10991;devices=cpu;power=4900.0mW", "timestamp=1524489876923;testname=test1\ntimestamp=1524489877100;testname=test1\ntimestamp=1524489877110;testname=test2\ntimestamp=1524489877119;testname=test2")