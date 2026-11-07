package org.fog.test.perfeval.models;

public class TaskSpec {
    public int taskId;
    public int subTaskId;
    public int srcVm;
    public int dstVm;
    public double cpuLength;
    public double nwLength;

    public TaskSpec(int taskId, int subTaskId, int srcVm, int dstVm, double cpuLength, double nwLength) {
        this.taskId = taskId;
        this.subTaskId = subTaskId;
        this.srcVm = srcVm;
        this.dstVm = dstVm;
        this.cpuLength = cpuLength;
        this.nwLength = nwLength;
    }
}