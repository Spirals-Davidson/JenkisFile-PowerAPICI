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
        //Need the \\\\\ to pipeline
        json += "\\\"" + secParsing[0] + "\\\":\\\"" + secParsing[1] + "\\\","
    }
    //Retire la virgule de fin
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

/**
 * Convert CSV format to JSon format
 * @param CSVFile : The table CSV to convert
 * TODO : String[] not in this function
 */
def csv2jsonString(String CSVString) {
    def CSVFile = CSVString.split("mW")

    def json = "{"
    for (def i = 0; i < CSVFile.length - 1; i++) { // TODO : I don't know why but need the -1 --'
        //Need the \\\\\ to pipeline
        json += "\\\"time\\\":" + csv2json(CSVFile[i]) + ","
    }

    json = json.substring(0, json.length() - 1)
    println(json)
    return json + "}"
}

csv2jsonString("muid=72e9d91f-0b77-4d48-a75c-beeef833a663;timestamp=1524489876920;targets=10991;devices=cpu;power=4900.0 mW" +
        "muid=72e9d91f-0b77-4d48-a75c-beeef833a663;timestamp=1524489876920;targets=10991;devices=cpu;power=4900.0 mWmuid=72e9d91f-0b77-4d48-a75c-beeef833a663;timestamp=1524489876920;targets=10991;devices=cpu;power=4900.0 mW")

//csv2jsonFile("C:\\Users\\Admin\\Desktop\\dev\\gitproject\\JenkisFile-PowerAPICI\\resources\\com\\powerapi\\test.csv")
/**
 * Send CSV format to elasticSearch after have transform CSV to JSON
 * @param CSVFile : The CSV to send
 * @param path_index : The index to send
 */
def sendCSV2ES(String CSVFile, String path_index) {

}

/**
 * Send JSON format to ES
 * @param JSON
 * @param path_index : The index to send
 */
def sendJSon2ES(String JSON, String path_index) {

}