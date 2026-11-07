package org.fog.test.perfeval;

import java.io.IOException;
import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;

// ایمپورت کردن پکیج‌های جدیدی که ساختیم
import org.fog.test.perfeval.models.*;
import org.fog.test.perfeval.algorithms.*;
import org.fog.test.perfeval.utils.CsvExporter;

public class GpcFuzzyQosExperiment {

    static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
    static List<PhysicalMachineSpec> physicalMachines = new ArrayList<PhysicalMachineSpec>();

    static final int REPEAT_COUNT = 10;
    static final int ALGORITHM_POPULATION = 40;
    static final int ALGORITHM_ITERATIONS = 25;

    public static void main(String[] args) {

        Log.printLine("VERSION_CHECK_GPC_QOS_WEIGHTED_PARAMS_FINAL_REFACTORED_07");
        Log.printLine("Starting Multi-Run Weighted Sensitivity Comparison (Refactored Structure)...");

        try {
            int numUser = 1;
            Calendar calendar = Calendar.getInstance();
            boolean traceFlag = false;

            CloudSim.init(numUser, calendar, traceFlag);

            FogBroker broker = new FogBroker("broker");

            List<ScenarioConfig> scenarios = new ArrayList<ScenarioConfig>();
            scenarios.add(new ScenarioConfig("scenario1_5PM_14VM", 5, 14, 4, 3));
            scenarios.add(new ScenarioConfig("scenario2_10PM_26VM", 10, 26, 5, 3));
            scenarios.add(new ScenarioConfig("scenario3_10PM_31VM", 10, 31, 5, 3));

            for (ScenarioConfig scenario : scenarios) {
                runScenario(scenario, broker.getId());
            }

            Log.printLine("All scenarios finished successfully.");

        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Error occurred in setup.");
        }
    }

    private static void runScenario(ScenarioConfig scenario, int userId) throws IOException {

        Log.printLine("");
        Log.printLine("==============================================");
        Log.printLine("Running Scenario: " + scenario.name);
        Log.printLine("PM Count: " + scenario.pmCount);
        Log.printLine("VM Count: " + scenario.vmCount);
        Log.printLine("Task Count: " + scenario.taskCount);
        Log.printLine("SubTask Per Task: " + scenario.subTaskPerTask);
        Log.printLine("Repeat Count: " + REPEAT_COUNT);
        Log.printLine("Algorithm Population: " + ALGORITHM_POPULATION);
        Log.printLine("Algorithm Iterations: " + ALGORITHM_ITERATIONS);
        Log.printLine("==============================================");

        String appId = scenario.name + "_app";

        createPhysicalMachines(userId, scenario);
        Log.printLine("Created " + fogDevices.size() + " Physical Machines.");

        org.fog.application.Application application = createApplication(
                appId, userId, scenario.vmCount, scenario.taskCount, scenario.subTaskPerTask
        );
        Log.printLine("Created " + application.getModules().size() + " Virtual Machines.");

        List<VmSpec> vms = createVmSpecs(scenario.vmCount);
        List<TaskSpec> tasks = createTaskSpecs(scenario.taskCount, scenario.subTaskPerTask, scenario.vmCount);

        List<FitnessWeights> fitnessWeightScenarios = createFitnessWeightScenarios();
        List<RunAlgorithmResult> detailedResults = new ArrayList<RunAlgorithmResult>();

        for (int weightIndex = 0; weightIndex < fitnessWeightScenarios.size(); weightIndex++) {

            FitnessWeights selectedWeights = fitnessWeightScenarios.get(weightIndex);

            Log.printLine("");
            Log.printLine("----------------------------------------------");
            Log.printLine("Weight Scenario: " + selectedWeights.name);
            Log.printLine(selectedWeights.description());
            Log.printLine("----------------------------------------------");

            for (int runIndex = 1; runIndex <= REPEAT_COUNT; runIndex++) {

                long baseSeed = 1000L + (runIndex * 100L) + (weightIndex * 10000L);

                Log.printLine("");
                Log.printLine("Weight Scenario: " + selectedWeights.name + " | Run " + runIndex + " / " + REPEAT_COUNT);

                List<BaseAllocator> allocators = new ArrayList<BaseAllocator>();

                allocators.add(new GpcAllocator(
                        "Proposed-GPC-Fuzzy-QoS", ALGORITHM_POPULATION, ALGORITHM_ITERATIONS, baseSeed + 1, selectedWeights
                ));

                allocators.add(new CroOblAllocator(
                        "CRO-OBL-Standard", ALGORITHM_POPULATION, ALGORITHM_ITERATIONS, baseSeed + 2, selectedWeights
                ));

                allocators.add(new MomfoAllocator(
                        "MOMFO-Pareto", ALGORITHM_POPULATION, ALGORITHM_ITERATIONS, baseSeed + 3, selectedWeights
                ));

                for (BaseAllocator allocator : allocators) {

                    List<IterationResult> results = allocator.optimize(physicalMachines, vms, tasks);

                    detailedResults.add(new RunAlgorithmResult(
                            selectedWeights.name, runIndex, allocator.algorithmName, results
                    ));

                    if (results != null && !results.isEmpty()) {
                        IterationResult last = results.get(results.size() - 1);

                        Log.printLine(String.format(
                                Locale.US,
                                "WeightScenario: %s | Run %d | %s | Memory: %.6f | Cost: %.6f | Energy: %.6f | Reliability: %.6f | Penalty: %.6f | Delay: %.6f | ResponseTime: %.6f | Fitness: %.6f",
                                selectedWeights.name, runIndex, allocator.algorithmName,
                                last.metrics.memory, last.metrics.cost, last.metrics.energy,
                                last.metrics.reliability, last.metrics.penalty, last.metrics.delay,
                                last.metrics.responseTime, last.metrics.fitness
                        ));
                    }
                }
            }
        }

        List<SummaryResult> summaryResults = calculateSummaryResults(detailedResults);

        File resultsDirectory = new File("results/algorithm-comparison");
        if (!resultsDirectory.exists() && !resultsDirectory.mkdirs()) {
            throw new IOException("Could not create results directory: " + resultsDirectory.getAbsolutePath());
        }

        String detailedCsvFileName = new File(resultsDirectory, scenario.name + "_weighted_sensitivity_detailed_results.csv").getPath();
        String summaryCsvFileName = new File(resultsDirectory, scenario.name + "_weighted_sensitivity_summary_results.csv").getPath();

        // استفاده از کلاس صادرکننده جدید ابزار کمکی ما برای فایل‌های اکسل
        CsvExporter.exportConvergenceDetails(detailedCsvFileName, detailedResults);
        CsvExporter.exportFinalSummary(summaryCsvFileName, summaryResults);

        printMultiRunSummary(scenario.name, summaryResults);
        Log.printLine("Scenario finished: " + scenario.name);
    }

