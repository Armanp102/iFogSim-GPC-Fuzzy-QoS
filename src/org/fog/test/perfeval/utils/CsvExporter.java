package org.fog.test.perfeval.utils;

import org.fog.test.perfeval.models.IterationResult;
import org.fog.test.perfeval.models.RunAlgorithmResult;
import org.fog.test.perfeval.models.SummaryResult;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class CsvExporter {

    public static void exportConvergenceDetails(String filePath, List<RunAlgorithmResult> allRunResults) {
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write("WeightScenario,Algorithm,RunIndex,Iteration,Fitness,Memory,Cost,Energy,ReliabilityLoss,Penalty,Delay,ResponseTime\n");

            for (RunAlgorithmResult runRes : allRunResults) {
                for (IterationResult iterRes : runRes.results) {
                    writer.write(String.format(Locale.US, "%s,%s,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f\n",
                            runRes.weightScenarioName,
                            runRes.algorithmName,
                            runRes.runIndex,
                            iterRes.iteration,
                            iterRes.metrics.fitness,
                            iterRes.metrics.memory,
                            iterRes.metrics.cost,
                            iterRes.metrics.energy,
                            iterRes.metrics.reliability,
                            iterRes.metrics.penalty,
                            iterRes.metrics.delay,
                            iterRes.metrics.responseTime
                    ));
                }
            }
            System.out.println(">> Convergence details successfully exported to: " + filePath);
        } catch (IOException e) {
            System.err.println("Error writing convergence CSV: " + e.getMessage());
        }
    }

    public static void exportFinalSummary(String filePath, List<SummaryResult> summaryResults) {
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write("WeightScenario,Algorithm,TotalRuns,AvgFitness,AvgMemory,AvgCost,AvgEnergy,AvgReliabilityLoss,AvgPenalty,AvgDelay,AvgResponseTime\n");

            for (SummaryResult sr : summaryResults) {
                writer.write(String.format(Locale.US, "%s,%s,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f\n",
                        sr.weightScenarioName,
                        sr.algorithmName,
                        sr.runs,
                        sr.metrics.fitness,
                        sr.metrics.memory,
                        sr.metrics.cost,
                        sr.metrics.energy,
                        sr.metrics.reliability,
                        sr.metrics.penalty,
                        sr.metrics.delay,
                        sr.metrics.responseTime
                ));
            }
            System.out.println(">> Final summary successfully exported to: " + filePath);
        } catch (IOException e) {
            System.err.println("Error writing summary CSV: " + e.getMessage());
        }
    }
}