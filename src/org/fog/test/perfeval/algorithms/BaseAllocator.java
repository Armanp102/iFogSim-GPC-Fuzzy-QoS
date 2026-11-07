package org.fog.test.perfeval.algorithms;

import org.fog.test.perfeval.models.*;
import org.fog.test.perfeval.utils.FuzzyScorer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public abstract class BaseAllocator {

    public String algorithmName;
    public int populationSize;
    public int maxIteration;
    public Random random;
    public FitnessWeights weights;

    public BaseAllocator(String algorithmName, int populationSize, int maxIteration, long seed, FitnessWeights weights) {
        this.algorithmName = algorithmName;
        this.populationSize = populationSize;
        this.maxIteration = maxIteration;
        this.random = new Random(seed);
        this.weights = weights == null ? FitnessWeights.qosDefault() : weights;
    }

    public abstract List<IterationResult> optimize(List<PhysicalMachineSpec> pms, List<VmSpec> vms, List<TaskSpec> tasks);

    public List<Solution> createInitialPopulation(int vmCount, int pmCount, List<PhysicalMachineSpec> pms, List<VmSpec> vms, List<TaskSpec> tasks) {
        List<Solution> population = new ArrayList<>();
        for (int i = 0; i < populationSize; i++) {
            Solution solution = Solution.randomSolution(vmCount, pmCount, random);
            solution.metrics = evaluate(solution, pms, vms, tasks);
            population.add(solution);
        }
        return population;
    }

    public void sortPopulation(List<Solution> population) {
        population.sort((a, b) -> Double.compare(a.metrics.fitness, b.metrics.fitness));
    }

    public void mutateOneGene(Solution solution, int pmCount) {
        int vmIndex = random.nextInt(solution.placement.length);
        solution.placement[vmIndex] = random.nextInt(pmCount);
        solution.updatePositionFromPlacement();
    }

    public void mutateByRate(Solution solution, int pmCount, double mutationRate) {
        for (int i = 0; i < solution.placement.length; i++) {
            if (random.nextDouble() < mutationRate) {
                solution.placement[i] = random.nextInt(pmCount);
            }
        }
        solution.updatePositionFromPlacement();
    }

    public Solution crossover(Solution a, Solution b) {
        int[] childPlacement = new int[a.placement.length];
        for (int i = 0; i < childPlacement.length; i++) {
            childPlacement[i] = random.nextBoolean() ? a.placement[i] : b.placement[i];
        }
        return new Solution(childPlacement);
    }

    public Solution opposition(Solution solution, int pmCount) {
        int[] oppositePlacement = new int[solution.placement.length];
        for (int i = 0; i < solution.placement.length; i++) {
            oppositePlacement[i] = (pmCount - 1) - solution.placement[i];
        }
        return new Solution(oppositePlacement);
    }

    public double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public double standardDeviation(double[] values, double mean) {
        if (values == null || values.length == 0) return 0.0;
        double sum = 0;
        for (double value : values) {
            sum += Math.pow(value - mean, 2);
        }
        return Math.sqrt(sum / values.length);
    }

    public double[] calculateTaskCpuPressure(List<TaskSpec> tasks, int vmCount) {
        double[] taskCpuPressure = new double[vmCount];
        for (TaskSpec task : tasks) {
            taskCpuPressure[task.srcVm] += task.cpuLength;
            taskCpuPressure[task.dstVm] += task.cpuLength * 0.5;
        }
        return taskCpuPressure;
    }

    public int[] calculateTaskConnectionCount(List<TaskSpec> tasks, int vmCount) {
        int[] taskConnectionCount = new int[vmCount];
        for (TaskSpec task : tasks) {
            taskConnectionCount[task.srcVm]++;
            taskConnectionCount[task.dstVm]++;
        }
        return taskConnectionCount;
    }

    public double estimateDynamicUsedRam(VmSpec vm, int pmIndex, double[] provisionalMips, int[] vmCountOnPm,
                                         List<PhysicalMachineSpec> pms, int totalVmCount, double[] taskCpuPressure, int[] taskConnectionCount) {
        PhysicalMachineSpec pm = pms.get(pmIndex);
        double cpuLoadOnNode = clamp(provisionalMips[pmIndex] / pm.totalMips(), 0.0, 1.5);
        double taskPressure = clamp(taskCpuPressure[vm.id] / 10000.0, 0.0, 1.0);
        double connectionPressure = clamp(taskConnectionCount[vm.id] / 6.0, 0.0, 1.0);
        double priorityPressure = clamp(vm.priority / 10.0, 0.0, 1.0);
        double delayPressure = clamp(vm.delaySensitivity / 10.0, 0.0, 1.0);

        double averageVmPerPm = totalVmCount / (double) pms.size();
        double coLocationPressure = averageVmPerPm > 0 ? clamp(vmCountOnPm[pmIndex] / (averageVmPerPm * 2.0), 0.0, 1.0) : 0.0;

        double dynamicUsageRatio = vm.baseRamRatio + (0.18 * taskPressure) + (0.12 * connectionPressure)
                + (0.10 * priorityPressure) + (0.10 * delayPressure) + (0.10 * cpuLoadOnNode) + (0.05 * coLocationPressure);

        return vm.allocatedRam * clamp(dynamicUsageRatio, 0.30, 0.95);
    }

    public Metrics evaluate(Solution solution, List<PhysicalMachineSpec> pms, List<VmSpec> vms, List<TaskSpec> tasks) {
        double[] usedMips = new double[pms.size()];
        double[] usedRam = new double[pms.size()];
        double totalRam = 0, totalBusyPower = 0;

        for (PhysicalMachineSpec pm : pms) {
            totalRam += pm.ram;
            totalBusyPower += pm.busyPower;
        }

        int[] vmCountOnPm = new int[pms.size()];
        double[] provisionalMips = new double[pms.size()];

        for (VmSpec vm : vms) {
            int pmIndex = solution.placement[vm.id];
            provisionalMips[pmIndex] += vm.requiredMips;
            vmCountOnPm[pmIndex]++;
        }

        double[] taskCpuPressure = calculateTaskCpuPressure(tasks, vms.size());
        int[] taskConnectionCount = calculateTaskConnectionCount(tasks, vms.size());

        for (VmSpec vm : vms) {
            int pmIndex = solution.placement[vm.id];
            double dynamicUsedRam = estimateDynamicUsedRam(vm, pmIndex, provisionalMips, vmCountOnPm, pms, vms.size(), taskCpuPressure, taskConnectionCount);
            usedMips[pmIndex] += vm.requiredMips;
            usedRam[pmIndex] += dynamicUsedRam;
        }

        double totalUsedRam = 0, totalEnergy = 0, totalCost = 0, overloadPenalty = 0;
        double maxRamLoad = 0, ramLoadSum = 0, mipsLoadSum = 0;
        int activeNodes = 0;

        double[] ramLoads = new double[pms.size()];
        double[] mipsLoads = new double[pms.size()];

        for (int i = 0; i < pms.size(); i++) {
            PhysicalMachineSpec pm = pms.get(i);
            double ramLoad = usedRam[i] / pm.ram;
            double mipsLoad = usedMips[i] / pm.totalMips();
            double combinedLoad = (ramLoad + mipsLoad) / 2.0;

            ramLoads[i] = ramLoad;
            mipsLoads[i] = mipsLoad;
            totalUsedRam += usedRam[i];
            ramLoadSum += ramLoad;
            mipsLoadSum += mipsLoad;
            maxRamLoad = Math.max(maxRamLoad, ramLoad);

            if (usedRam[i] > 0 || usedMips[i] > 0) activeNodes++;
            if (mipsLoad > 1.0) overloadPenalty += Math.pow(mipsLoad - 1.0, 2);
            if (ramLoad > 1.0) overloadPenalty += Math.pow(ramLoad - 1.0, 2);

            double safeLoad = clamp(combinedLoad, 0.0, 1.0);
            if (safeLoad > 0) {
                totalEnergy += pm.idlePower + (pm.busyPower - pm.idlePower) * Math.pow(safeLoad, 2);
            }
            totalCost += (usedMips[i] * pm.ratePerMips) + (Math.pow(safeLoad, 2) * 0.012) + (ramLoad * 0.002);
        }

        double ramStd = standardDeviation(ramLoads, ramLoadSum / pms.size());
        double mipsStd = standardDeviation(mipsLoads, mipsLoadSum / pms.size());
        double memoryUtilization = totalUsedRam / totalRam;

        double activeRamLoadSum = 0;
        int activeRamNodes = 0;
        for (double ramLoad : ramLoads) {
            if (ramLoad > 0) {
                activeRamLoadSum += ramLoad;
                activeRamNodes++;
            }
        }
        double avgActiveRamLoad = activeRamNodes > 0 ? activeRamLoadSum / activeRamNodes : 0.0;
        double memory = (0.20 * memoryUtilization) + (0.35 * maxRamLoad) + (0.30 * ramStd) + (0.15 * avgActiveRamLoad);
        double energyNormalized = (0.70 * (totalEnergy / totalBusyPower)) + (0.30 * (activeNodes / (double) pms.size()));

        double vmReliabilitySum = 0;
        for (VmSpec vm : vms) {
            int pmIndex = solution.placement[vm.id];
            PhysicalMachineSpec pm = pms.get(pmIndex);
            double nodeLoadRatio = clamp((0.55 * (usedMips[pmIndex] / pm.totalMips())) + (0.45 * (usedRam[pmIndex] / pm.ram)), 0.0, 1.5);
            double nodeLoad = nodeLoadRatio * 100.0;
            double freeResources = clamp(100.0 - nodeLoad, 0.0, 100.0);

            double fuzzyScore = FuzzyScorer.score(vm.priority, vm.delaySensitivity, nodeLoad, freeResources) / 100.0;
            double dynamicNodeReliability = clamp(pm.baseReliability - (0.10 * nodeLoadRatio) - (0.18 * Math.max(0.0, nodeLoadRatio - 0.75)), 0.50, 0.99);
            double vmCriticality = (0.50 * (vm.priority / 10.0)) + (0.50 * (vm.delaySensitivity / 10.0));

            double vmReliability = clamp((0.65 * fuzzyScore) + (0.35 * dynamicNodeReliability) - (vmCriticality * nodeLoadRatio * 0.08), 0.0, 1.0);
            vmReliabilitySum += vmReliability;
        }

        double taskReliabilitySum = 0;
        for (TaskSpec task : tasks) {
            int srcNode = solution.placement[task.srcVm];
            int dstNode = solution.placement[task.dstVm];
            PhysicalMachineSpec srcPm = pms.get(srcNode);
            PhysicalMachineSpec dstPm = pms.get(dstNode);

            double srcLoad = ((usedMips[srcNode] / srcPm.totalMips()) + (usedRam[srcNode] / srcPm.ram)) / 2.0;
            double dstLoad = ((usedMips[dstNode] / dstPm.totalMips()) + (usedRam[dstNode] / dstPm.ram)) / 2.0;

            double communicationReliability = srcNode == dstNode ? 0.98 : clamp(0.92 - (task.nwLength / 20000.0), 0.70, 0.98);
            double taskReliability = clamp((0.55 * ((srcPm.baseReliability + dstPm.baseReliability) / 2.0)) + (0.35 * communicationReliability) - (((srcLoad + dstLoad) / 2.0) * 0.10), 0.0, 1.0);
            taskReliabilitySum += taskReliability;
        }

        double reliability = clamp((0.70 * (vmReliabilitySum / vms.size())) + (0.30 * (taskReliabilitySum / tasks.size())), 0.0, 1.0);

        double delaySum = 0, responseTimeSum = 0;
        for (TaskSpec task : tasks) {
            int srcNode = solution.placement[task.srcVm];
            int dstNode = solution.placement[task.dstVm];
            PhysicalMachineSpec srcPm = pms.get(srcNode);
            PhysicalMachineSpec dstPm = pms.get(dstNode);

            double srcLoad = clamp(((usedMips[srcNode] / srcPm.totalMips()) + (usedRam[srcNode] / srcPm.ram)) / 2.0, 0.0, 1.5);
            double dstLoad = clamp(((usedMips[dstNode] / dstPm.totalMips()) + (usedRam[dstNode] / dstPm.ram)) / 2.0, 0.0, 1.5);

            double processingDelay = clamp(task.cpuLength / (dstPm.totalMips() * (1.0 - clamp(dstLoad * 0.55, 0.0, 0.85))), 0.0, 1.0);
            double networkDelay = srcNode == dstNode ? clamp(task.nwLength / 250000.0, 0.0, 1.0) : clamp((task.nwLength / 60000.0) + 0.005, 0.0, 1.0);

            double queueDelay = (srcLoad > 1.0 ? (srcLoad - 1.0) * 0.03 : 0) + (dstLoad > 1.0 ? (dstLoad - 1.0) * 0.03 : 0) + (((srcLoad + dstLoad) / 2.0) * 0.035);
            queueDelay = clamp(queueDelay, 0.0, 1.0);

            double taskDelay = clamp(networkDelay + queueDelay, 0.0, 1.0);
            delaySum += taskDelay;
            responseTimeSum += clamp(processingDelay + taskDelay, 0.0, 1.0);
        }

        double delay = tasks.isEmpty() ? 0.0 : clamp(delaySum / tasks.size(), 0.0, 1.0);
        double responseTime = tasks.isEmpty() ? 0.0 : clamp(responseTimeSum / tasks.size(), 0.0, 1.0);

        double networkPenalty = 0;
        for (TaskSpec task : tasks) {
            networkPenalty += (solution.placement[task.srcVm] != solution.placement[task.dstVm]) ? task.nwLength / 9000.0 : task.nwLength / 60000.0;
        }
        double penalty = (0.45 * overloadPenalty) + (0.35 * (networkPenalty / tasks.size())) + (0.20 * ((0.55 * ramStd) + (0.45 * mipsStd)));

        double costNormalized = clamp(totalCost / weights.costReference, 0.0, 1.0);
        double penaltyNormalized = clamp(penalty / weights.penaltyReference, 0.0, 1.0);

        double fitness = (weights.memoryWeight * memory) + (weights.costWeight * costNormalized)
                + (weights.energyWeight * energyNormalized) + (weights.reliabilityWeight * (1.0 - reliability))
                + (weights.penaltyWeight * penaltyNormalized) + (weights.delayWeight * delay) + (weights.responseTimeWeight * responseTime);

        return new Metrics(memory, totalCost, energyNormalized, reliability, penalty, delay, responseTime, fitness);
    }
}