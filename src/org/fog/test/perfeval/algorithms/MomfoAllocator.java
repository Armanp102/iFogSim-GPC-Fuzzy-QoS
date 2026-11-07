package org.fog.test.perfeval.algorithms;

import org.fog.test.perfeval.models.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MomfoAllocator extends BaseAllocator {

    private static final int OBJECTIVE_COUNT = 5;
    private static final double SPIRAL_B = 1.0;

    public MomfoAllocator(String algorithmName, int populationSize, int maxIteration, long seed, FitnessWeights weights) {
        super(algorithmName, populationSize, maxIteration, seed, weights);
    }

    @Override
    public List<IterationResult> optimize(List<PhysicalMachineSpec> pms, List<VmSpec> vms, List<TaskSpec> tasks) {
        int vmCount = vms.size();
        int pmCount = pms.size();

        List<Solution> moths = createInitialContinuousPopulation(vmCount, pmCount, pms, vms, tasks);
        List<Solution> archive = new ArrayList<>();
        archive = updateParetoArchive(archive, moths, populationSize);

        List<IterationResult> results = new ArrayList<>();
        Solution bestCompromise = selectBestByFitness(archive);
        if (bestCompromise == null) {
            sortPopulation(moths);
            bestCompromise = moths.get(0).copy();
        }

        for (int iteration = 1; iteration <= maxIteration; iteration++) {
            archive = updateParetoArchive(archive, moths, populationSize);
            List<Solution> newMoths = new ArrayList<>();

            for (int i = 0; i < populationSize; i++) {
                Solution currentMoth = moths.get(i);
                Solution selectedFlame = selectFlameFromArchive(archive);
                if (selectedFlame == null) selectedFlame = bestCompromise;

                Solution moved = moveMothBySpiral(currentMoth, selectedFlame, pmCount, iteration);
                moved.metrics = evaluate(moved, pms, vms, tasks);
                newMoths.add(moved);
            }

            moths = environmentalSelection(newMoths, archive, populationSize);
            for (Solution moth : moths) {
                if (moth.metrics == null) {
                    moth.metrics = evaluate(moth, pms, vms, tasks);
                }
            }

            archive = updateParetoArchive(archive, moths, populationSize);
            Solution currentBest = selectBestByFitness(archive);

            if (currentBest != null && currentBest.metrics.fitness < bestCompromise.metrics.fitness) {
                bestCompromise = currentBest.copy();
            }

            if (iteration % 5 == 0 || iteration == maxIteration) {
                results.add(new IterationResult(iteration, bestCompromise.metrics, Arrays.copyOf(bestCompromise.placement, bestCompromise.placement.length)));
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

    private Solution moveMothBySpiral(Solution moth, Solution flame, int pmCount, int iteration) {
        if (moth.position == null) moth.updatePositionFromPlacement();
        if (flame.position == null) flame.updatePositionFromPlacement();

        double[] newPosition = new double[moth.position.length];
        double a = -1.0 - ((double) iteration / (double) maxIteration);
        double mutationProbability = 0.08 * (1.0 - ((double) iteration / (double) maxIteration)) + 0.02;

        for (int j = 0; j < moth.position.length; j++) {
            double t = ((a - 1.0) * random.nextDouble()) + 1.0;
            double distanceToFlame = Math.abs(flame.position[j] - moth.position[j]);
            double spiralPosition = (distanceToFlame * Math.exp(SPIRAL_B * t) * Math.cos(2.0 * Math.PI * t)) + flame.position[j];

            if (random.nextDouble() < mutationProbability) {
                spiralPosition = random.nextDouble() * (pmCount - 1.0);
            }
            newPosition[j] = Math.max(0.0, Math.min(pmCount - 1.0, spiralPosition));
        }
        return new Solution(newPosition, pmCount);
    }

    private List<Solution> environmentalSelection(List<Solution> moths, List<Solution> archive, int maxSize) {
        List<Solution> combined = new ArrayList<>();
        for (Solution solution : moths) combined.add(solution.copy());
        for (Solution solution : archive) combined.add(solution.copy());

        List<Solution> nonDominated = getNonDominatedSolutions(combined);
        nonDominated = removeDuplicatePlacements(nonDominated);

        if (nonDominated.size() >= maxSize) {
            return trimByCrowdingDistance(nonDominated, maxSize);
        }

        sortPopulation(combined);
        List<Solution> selected = new ArrayList<>();
        for (Solution solution : nonDominated) selected.add(solution.copy());

        for (Solution solution : combined) {
            if (selected.size() >= maxSize) break;
            if (!containsPlacement(selected, solution)) {
                selected.add(solution.copy());
            }
        }
        while (selected.size() < maxSize && !combined.isEmpty()) {
            selected.add(combined.get(random.nextInt(combined.size())).copy());
        }
        return selected;
    }

    private List<Solution> updateParetoArchive(List<Solution> archive, List<Solution> candidates, int maxSize) {
        List<Solution> combined = new ArrayList<>();
        for (Solution solution : archive) combined.add(solution.copy());
        for (Solution solution : candidates) combined.add(solution.copy());

        List<Solution> nonDominated = getNonDominatedSolutions(combined);
        nonDominated = removeDuplicatePlacements(nonDominated);

        if (nonDominated.size() > maxSize) {
            nonDominated = trimByCrowdingDistance(nonDominated, maxSize);
        }
        return nonDominated;
    }

    private List<Solution> getNonDominatedSolutions(List<Solution> population) {
        List<Solution> nonDominated = new ArrayList<>();
        for (int i = 0; i < population.size(); i++) {
            Solution candidate = population.get(i);
            boolean dominated = false;

            for (int j = 0; j < population.size(); j++) {
                if (i == j) continue;
                if (dominates(population.get(j), candidate)) {
                    dominated = true;
                    break;
                }
            }
            if (!dominated) nonDominated.add(candidate.copy());
        }
        return nonDominated;
    }

    private boolean dominates(Solution a, Solution b) {
        double[] objA = objectiveVector(a);
        double[] objB = objectiveVector(b);
        boolean betterInAtLeastOne = false;

        for (int i = 0; i < objA.length; i++) {
            if (objA[i] > objB[i]) return false;
            if (objA[i] < objB[i]) betterInAtLeastOne = true;
        }
        return betterInAtLeastOne;
    }

    private double[] objectiveVector(Solution solution) {
        return new double[]{
                solution.metrics.delay,
                solution.metrics.responseTime,
                solution.metrics.energy,
                solution.metrics.penalty,
                1.0 - solution.metrics.reliability
        };
    }

    private Solution selectBestByFitness(List<Solution> population) {
        if (population == null || population.isEmpty()) return null;
        Solution best = population.get(0).copy();
        for (Solution solution : population) {
            if (solution.metrics.fitness < best.metrics.fitness) {
                best = solution.copy();
            }
        }
        return best;
    }

    private Solution selectFlameFromArchive(List<Solution> archive) {
        if (archive == null || archive.isEmpty()) return null;
        if (archive.size() == 1) return archive.get(0).copy();

        double[] crowding = calculateCrowdingDistance(archive);
        int first = random.nextInt(archive.size());
        int second = random.nextInt(archive.size());

        if (crowding[first] > crowding[second]) return archive.get(first).copy();
        if (crowding[second] > crowding[first]) return archive.get(second).copy();

        return archive.get(first).metrics.fitness <= archive.get(second).metrics.fitness ?
                archive.get(first).copy() : archive.get(second).copy();
    }

    private List<Solution> trimByCrowdingDistance(List<Solution> population, int maxSize) {
        List<Solution> current = new ArrayList<>();
        for (Solution solution : population) current.add(solution.copy());

        while (current.size() > maxSize) {
            double[] crowding = calculateCrowdingDistance(current);
            int worstIndex = 0;
            double worstDistance = Double.POSITIVE_INFINITY;

            for (int i = 0; i < current.size(); i++) {
                if (crowding[i] < worstDistance) {
                    worstDistance = crowding[i];
                    worstIndex = i;
                }
            }
            current.remove(worstIndex);
        }
        return current;
    }

    private double[] calculateCrowdingDistance(List<Solution> front) {
        int size = front.size();
        double[] distance = new double[size];
        if (size == 0) return distance;
        if (size <= 2) {
            Arrays.fill(distance, Double.POSITIVE_INFINITY);
            return distance;
        }

        for (int objectiveIndex = 0; objectiveIndex < OBJECTIVE_COUNT; objectiveIndex++) {
            List<Integer> indexes = new ArrayList<>();
            for (int i = 0; i < size; i++) indexes.add(i);

            final int currentObjective = objectiveIndex;
            indexes.sort((a, b) -> Double.compare(objectiveVector(front.get(a))[currentObjective], objectiveVector(front.get(b))[currentObjective]));

            distance[indexes.get(0)] = Double.POSITIVE_INFINITY;
            distance[indexes.get(size - 1)] = Double.POSITIVE_INFINITY;

            double minObj = objectiveVector(front.get(indexes.get(0)))[currentObjective];
            double maxObj = objectiveVector(front.get(indexes.get(size - 1)))[currentObjective];
            double denominator = maxObj - minObj;

            if (Math.abs(denominator) < 1e-12) continue;

            for (int i = 1; i < size - 1; i++) {
                int currentIndex = indexes.get(i);
                if (Double.isInfinite(distance[currentIndex])) continue;

                double prevObj = objectiveVector(front.get(indexes.get(i - 1)))[currentObjective];
                double nextObj = objectiveVector(front.get(indexes.get(i + 1)))[currentObjective];
                distance[currentIndex] += (nextObj - prevObj) / denominator;
            }
        }
        return distance;
    }

    private List<Solution> removeDuplicatePlacements(List<Solution> population) {
        Map<String, Solution> unique = new LinkedHashMap<>();
        for (Solution solution : population) {
            String key = Arrays.toString(solution.placement);
            if (!unique.containsKey(key) || solution.metrics.fitness < unique.get(key).metrics.fitness) {
                unique.put(key, solution.copy());
            }
        }
        return new ArrayList<>(unique.values());
    }

    private boolean containsPlacement(List<Solution> population, Solution candidate) {
        String candidateKey = Arrays.toString(candidate.placement);
        for (Solution solution : population) {
            if (Arrays.toString(solution.placement).equals(candidateKey)) return true;
        }
        return false;
    }
}