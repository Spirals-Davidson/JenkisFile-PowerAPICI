package com.powerapi.json

/**
 * Class to build json
 */
class Methods {
    /**
     * Name of the test
     */
    String name
    /**
     * Average energy of this test
     */
    double energy
    long duration
    List<Iteration> iterations

    Methods(String name, double energy, long duration, List<Iteration> iterations){
        this.name = name
        this.energy = energy
        this.duration = duration
        this.iterations = iterations
    }

    Methods(String name, long duration) {
        this.name = name
        this.duration = duration
    }
}
