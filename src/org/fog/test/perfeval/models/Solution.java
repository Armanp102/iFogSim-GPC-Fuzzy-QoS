package org.fog.test.perfeval.models;

import java.util.Arrays;
import java.util.Random;

public class Solution {
    public int[] placement;
    public double[] position;
    public Metrics metrics;

    public Solution(int[] placement) {
        this.placement = Arrays.copyOf(placement, placement.length);
        this.position = new double[placement.length];
        for (int i = 0; i < placement.length; i++) {
            this.position[i] = placement[i];
        }
    }

    public Solution(double[] position, int pmCount) {
        this.position = Arrays.copyOf(position, position.length);
        this.placement = new int[position.length];
        updatePlacementFromPosition(pmCount);
    }

    public static Solution randomSolution(int vmCount, int pmCount, Random random) {
        int[] placement = new int[vmCount];
        for (int i = 0; i < vmCount; i++) {
            placement[i] = random.nextInt(pmCount);
        }
        return new Solution(placement);
    }

    public static Solution randomContinuousSolution(int vmCount, int pmCount, Random random) {
        double[] position = new double[vmCount];
        double varMin = 0.0;
        double varMax = pmCount - 1.0;
        for (int i = 0; i < vmCount; i++) {
            position[i] = varMin + ((varMax - varMin) * random.nextDouble());
        }
        return new Solution(position, pmCount);
    }

    public void updatePlacementFromPosition(int pmCount) {
        for (int i = 0; i < position.length; i++) {
            double bounded = Math.max(0.0, Math.min(pmCount - 1.0, position[i]));
            int pmIndex = (int) Math.round(bounded);
            if (pmIndex < 0) pmIndex = 0;
            if (pmIndex >= pmCount) pmIndex = pmCount - 1;
            placement[i] = pmIndex;
            position[i] = bounded;
        }
    }

    public void updatePositionFromPlacement() {
        if (position == null || position.length != placement.length) {
            position = new double[placement.length];
        }
        for (int i = 0; i < placement.length; i++) {
            position[i] = placement[i];
        }
    }

    public Solution copy() {
        Solution cloned = new Solution(Arrays.copyOf(this.placement, this.placement.length));
        if (this.position != null) {
            cloned.position = Arrays.copyOf(this.position, this.position.length);
        }
        cloned.metrics = this.metrics;
        return cloned;
    }
}