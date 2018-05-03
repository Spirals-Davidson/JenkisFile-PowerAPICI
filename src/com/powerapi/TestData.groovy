package com.powerapi

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