package org.fog.test.perfeval.models;

public class IterationResult {
    public int iteration;
    public Metrics metrics;
    public int[] bestPlacement;

    public IterationResult(int iteration, Metrics metrics, int[] bestPlacement) {
        this.iteration = iteration;
        this.metrics = metrics;
        this.bestPlacement = bestPlacement;
    }
}