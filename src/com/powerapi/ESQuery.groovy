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

def csv2jsonPowerapidata(String powerapiDataCSV){
    def powerapiData = new PowerapiData(powerapiDataCSV)

    def header = new JsonBuilder()
    def content = new JsonBuilder()

    header.index(
            _index: "powerapi",
            _type: "doc",
            _timestamp: powerapiData.timestamp,
    )

    content(
            timestamp: powerapiData.timestamp,
            muid: powerapiData.muid,
            devices: powerapiData.devices,
            targets: powerapiData.targets,
            power: powerapiData.power
    )

    //println(JsonOutput.prettyPrint(header.toString()) + '\n' + JsonOutput.prettyPrint(content.toString()) + '\n')
    sendPOSTMessage("http://elasticsearch.app.projet-davidson.fr/_bulk", header.toString() + '\n' + content.toString() + '\n')
}

def csv2jsonTestdata(String testDataCSV){
    def testData = new TestData(testDataCSV)

    def header = new JsonBuilder()
    def content = new JsonBuilder()

    header.index(
            _index: "testdata",
            _type: "doc",
            _timestamp: testData.timestamp,
    )

    content(
            timestamp: testData.timestamp,
            testname: testData.testName,
            startOrEnd: testData.startOrEnd
    )

    //println(JsonOutput.prettyPrint(header.toString()) + '\n' + JsonOutput.prettyPrint(content.toString()) + '\n')
    sendPOSTMessage("http://elasticsearch.app.projet-davidson.fr/_bulk", header.toString() + '\n' + content.toString() + '\n')
}
/**
 * Convert CSV format to JSon format
 * @param CSVFile : The table CSV to convert
 */
def sendPowerapiCSV2ES(String CSVString) {
    def CSVFile = CSVString.split("mW")

    for (def i = 0; i < CSVFile.length; i++) {
        csv2jsonPowerapidata(CSVFile[i])
    }

    println("Data of powerapi are correctly send")
}
//sendPowerapiCSV2ES("muid=test;timestamp=1524489876920;targets=10991;devices=cpu;power=4900.0mW muid=testing;timestamp=1524489876921;targets=10991;devices=cpu;power=4900.0mW")

def sendTestCSV2ES(String CSVString){
    def CSVFile = CSVString.split("\n")

    for (def i = 0; i < CSVFile.length; i++) {
        csv2jsonTestdata(CSVFile[i])
    }
    println("Data of test are correctly send")
}
sendTestCSV2ES("timestamp=1524489876923;testname=createhotel")

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
