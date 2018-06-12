package com.powerapi.converter

import com.powerapi.Constants
import com.powerapi.PowerapiCI
import com.powerapi.json.Classe
import com.powerapi.json.Iteration
import com.powerapi.json.Methods
import com.powerapi.json.PowerData
import com.powerapi.json.ResultatApplication
import groovy.json.JsonBuilder
import sun.nio.cs.StandardCharsets

class Converter {

    /**
     * Transform TestCSV to Json and send data on testdata index
     * @param testDataCSV test CSV string
     */
    def static resultatApplicationToJson(ResultatApplication resultatApplication) {

        def content = new JsonBuilder()
        content(
                timestamp: resultatApplication.timestamp,
                branch: resultatApplication.branch,
                build_url: Constants.BUILD_URL + resultatApplication.branch + "/" + resultatApplication.build_name + "/pipeline",
                scm_url: resultatApplication.scm_url,
                build_name: resultatApplication.build_name,
                energy: resultatApplication.energy,
                app_name: resultatApplication.app_name,
                duration: resultatApplication.duration,
                commit_name: resultatApplication.commit_name,
                classes: resultatApplication.classes.collect {
                    Classe c ->
                        [name    : c.name,
                         energy  : c.energy,
                         duration: c.duration,
                         methods : c.methods.collect {
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
                         }]
                }

        )
        return content.toString() + '\n'
    }

    def
    static fillResultatApplication(ResultatApplication resultatApplication, List<List<PowerapiCI>> powerapiCIList, Map<String, String> classes) {
        List<Classe> classeL = new ArrayList<>()

        String lastTestName = ""
        String lastClassName = ""
        for (def papici : powerapiCIList.get(0)) {
            if (papici.testName != lastTestName) {
                if (classes.get(papici.testName) != lastClassName) {
                    lastClassName = classes.get(papici.testName)
                    classeL.add(new Classe(lastClassName))
                }
                List<Iteration> iterations = new ArrayList<>()
                int cpt = 1

                for (List<PowerapiCI> papiciL : powerapiCIList) {
                    List<PowerData> powerDatas = new ArrayList<>()
                    double averageEnergy = 0
                    long time_b = 0, time_e = 0
                    for (def papici1 : papiciL) {
                        if (papici1.testName == papici.testName) {
                            powerDatas.add(new PowerData(papici1.power, papici1.timestamp))
                            averageEnergy = papici1.energy
                            time_b = papici1.timeBeginTest
                            time_e = papici1.timeEndTest
                        }
                    }
                    iterations.add(new Iteration(cpt, averageEnergy, time_b, time_e, powerDatas))
                    cpt++
                }

                Methods newMethods = new Methods(papici.testName, (papici.timeEndTest - papici.timeBeginTest))
                newMethods.iterations = iterations
                newMethods.energy = (newMethods.iterations.sum { Iteration iter -> iter.energy }) / newMethods.iterations.size()

                classeL.find { it.name == classes.get(papici.testName) }.methods.add(newMethods)
            }
            lastTestName = papici.testName
        }


        classeL.each { c ->
            c.duration = (long) c.methods.sum { Methods m -> m.duration }
            c.energy = (double) c.methods.sum { Methods m -> m.energy }
        }

        resultatApplication.classes = classeL

        //Total Energy
        resultatApplication.energy = (double) resultatApplication.classes.sum { Classe m -> m.energy }

        //duration
        resultatApplication.duration = (long) resultatApplication.classes.sum { Classe m -> m.duration }

        return resultatApplication
    }
}