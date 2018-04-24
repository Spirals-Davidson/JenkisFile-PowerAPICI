package com.powerapi

/**
 * Convert CSV format to JSon format
 * @param CSVFile : The CSV to convert
 */
def csv2json(String CSVFile) {
    /**
     * Pour l'instant une string, par la suite un JSONBuilder???
     */
    String json = "{"
    String[] parsingCSV = CSVFile.split(";")

    for (String st : parsingCSV) {
        String[] secParsing = st.split("=")
        json += "\"" + secParsing[0] + "\":\"" + secParsing[1] + "\","
    }
    //Retire la virgule de fin
    json = json.substring(0, json.length() - 1)
    return json + "}"
}

/**
 * Convert CSV format to JSon format
 * @param CSVFile : The table CSV to convert
 * TODO : String[] not in this function
 */
def csv2jsonString(String CSVString) {
    def CSVFile = CSVString.split("mW")

    def json = "{"
    for (def i = 0; i < CSVFile.length - 1; i++) { // TODO : I don't know why but need the -1 --'
        //Need the \\\ to pipeline
        json += "\"time\":" + csv2json(CSVFile[i]) + ","
    }

    json = json.substring(0, json.length() - 1)

    return json + "}"
}

/**
 * Convert CSV format to JSon format
 * @param CSVFile : The table CSV to convert
 */
def csv2jsonFile(File CSVFile) {
    def json = "{"
    CSVFile.eachLine { line ->
        json += "\"time\":" + csv2json(line) + ","
    }
    json = json.substring(0, json.length() - 1)
    return json + "}"
}

//csv2jsonFile("C:\\Users\\Admin\\Desktop\\dev\\gitproject\\JenkisFile-PowerAPICI\\resources\\com\\powerapi\\test.csv")
/**
 * Send CSV format to elasticSearch after have transform CSV to JSON
 * @param CSV : The CSV to send
 * @param path_index : The index to send
 */
def sendCSV2ES(String path_index, String CSV) {
    def jsonFormat = csv2jsonString(CSV)

    def url = new URL(path_index)
    def http = url.openConnection()
    http.setDoOutput(true)
    http.setRequestMethod('PUT')
    http.setRequestProperty('User-agent', 'groovy script')

    def out = new OutputStreamWriter(http.outputStream)
    out.write(jsonFormat)
    out.close()

    println(http.inputStream) // read server response from it
}

sendCSV2ES("http://elasticsearch.app.projet-davidson.fr/powerapi/power/testing", "muid=test;timestamp=1524489876920;targets=10991;devices=cpu;power=4900.0 mW" +
        "muid=test;timestamp=1524489876920;targets=10991;devices=cpu;power=4900.0 mWmuid=72e9d91f-0b77-4d48-a75c-beeef833a663;timestamp=1524489876920;targets=10991;devices=cpu;power=4900.0 mW")
/**
 * Send JSON format to ES
 * @param JSON
 * @param path_index : The index to send
 */
def sendJSon2ES(String JSON, String path_index) {

}