package com.powerapi

class PowerapiCI {
    double power
    long timestamp
    String testName
    long timeBeginTest
    long timeEndTest
    long testDuration
    double energy

     PowerapiCI(double power, long timestamp, String testName, long timeBeginTest, long timeEndTest, long testDuration, double energy) {
        this.power = power
        this.timestamp = timestamp
        this.testName = testName
        this.timeBeginTest = timeBeginTest
        this.timeEndTest = timeEndTest
        this.testDuration = testDuration
        this.energy = energy
    }
}

