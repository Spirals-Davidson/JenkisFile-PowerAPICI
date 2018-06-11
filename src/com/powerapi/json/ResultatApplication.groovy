package com.powerapi.json

class ResultatApplication {
    long timestamp
    String branch
    String build_name
    /**
     * Energy total of the project
     */
    double energy
    String app_name
    long duration
    String commit_name
    List<Classe> classes
    String scm_url

    ResultatApplication(long timestamp, String branch, String build_name, double energy, String app_name, long duration, String commit_name, List<Classe> classes){
        this.timestamp = timestamp
        this.branch = branch
        this.build_name = build_name
        this.energy = energy
        this.app_name = app_name
        this.duration = duration
        this.commit_name = commit_name
        this.classes = classes
    }

    ResultatApplication(long timestamp, String branch, String build_name, String commit_name, String app_name, scm_url){
        this.timestamp = timestamp
        this.branch = branch
        this.build_name = build_name
        this.commit_name = commit_name
        this.app_name = app_name
        this.scm_url = scm_url
    }
}