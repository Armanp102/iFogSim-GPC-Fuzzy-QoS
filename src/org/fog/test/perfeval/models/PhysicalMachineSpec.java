package org.fog.test.perfeval.models;

public class PhysicalMachineSpec {
    public String name;
    public double mipsPerCore;
    public int cores;
    public int ram;
    public long bw;
    public double busyPower;
    public double idlePower;
    public double ratePerMips;
    public double baseReliability;

    public PhysicalMachineSpec(String name, double mipsPerCore, int cores, int ram, long bw, 
                               double busyPower, double idlePower, double ratePerMips, double baseReliability) {
        this.name = name;
        this.mipsPerCore = mipsPerCore;
        this.cores = cores;
        this.ram = ram;
        this.bw = bw;
        this.busyPower = busyPower;
        this.idlePower = idlePower;
        this.ratePerMips = ratePerMips;
        this.baseReliability = baseReliability;
    }

    public double totalMips() {
        return mipsPerCore * cores;
    }
}