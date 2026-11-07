package org.fog.test.perfeval.models;

import java.util.Locale;

public class FitnessWeights {
    public String name;
    public double memoryWeight;
    public double costWeight;
    public double energyWeight;
    public double reliabilityWeight;
    public double penaltyWeight;
    public double delayWeight;
    public double responseTimeWeight;
    public double costReference;
    public double penaltyReference;

    public FitnessWeights(String name, double memoryWeight, double costWeight, double energyWeight,
                          double reliabilityWeight, double penaltyWeight, double delayWeight, 
                          double responseTimeWeight, double costReference, double penaltyReference) {
        this.name = name;
        this.memoryWeight = memoryWeight;
        this.costWeight = costWeight;
        this.energyWeight = energyWeight;
        this.reliabilityWeight = reliabilityWeight;
        this.penaltyWeight = penaltyWeight;
        this.delayWeight = delayWeight;
        this.responseTimeWeight = responseTimeWeight;
        this.costReference = costReference;
        this.penaltyReference = penaltyReference;
        validate();
    }

    private void validate() {
        double sum = memoryWeight + costWeight + energyWeight + reliabilityWeight +
                     penaltyWeight + delayWeight + responseTimeWeight;
        if (Math.abs(sum - 1.0) > 0.000001) {
            throw new IllegalArgumentException("Fitness weights must sum to 1. Current sum = " + sum);
        }
        if (costReference <= 0.0 || penaltyReference <= 0.0) {
            throw new IllegalArgumentException("Cost and penalty references must be greater than zero.");
        }
    }

    public String description() {
        return String.format(Locale.US,
                "Weights => memory: %.2f, cost: %.2f, energy: %.2f, reliability_loss: %.2f, penalty: %.2f, delay: %.2f, response_time: %.2f | costRef: %.4f | penaltyRef: %.4f",
                memoryWeight, costWeight, energyWeight, reliabilityWeight, penaltyWeight, delayWeight, responseTimeWeight, costReference, penaltyReference);
    }

    public static FitnessWeights qosDefault() {
        return new FitnessWeights("QoS_Default", 0.12, 0.15, 0.10, 0.20, 0.15, 0.16, 0.12, 0.08, 1.00);
    }

    public static FitnessWeights latencyPriority() {
        return new FitnessWeights("Latency_Priority", 0.08, 0.12, 0.08, 0.18, 0.14, 0.22, 0.18, 0.08, 1.00);
    }

    public static FitnessWeights energyCostPriority() {
        return new FitnessWeights("Energy_Cost_Priority", 0.12, 0.20, 0.18, 0.16, 0.12, 0.12, 0.10, 0.08, 1.00);
    }

    public static FitnessWeights balanced() {
        return new FitnessWeights("Balanced", 0.14, 0.14, 0.14, 0.16, 0.14, 0.14, 0.14, 0.08, 1.00);
    }
}