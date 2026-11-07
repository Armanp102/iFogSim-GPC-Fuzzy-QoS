package org.fog.test.perfeval.models;

public class VmSpec {
    public int id;
    public String name;
    public double requiredMips;
    public int allocatedRam;
    public double baseRamRatio;
    public double priority;
    public double delaySensitivity;

    public VmSpec(int id, String name, double requiredMips, int allocatedRam, 
                  double baseRamRatio, double priority, double delaySensitivity) {
        this.id = id;
        this.name = name;
        this.requiredMips = requiredMips;
        this.allocatedRam = allocatedRam;
        this.baseRamRatio = baseRamRatio;
        this.priority = priority;
        this.delaySensitivity = delaySensitivity;
    }
}