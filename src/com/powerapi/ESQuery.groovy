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
            commitName: powerapiCI.commitName
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
        println("Youps.. Une erreur est survenu lors de l'envoie d'un donnée!")
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

        def sizeTable = powerapiCIList.size()
        for (PowerapiData papiD : allPowerapi) {
            powerapiCIList.add(new PowerapiCI(papiD.power, papiD.timestamp, appName, beginTest.testName, commitName, beginTest.timestamp, endTest.timestamp))
        }
        if(powerapiCIList.size() == sizeTable){ /* Si aucune mesure n'a était prise pour ce test */
            powerapiCIList.add(new PowerapiCI(0, beginTest.timestamp, appName, beginTest.testName, commitName, beginTest.timestamp, endTest.timestamp))
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
    List<PowerapiCI> powerapiCIList = findListPowerapiCI(powerapiList, testList, commitName, appName)

    sendDataByPackage({ PowerapiCI p -> mapPowerapiCItoJson(p) }, "powerapici", powerapiCIList)
    println("Data correctly send")
}

sendPowerapiAndTestCSV("muid=test;timestamp=1525336448587;targets=10991;devices=cpu;power=4900.0mWmuid=testing;timestamp=1524489876928;targets=10991;devices=cpu;power=4900.0mW", "timestamp=1525336448586;testname=test1\ntimestamp=1525336448588;testname=test1\ntimestamp=1524489877110;testname=test2\ntimestamp=1524489877119;testname=test2", "commit", "<somthing name='coucou'></somthing>")

