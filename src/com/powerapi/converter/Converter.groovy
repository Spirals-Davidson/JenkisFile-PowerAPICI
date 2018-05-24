package com.powerapi.converter

import com.powerapi.PowerapiCI
import com.powerapi.json.Iteration
import com.powerapi.json.Methods
import com.powerapi.json.PowerData
import com.powerapi.json.ResultatApplication
import groovy.json.JsonBuilder

class Converter {

    /**
     * Transform TestCSV to Json and send data on testdata index
     * @param testDataCSV test CSV string
     */
    def static resultatApplicationToJson(ResultatApplication resultatApplication) {

/*
    List<PowerData> powerData = new ArrayList<>()
    powerData.add(new PowerData(10, 100))
    powerData.add(new PowerData(15, 150))
    List<PowerData> powerData1 = new ArrayList<>()
    powerData1.add(new PowerData(15, 10))
    powerData1.add(new PowerData(30, 15))

    List<Iteration> iterations = new ArrayList<>()
    iterations.add(new Iteration(1, (double) ((10.0 + 15.0) / 2), 120l, 145l, powerData))
    iterations.add(new Iteration(2, (double) ((15.0 + 30.0) / 2), 130l, 140l, powerData1))

    Methods method1 = new Methods("suitefibo", 15, 120, iterations)
    Methods method2 = new Methods("shouldmachin", 10, 150, iterations)

    List<Methods> methods = new ArrayList<>()
    methods.add(method1)
    methods.add(method2)

    ResultatApplication resultatApplication = new ResultatApplication(150l, "branch", "build", 12d, "app", 150l, "commit", methods)
*/

        def content = new JsonBuilder()
        content(
                timestamp: resultatApplication.timestamp,
                branch: resultatApplication.branch,
                build_name: resultatApplication.build_name,
                energy: resultatApplication.energy,
                app_name: resultatApplication.app_name,
                duration: resultatApplication.duration,
                commit_name: resultatApplication.commit_name,
                methods: resultatApplication.methods.collect {
                    Methods m ->
                        [name      : m.name,
                         energy    : m.energy,
                         duration  : m.duration,
                         iterations: m.iterations.collect {
                             Iteration i ->
                                 [n         : i.number,
                                  energy    : i.energy,
                                  time_begin: i.time_begin,
                                  time_end  : i.time_end,
                                  power_data: i.power_data.collect {
                                      PowerData p ->
                                          [power    : p.power,
                                           timestamp: p.timestamp]
                                  }]
                         }]
                }
        )
        return content.toString() + '\n'
    }

    /**
     * Transform TestCSV to Json and send data on testdata index
     * @param testDataCSV test CSV string
     */
    def static mapPowerapiCItoJson(PowerapiCI powerapiCI) {
        def content = new JsonBuilder()
        content(
                power: powerapiCI.power,
                timestamp: powerapiCI.timestamp,
                appName: powerapiCI.appName,
                testName: powerapiCI.testName,
                timeBeginTest: powerapiCI.timeBeginTest,
                timeEndTest: powerapiCI.timeEndTest,
                commitName: powerapiCI.commitName,
                testDuration: powerapiCI.testDuration,
                energy: powerapiCI.energy
        )
        return content.toString() + '\n'
    }

    def static fillResultatApplication(ResultatApplication resultatApplication, List<PowerapiCI> powerapiCIList) {
        //total energy of the project
        String lastTestName = ""
        double energy = 0.0
        for (def papici : powerapiCIList) {
            if (papici.testName != lastTestName) {
                energy += papici.energy
            }
        }

        resultatApplication.energy = energy

        //duration
        resultatApplication.duration = powerapiCIList.max { it.timeEndTest }.timeEndTest - powerapiCIList.min {
            it.timeBeginTest
        }.timeBeginTest

        //methods
        List<Methods> methods = new ArrayList<>()
        Methods newMethods

        int cpt = 1

        for (def papici : powerapiCIList) {
            if (papici.testName != lastTestName) {
                newMethods = new Methods(papici.testName, (papici.timeEndTest - papici.timeBeginTest))
                List<Iteration> iterations = new ArrayList<>()
                cpt = 1

                //TODO Boucle sur toutes les iterations
                Iteration iteration = new Iteration(cpt++, papici.energy, papici.timeBeginTest, papici.timeEndTest)

                def papiSameName = powerapiCIList.findAll { it.testName == papici.testName }


                List<PowerData> powerDatal = new ArrayList<>()
                for (def papici1 : papiSameName) {
                    powerDatal.add(new PowerData(papici1.power, papici1.timestamp))
                }
                iteration.power_data = powerDatal
                iterations.add(iteration)

                newMethods.iterations = iterations
                newMethods.energy = (newMethods.iterations.sum { Iteration iter -> iter.energy }) / newMethods.iterations.size()
                methods.add(newMethods)
            }
            lastTestName = papici.testName
        }

        resultatApplication.methods = methods
        return resultatApplication
    }
}