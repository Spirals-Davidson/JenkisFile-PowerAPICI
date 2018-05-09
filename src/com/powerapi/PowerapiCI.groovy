package com.powerapi

class PowerapiCI {
    double power
    long timestamp
    String appName
    String testName
    String commitName
    long timeBeginTest
    long timeEndTest
    long testDuration
    double energy

    com.powerapi.PowerapiCI java.lang.Double java.lang.Long java.lang.String java.lang.String java.lang.String java.lang.Long java.lang.Long java.lang.Long java.lang.Long
    PowerapiCI(double power, long timestamp, String appName, String testName, String commitName, long timeBeginTest, long timeEndTest, long testDuration, double energy) {
        this.power = power
        this.timestamp = timestamp
        this.appName = appName
        this.testName = testName
        this.timeBeginTest = timeBeginTest
        this.timeEndTest = timeEndTest
        this.commitName = commitName
        this.testDuration = testDuration
        this.energy = energy
    }
}

