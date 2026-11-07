package org.fog.test.perfeval.algorithms;

import org.fog.test.perfeval.models.*;
import org.fog.test.perfeval.utils.FuzzyScorer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GpcAllocator extends BaseAllocator {

    private static final double GRAVITY = 9.8;
    private static final double THETA = 14.0;
    private static final double MU_MIN = 1.0;
    private static final double MU_MAX = 10.0;
    private static final double P_SS = 0.5;

    public GpcAllocator(String algorithmName, int populationSize, int maxIteration, long seed, FitnessWeights weights) {
        super(algorithmName, populationSize, maxIteration, seed, weights);
    }

    @Override
    public List<IterationResult> optimize(List<PhysicalMachineSpec> pms, List<VmSpec> vms, List<TaskSpec> tasks) {
        int nVar = vms.size();
        int pmCount = pms.size();
        double varRange = pmCount - 1.0;

        List<Solution> pop = createInitialContinuousPopulation(nVar, pmCount, pms, vms, tasks);
        List<IterationResult> results = new ArrayList<>();

        sortPopulation(pop);
        Solution bestWorker = pop.get(0).copy();

        double thetaRad = Math.toRadians(THETA);
        double sinTheta = Math.sin(thetaRad);
        double cosTheta = Math.cos(thetaRad);

        for (int it = 1; it <= maxIteration; it++) {
            List<Solution> newPop = new ArrayList<>();

            for (int i = 0; i < populationSize; i++) {
                Solution currentWorker = pop.get(i);
                double v0 = random.nextDouble();
                double mu = MU_MIN + ((MU_MAX - MU_MIN) * random.nextDouble());

                double d = (v0 * v0) / ((2.0 * GRAVITY) * (sinTheta + (mu * cosTheta)));
                double x = (v0 * v0) / ((2.0 * GRAVITY) * sinTheta);

                double[] generatedPosition = new double[nVar];
                double[] substitutedPosition = new double[nVar];
                int forcedIndex = random.nextInt(nVar);

                for (int k = 0; k < nVar; k++) {
                    double epsilon = uniform(-(varRange / 2.0), (varRange / 2.0));
                    generatedPosition[k] = clamp((currentWorker.position[k] + d) + (x * epsilon), 0.0, pmCount - 1.0);
                    substitutedPosition[k] = (k == forcedIndex || random.nextDouble() <= P_SS) ? generatedPosition[k] : currentWorker.position[k];
                }

                Solution newSol = new Solution(substitutedPosition, pmCount);
                newSol.metrics = evaluate(newSol, pms, vms, tasks);

                Solution repaired = fuzzyLocalRepair(newSol, pms, vms, tasks);
                repaired.metrics = evaluate(repaired, pms, vms, tasks);

                if (isBetterRepairCandidate(repaired.metrics, newSol.metrics)) {
                    newSol = repaired;
                }
                newPop.add(newSol);
            }

            List<Solution> mergedPop = new ArrayList<>(pop);
            mergedPop.addAll(newPop);
            sortPopulation(mergedPop);

            pop = new ArrayList<>(mergedPop.subList(0, populationSize));
            if (pop.get(0).metrics.fitness < bestWorker.metrics.fitness) {
                bestWorker = pop.get(0).copy();
            }

            if (it % 5 == 0 || it == maxIteration) {
                results.add(new IterationResult(it, bestWorker.metrics, Arrays.copyOf(bestWorker.placement, bestWorker.placement.length)));
            }
        }
        return results;
    }

    private List<Solution> createInitialContinuousPopulation(int vmCount, int pmCount, List<PhysicalMachineSpec> pms, List<VmSpec> vms, List<TaskSpec> tasks) {
        List<Solution> population = new ArrayList<>();
        for (int i = 0; i < populationSize; i++) {
            Solution solution = Solution.randomContinuousSolution(vmCount, pmCount, random);
            solution.metrics = evaluate(solution, pms, vms, tasks);
            population.add(solution);
        }
        return population;
    }

    private double uniform(double min, double max) {
        return min + ((max - min) * random.nextDouble());
    }

    private Solution fuzzyLocalRepair(Solution solution, List<PhysicalMachineSpec> pms, List<VmSpec> vms, List<TaskSpec> tasks) {
        Solution bestSolution = solution.copy();
        bestSolution.metrics = evaluate(bestSolution, pms, vms, tasks);
        int repairAttempts = Math.max(4, vms.size() / 3);

        for (int attempt = 0; attempt < repairAttempts; attempt++) {
            List<Integer> criticalVms = selectCriticalVms(bestSolution, pms, vms, tasks, 4);
            Solution bestNeighbor = bestSolution.copy();

            for (Integer selectedVmIndex : criticalVms) {
                for (int pmIndex = 0; pmIndex < pms.size(); pmIndex++) {
                    if (pmIndex == bestSolution.placement[selectedVmIndex]) continue;

                    Solution neighbor = bestSolution.copy();
                    neighbor.placement[selectedVmIndex] = pmIndex;
                    neighbor.updatePositionFromPlacement();
                    neighbor.metrics = evaluate(neighbor, pms, vms, tasks);

                    if (isBetterRepairCandidate(neighbor.metrics, bestNeighbor.metrics)) {
                        bestNeighbor = neighbor;
                    }
                }
            }

            if (isBetterRepairCandidate(bestNeighbor.metrics, bestSolution.metrics)) {
                bestSolution = bestNeighbor;
            } else {
                break;
            }
        }
        return bestSolution;
    }

    private List<Integer> selectCriticalVms(Solution solution, List<PhysicalMachineSpec> pms, List<VmSpec> vms, List<TaskSpec> tasks, int count) {
        int[] vmCountOnPm = new int[pms.size()];
        double[] provisionalMips = new double[pms.size()];

        for (VmSpec vm : vms) {
            int pmIndex = solution.placement[vm.id];
            provisionalMips[pmIndex] += vm.requiredMips;
            vmCountOnPm[pmIndex]++;
        }

        double[] taskCpuPressure = calculateTaskCpuPressure(tasks, vms.size());
        int[] taskConnectionCount = calculateTaskConnectionCount(tasks, vms.size());

        double[] usedMips = new double[pms.size()];
        double[] usedRam = new double[pms.size()];

        for (VmSpec vm : vms) {
            int pmIndex = solution.placement[vm.id];
            double dynamicUsedRam = estimateDynamicUsedRam(vm, pmIndex, provisionalMips, vmCountOnPm, pms, vms.size(), taskCpuPressure, taskConnectionCount);
            usedMips[pmIndex] += vm.requiredMips;
            usedRam[pmIndex] += dynamicUsedRam;
        }

        List<Integer> selected = new ArrayList<>();
        boolean[] usedVm = new boolean[vms.size()];
        int safeCount = Math.min(count, vms.size());

        for (int k = 0; k < safeCount; k++) {
            int criticalVm = -1;
            double worstScore = -1.0;

            for (VmSpec vm : vms) {
                if (usedVm[vm.id]) continue;

                int pmIndex = solution.placement[vm.id];
                PhysicalMachineSpec pm = pms.get(pmIndex);

                double nodeLoadRatio = clamp((0.55 * (usedMips[pmIndex] / pm.totalMips())) + (0.45 * (usedRam[pmIndex] / pm.ram)), 0.0, 1.5);
                double nodeLoad = Math.min(100.0, nodeLoadRatio * 100.0);
                double freeResources = Math.max(0.0, 100.0 - nodeLoad);

                double urgency = (vm.priority + vm.delaySensitivity) / 20.0;
                double resourcePressure = 1.0 - (freeResources / 100.0);
                double connectionPressure = Math.min(1.0, taskConnectionCount[vm.id] / 6.0);
                double fuzzyNeed = FuzzyScorer.score(vm.priority, vm.delaySensitivity, nodeLoad, freeResources) / 100.0;

                double criticalScore = (0.30 * nodeLoadRatio) + (0.25 * urgency) + (0.20 * resourcePressure) + (0.15 * connectionPressure) + (0.10 * fuzzyNeed);

                if (criticalScore > worstScore) {
                    worstScore = criticalScore;
                    criticalVm = vm.id;
                }
            }

            if (criticalVm >= 0) {
                selected.add(criticalVm);
                usedVm[criticalVm] = true;
            }
        }
        return selected;
    }

    private boolean isBetterRepairCandidate(Metrics candidate, Metrics current) {
        if (candidate.fitness < current.fitness) return true;
        if (candidate.delay < current.delay && candidate.responseTime < current.responseTime
                && candidate.reliability >= current.reliability - 0.006 && candidate.fitness <= current.fitness * 1.010) return true;
        return candidate.reliability > current.reliability && candidate.delay <= current.delay * 1.010
                && candidate.responseTime <= current.responseTime * 1.010 && candidate.fitness <= current.fitness * 1.008;
    }
}