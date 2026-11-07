package org.fog.test.perfeval.models;

public class ScenarioConfig {
    public String name;
    public int pmCount;
    public int vmCount;
    public int taskCount;
    public int subTaskPerTask;

    public ScenarioConfig(String name, int pmCount, int vmCount, int taskCount, int subTaskPerTask) {
        this.name = name;
        this.pmCount = pmCount;
        this.vmCount = vmCount;
        this.taskCount = taskCount;
        this.subTaskPerTask = subTaskPerTask;
    }
}