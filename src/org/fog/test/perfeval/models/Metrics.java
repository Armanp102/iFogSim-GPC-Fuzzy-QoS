package org.fog.test.perfeval.models;

public class Metrics {
    public double memory;
    public double cost;
    public double energy;
    public double reliability;
    public double penalty;
    public double delay;
    public double responseTime;
    public double fitness;

    public Metrics(double memory, double cost, double energy, double reliability, 
                   double penalty, double delay, double responseTime, double fitness) {
        this.memory = memory;
        this.cost = cost;
        this.energy = energy;
        this.reliability = reliability;
        this.penalty = penalty;
        this.delay = delay;
        this.responseTime = responseTime;
        this.fitness = fitness;
    }
}