    private static List<FitnessWeights> createFitnessWeightScenarios() {
        List<FitnessWeights> scenarios = new ArrayList<FitnessWeights>();
        scenarios.add(FitnessWeights.qosDefault());
        scenarios.add(FitnessWeights.latencyPriority());
        scenarios.add(FitnessWeights.energyCostPriority());
        scenarios.add(FitnessWeights.balanced());
        return scenarios;
    }

    private static void createPhysicalMachines(int userId, ScenarioConfig scenario) {
        fogDevices.clear();
        physicalMachines.clear();

        for (int i = 0; i < scenario.pmCount; i++) {
            String nodeName = scenario.name + "_FogNode_" + i;
            FogDevice fogNode = createFogDevice(nodeName, 3100, 8192, 10000, 10000, 1, 0.0, 107.339, 83.4333);
            fogNode.setParentId(-1);
            fogDevices.add(fogNode);

            double baseReliability = 0.90 + ((i % 4) * 0.02);
            baseReliability = Math.max(0.88, Math.min(0.97, baseReliability));

            physicalMachines.add(new PhysicalMachineSpec(
                    nodeName, 3100, 4, 8192, 10000, 107.339, 83.4333, 0.0000015, baseReliability
            ));
        }
    }

    private static FogDevice createFogDevice(String nodeName, long mips, int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower) {
        List<Pe> peList = new ArrayList<Pe>();
        peList.add(new Pe(0, new PeProvisionerOverbooking(mips)));
        peList.add(new Pe(1, new PeProvisionerOverbooking(mips)));
        peList.add(new Pe(2, new PeProvisionerOverbooking(mips)));
        peList.add(new Pe(3, new PeProvisionerOverbooking(mips)));

        int hostId = FogUtils.generateEntityId();
        long storage = 500000;
        int bw = 10000;

        PowerHost host = new PowerHost(
                hostId, new RamProvisionerSimple(ram), new BwProvisionerOverbooking(bw), storage, peList,
                new StreamOperatorScheduler(peList), new FogLinearPowerModel(busyPower, idlePower)
        );

        List<Host> hostList = new ArrayList<Host>();
        hostList.add(host);

        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                "x86", "Windows 10", "Xen", host, 10.0, 3.0, 0.05, 0.001, 0.0
        );

