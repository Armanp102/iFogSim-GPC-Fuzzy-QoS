package org.fog.test.perfeval.models;

public class SummaryResult {
    public String weightScenarioName;
    public String algorithmName;
    public int runs;
    public Metrics metrics;

    public SummaryResult(String weightScenarioName, String algorithmName, int runs, Metrics metrics) {
        this.weightScenarioName = weightScenarioName;
        this.algorithmName = algorithmName;
        this.runs = runs;
        this.metrics = metrics;
    }
}