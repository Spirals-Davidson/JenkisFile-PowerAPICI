package com.powerapi.json

class Classe {
    String name
    double energy
    long duration
    List<Methods> methods

    Classe(String name, double energy, long duration, List<Methods> methods){
        this.name = name
        this.energy = energy
        this.duration = duration
        this.methods = methods
    }

    Classe(String name) {
        this.name = name
    }
}
