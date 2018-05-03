package com.powerapi

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