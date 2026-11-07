package org.fog.test.perfeval.utils;

public class FuzzyScorer {

    public static double score(double priority, double delaySensitivity, double nodeLoad, double freeResources) {

        double[] priorityMembership = memberships0To10(priority);
        double[] delayMembership = memberships0To10(delaySensitivity);

        double resourceQuality = (0.55 * freeResources) + (0.45 * (100.0 - nodeLoad));
        resourceQuality = Math.max(0.0, Math.min(100.0, resourceQuality));

        double[] resourceMembership = memberships0To100(resourceQuality);
        double[] output = new double[4];

        for (int p = 0; p < 3; p++) {
            for (int d = 0; d < 3; d++) {
                for (int r = 0; r < 3; r++) {

                    double ruleStrength = Math.min(priorityMembership[p],
                            Math.min(delayMembership[d], resourceMembership[r]));

                    if (ruleStrength <= 0) {
                        continue;
                    }

                    int urgency = Math.max(p, d);
                    int outIndex;

                    if (r == 0) {
                        outIndex = (urgency == 2) ? 1 : 0;
                    } else if (r == 1) {
                        outIndex = (urgency == 2) ? 2 : 1;
                    } else {
                        outIndex = (urgency == 2) ? 3 : 2;
                    }

                    output[outIndex] = Math.max(output[outIndex], ruleStrength);
                }
            }
        }

        double[] crispValues = {20.0, 45.0, 70.0, 90.0};
        double numerator = 0;
        double denominator = 0;

        for (int i = 0; i < output.length; i++) {
            numerator += output[i] * crispValues[i];
            denominator += output[i];
        }

        if (denominator == 0) {
            return 50.0;
        }
        return numerator / denominator;
    }

    private static double[] memberships0To10(double value) {
        value = Math.max(0.0, Math.min(10.0, value));
        return new double[]{
                trapezoid(value, 0, 0, 2.5, 5),
                triangle(value, 2.5, 5, 7.5),
                trapezoid(value, 5, 7.5, 10, 10)
        };
    }

    private static double[] memberships0To100(double value) {
        value = Math.max(0.0, Math.min(100.0, value));
        return new double[]{
                trapezoid(value, 0, 0, 25, 50),
                triangle(value, 25, 50, 75),
                trapezoid(value, 50, 75, 100, 100)
        };
    }

    private static double triangle(double x, double a, double b, double c) {
        if (x <= a || x >= c) return 0.0;
        if (x == b) return 1.0;
        if (x < b) return (x - a) / (b - a);
        return (c - x) / (c - b);
    }

    private static double trapezoid(double x, double a, double b, double c, double d) {
        if (x < a || x > d) return 0.0;
        if (x >= b && x <= c) return 1.0;
        if (x >= a && x < b) return (b == a) ? 1.0 : (x - a) / (b - a);
        if (x > c && x <= d) return (d == c) ? 1.0 : (d - x) / (d - c);
        return 0.0;
    }
}