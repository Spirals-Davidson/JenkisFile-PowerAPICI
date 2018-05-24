package com.powerapi.json

class Iteration {
    Integer number
    double energy
    long time_begin
    long time_end
    List<PowerData> power_data

    Iteration(Integer number, double energy, long time_begin, long time_end, List<PowerData> power_data){
        this.number = number
        this.energy = energy
        this.time_begin = time_begin
        this.time_end = time_end
        this.power_data = power_data
    }

    Iteration(Integer number, double energy, long time_begin, long time_end){
        this.number = number
        this.energy = energy
        this.time_begin = time_begin
        this.time_end = time_end
    }
}