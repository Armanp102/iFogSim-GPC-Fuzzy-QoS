package org.fog.test.perfeval.models;

import java.util.List;

public class RunAlgorithmResult {
    public String weightScenarioName;
    public int runIndex;
    public String algorithmName;
    public List<IterationResult> results;

    public RunAlgorithmResult(String weightScenarioName, int runIndex, String algorithmName, List<IterationResult> results) {
        this.weightScenarioName = weightScenarioName;
        this.runIndex = runIndex;
        this.algorithmName = algorithmName;
        this.results = results;
    }
}