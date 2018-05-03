package com.powerapi

class PowerapiCI {
    double power
    long timestamp
    String appName
    String testName
    long timeBeginApp
    long timeEndApp
    long timeBeginTest
    long timeEndTest

    PowerapiCI(double power, long timestamp, String appName, String testName, long timeBeginApp, long timeEndApp, long timeBeginTest, long timeEndTest) {
        this.power = power
        this.timestamp = timestamp
        this.appName = appName
        this.testName = testName
        this.timeBeginApp = timeBeginApp
        this.timeEndApp = timeEndApp
        this.timeBeginTest = timeBeginTest
        this.timeEndTest = timeEndTest
    }
}

