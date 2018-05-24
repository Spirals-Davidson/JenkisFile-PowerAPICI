package com.powerapi.math

class Math {

    def static convertToJoule(double averagePowerInMilliWatts, double testDurationInMs) {

        double averagePowerInWatt = averagePowerInMilliWatts / 1000
        double durationInSec = testDurationInMs / 1000

        return averagePowerInWatt * durationInSec
    }
}