        FogDevice fogDevice = null;
        try {
            fogDevice = new FogDevice(
                    nodeName, characteristics, new AppModuleAllocationPolicy(hostList), new LinkedList<>(),
                    10, upBw, downBw, 0, ratePerMips
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
        fogDevice.setLevel(level);
        return fogDevice;
    }

    private static org.fog.application.Application createApplication(String appId, int userId, int vmCount, int taskCount, int subTaskPerTask) {
        org.fog.application.Application application = org.fog.application.Application.createApplication(appId, userId);
        int allocatedRamPerVm = 512;

        for (int i = 0; i < vmCount; i++) {
            application.addAppModule("VM_" + i, allocatedRamPerVm);
        }

        for (int task = 0; task < taskCount; task++) {
            for (int sub = 0; sub < subTaskPerTask; sub++) {
                int srcIndex = (task * subTaskPerTask + sub) % vmCount;
                int dstIndex = (srcIndex + 1) % vmCount;

                String src = "VM_" + srcIndex;
                String dst = "VM_" + dstIndex;
                String tupleType = "Task_" + (task + 1) + "_SubTask_" + (sub + 1);
                String outputType = "Result_" + tupleType;

                double cpuLength = 1000 + (task * 500) + (sub * 250);
                double nwLength = 500 + (task * 120) + (sub * 80);

                application.addAppEdge(src, dst, cpuLength, nwLength, tupleType, org.fog.entities.Tuple.UP, org.fog.application.AppEdge.MODULE);
                application.addTupleMapping(src, tupleType, outputType, new org.fog.application.selectivity.FractionalSelectivity(1.0));
            }
        }
        return application;
    }

    private static List<VmSpec> createVmSpecs(int vmCount) {
        List<VmSpec> vms = new ArrayList<VmSpec>();
        for (int i = 0; i < vmCount; i++) {
            double requiredMips = 700 + (i % 4) * 250;
            double priority = 2 + (i % 3) * 4;
            double delaySensitivity = 3 + (i % 4) * 2;
            double baseRamRatio = 0.35 + ((i % 4) * 0.05);

            vms.add(new VmSpec(i, "VM_" + i, requiredMips, 512, baseRamRatio, priority, delaySensitivity));
        }
        return vms;
    }

    private static List<TaskSpec> createTaskSpecs(int taskCount, int subTaskPerTask, int vmCount) {
        List<TaskSpec> tasks = new ArrayList<TaskSpec>();
        for (int task = 0; task < taskCount; task++) {
            for (int sub = 0; sub < subTaskPerTask; sub++) {
                int srcVm = (task * subTaskPerTask + sub) % vmCount;
                int dstVm = (srcVm + 1) % vmCount;

                tasks.add(new TaskSpec(
                        task + 1, sub + 1, srcVm, dstVm,
                        1000 + (task * 500) + (sub * 250), 500 + (task * 120) + (sub * 80)
                ));
            }
        }
        return tasks;
    }

    private static List<SummaryResult> calculateSummaryResults(List<RunAlgorithmResult> detailedResults) {
        Map<String, List<Metrics>> groupedFinalMetrics = new LinkedHashMap<String, List<Metrics>>();

        for (RunAlgorithmResult runResult : detailedResults) {
            if (runResult.results == null || runResult.results.isEmpty()) continue;

            IterationResult last = runResult.results.get(runResult.results.size() - 1);
            String groupKey = runResult.weightScenarioName + "||" + runResult.algorithmName;

            if (!groupedFinalMetrics.containsKey(groupKey)) {
                groupedFinalMetrics.put(groupKey, new ArrayList<Metrics>());
            }
            groupedFinalMetrics.get(groupKey).add(last.metrics);
        }

        List<SummaryResult> summaryResults = new ArrayList<SummaryResult>();
        for (Map.Entry<String, List<Metrics>> entry : groupedFinalMetrics.entrySet()) {
            String groupKey = entry.getKey();
            String[] keyParts = groupKey.split("\\|\\|", 2);

            String weightScenarioName = keyParts[0];
            String algorithmName = keyParts.length > 1 ? keyParts[1] : groupKey;

            List<Metrics> metricsList = entry.getValue();
            Metrics mean = meanMetrics(metricsList);

            summaryResults.add(new SummaryResult(weightScenarioName, algorithmName, metricsList.size(), mean));
        }
        return summaryResults;
    }

    private static Metrics meanMetrics(List<Metrics> metricsList) {
        double memory = 0, cost = 0, energy = 0, reliability = 0, penalty = 0, delay = 0, responseTime = 0, fitness = 0;

        for (Metrics metrics : metricsList) {
            memory += metrics.memory; cost += metrics.cost; energy += metrics.energy;
            reliability += metrics.reliability; penalty += metrics.penalty; delay += metrics.delay;
            responseTime += metrics.responseTime; fitness += metrics.fitness;
        }

        int count = metricsList.size();
        if (count == 0) return new Metrics(0, 0, 0, 0, 0, 0, 0, 0);

        return new Metrics(
                memory / count, cost / count, energy / count, reliability / count,
                penalty / count, delay / count, responseTime / count, fitness / count
        );
    }

    private static void printMultiRunSummary(String scenarioName, List<SummaryResult> summaryResults) {
        Log.printLine("");
        Log.printLine("Final Multi-Run Summary For: " + scenarioName);
        Log.printLine("WeightScenario | Algorithm | Runs | Memory | Cost | Energy | Reliability | Penalty | Delay | ResponseTime | Fitness");

        for (SummaryResult summary : summaryResults) {
            Log.printLine(String.format(
                    Locale.US, "%s | %s | %d | %.6f | %.6f | %.6f | %.6f | %.6f | %.6f | %.6f | %.6f",
                    summary.weightScenarioName, summary.algorithmName, summary.runs,
                    summary.metrics.memory, summary.metrics.cost, summary.metrics.energy,
                    summary.metrics.reliability, summary.metrics.penalty, summary.metrics.delay,
                    summary.metrics.responseTime, summary.metrics.fitness
            ));
        }
        Log.printLine("");
    }
}