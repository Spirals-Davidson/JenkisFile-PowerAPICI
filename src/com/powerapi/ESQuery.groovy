package com.powerapi

import groovy.json.JsonBuilder
import groovy.json.JsonOutput

class PowerapiData {
    String muid
    String devices
    String targets
    long timestamp
    Double power

    PowerapiData(String powerapiDataCSV){
        String[] parsingCSV = powerapiDataCSV.split(";")
        for (String st : parsingCSV) {
            String[] secParsing = st.split("=")
            switch (secParsing[0]){
                case "muid" :
                    muid = secParsing[1]
                    break
                case "devices" :
                    devices = secParsing[1]
                    break
                case "targets" :
                    targets = secParsing[1]
                    break
                case "timestamp" :
                    timestamp = Long.parseLong(secParsing[1])
                    break
                case "power" :
                    power = Double.parseDouble(secParsing[1])
                    break
                case "default" :
                    println("Default: "+secParsing[0])
            }
        }
    }
}

class TestData {
    long timestamp
    String testName
    String startOrEnd

    TestData(String testDataCSV){
        String[] parsingCSV = testDataCSV.split(";")
        for (String st : parsingCSV) {
            String[] secParsing = st.split("=")
            switch (secParsing[0]){
                case "timestamp" :
                    timestamp = Long.parseLong(secParsing[1])
                    break
                case "testname" :
                    testName = secParsing[1]
                    break
                case "startorend" :
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
def csv2jsonPowerapidata(String powerapiDataCSV){
    def powerapiData = new PowerapiData(powerapiDataCSV)

    def content = new JsonBuilder()

    content(
            timestamp: powerapiData.timestamp,
            muid: powerapiData.muid,
            devices: powerapiData.devices,
            targets: powerapiData.targets,
            power: powerapiData.power
    )

    //println(JsonOutput.prettyPrint(content.toString()) + '\n')
    return content.toString() + '\n'
}

/**
 * Transform TestCSV to Json and send data on testdata index
 * @param testDataCSV test CSV string
 */
def csv2jsonTestdata(String testDataCSV){
    def testData = new TestData(testDataCSV)

    def content = new JsonBuilder()
    content(
            timestamp: testData.timestamp,
            testname: testData.testName,
            startOrEnd: testData.startOrEnd
    )

    //println(JsonOutput.prettyPrint(JsonOutput.prettyPrint(content.toString()) + '\n')
    return content.toString() + '\n'
}

/**
 * parse and send powerapi data to ElasticSearch
 * @param CSVString the CSV to send
 */
def sendPowerapiCSV2ES(String CSVString) {
    def CSVFile = CSVString.split("mW")

    /* Create header to send data */
    def header = new JsonBuilder()
    header.index(
            _index: "powerapi",
            _type: "doc"
    )

    def jsonToSend = ""
    for (def i = 0; i < CSVFile.length; i++) {
        jsonToSend += header.toString()+'\n'
        jsonToSend += csv2jsonPowerapidata(CSVFile[i])
    }

    sendPOSTMessage("http://elasticsearch.app.projet-davidson.fr/_bulk", jsonToSend)
    println("Data of powerapi are correctly send")
}

sendPowerapiCSV2ES("muid=test;timestamp=1524489876820;targets=10991;devices=cpu;power=4900.0mWmuid=testing;timestamp=1524489876921;targets=10991;devices=cpu;power=4900.0mW") //muid=testing;timestamp=1524489876921;targets=10991;devices=cpu;power=4900.0mW

/**
 * parse and send test data to ElasticSearch
 * @param CSVString the CSV to send
 */
def sendTestCSV2ES(String CSVString){
    def CSVFile = CSVString.split("\n")

    /* Create header to send data */
    def header = new JsonBuilder()
    header.index(
            _index: "testdata",
            _type: "doc"
    )

    def jsonToSend = ""
    for (def i = 0; i < CSVFile.length; i++) {
        jsonToSend += header.toString() + '\n'
        jsonToSend += csv2jsonTestdata(CSVFile[i])
    }

    sendPOSTMessage("http://elasticsearch.app.projet-davidson.fr/_bulk", jsonToSend)
    println("Data of test are correctly send")
}
//sendTestCSV2ES("timestamp=1524489876923;testname=createhotel")

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