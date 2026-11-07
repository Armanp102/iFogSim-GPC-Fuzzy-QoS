package org.fog.test.perfeval.algorithms;

import org.fog.test.perfeval.models.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CroOblAllocator extends BaseAllocator {

    private static final double INITIAL_KE = 1.0;
    private static final double INITIAL_BUFFER = 10.0;
    private static final double MOLECULAR_COLLISION_RATE = 0.25;
    private static final double KINETIC_LOSS_RATE = 0.20;
    private static final int DECOMPOSITION_THRESHOLD = 8;
    private static final double SYNTHESIS_KE_THRESHOLD = 0.40;
    private static final double OBL_JUMPING_RATE = 0.20;

    public CroOblAllocator(String algorithmName, int populationSize, int maxIteration, long seed, FitnessWeights weights) {
        super(algorithmName, populationSize, maxIteration, seed, weights);
    }

    @Override
    public List<IterationResult> optimize(List<PhysicalMachineSpec> pms, List<VmSpec> vms, List<TaskSpec> tasks) {
        int vmCount = vms.size();
        int pmCount = pms.size();
        double[] energyBuffer = new double[]{INITIAL_BUFFER};

        List<Molecule> molecules = createInitialOblMolecules(vmCount, pmCount, pms, vms, tasks);
        normalizeMoleculePopulation(molecules, pmCount, pms, vms, tasks);

        List<IterationResult> results = new ArrayList<>();
        Solution globalBest = getBestSolution(molecules).copy();

        for (int iteration = 1; iteration <= maxIteration; iteration++) {
            int reactionCount = populationSize;

            for (int reaction = 0; reaction < reactionCount; reaction++) {
                if (molecules.size() < 2) {
                    normalizeMoleculePopulation(molecules, pmCount, pms, vms, tasks);
                }

                if (random.nextDouble() < MOLECULAR_COLLISION_RATE) {
                    int firstIndex = random.nextInt(molecules.size());
                    int secondIndex = random.nextInt(molecules.size());
                    while (secondIndex == firstIndex && molecules.size() > 1) {
                        secondIndex = random.nextInt(molecules.size());
                    }

                    Molecule first = molecules.get(firstIndex);
                    Molecule second = molecules.get(secondIndex);

                    if (first.kineticEnergy <= SYNTHESIS_KE_THRESHOLD && second.kineticEnergy <= SYNTHESIS_KE_THRESHOLD) {
                        synthesisReaction(molecules, firstIndex, secondIndex, pms, vms, tasks);
                    } else {
                        interMolecularIneffectiveCollision(molecules, firstIndex, secondIndex, pms, vms, tasks);
                    }
                } else {
                    int selectedIndex = random.nextInt(molecules.size());
                    Molecule selected = molecules.get(selectedIndex);

                    if (selected.hitCount >= DECOMPOSITION_THRESHOLD) {
                        decompositionReaction(molecules, selectedIndex, energyBuffer, pmCount, pms, vms, tasks);
                    } else {
                        onWallIneffectiveCollision(molecules, selectedIndex, energyBuffer, pmCount, pms, vms, tasks);
                    }
                }
                normalizeMoleculePopulation(molecules, pmCount, pms, vms, tasks);
            }

            if (iteration % 5 == 0 || random.nextDouble() < OBL_JUMPING_RATE) {
                oppositionBasedGenerationJumping(molecules, pmCount, pms, vms, tasks);
                normalizeMoleculePopulation(molecules, pmCount, pms, vms, tasks);
            }

            Solution currentBest = getBestSolution(molecules);
            if (currentBest.metrics.fitness < globalBest.metrics.fitness) {
                globalBest = currentBest.copy();
            }

            if (iteration % 5 == 0 || iteration == maxIteration) {
                results.add(new IterationResult(iteration, globalBest.metrics, Arrays.copyOf(globalBest.placement, globalBest.placement.length)));
            }
        }
        return results;
    }

    private List<Molecule> createInitialOblMolecules(int vmCount, int pmCount, List<PhysicalMachineSpec> pms, List<VmSpec> vms, List<TaskSpec> tasks) {
        List<Molecule> molecules = new ArrayList<>();
        for (int i = 0; i < populationSize; i++) {
            Solution original = Solution.randomSolution(vmCount, pmCount, random);
            original.metrics = evaluate(original, pms, vms, tasks);

            Solution opposite = opposition(original, pmCount);
            opposite.metrics = evaluate(opposite, pms, vms, tasks);

            Solution selected = (opposite.metrics.fitness < original.metrics.fitness) ? opposite : original;
            molecules.add(new Molecule(selected.copy(), selected.metrics.fitness, INITIAL_KE, 0));
        }
        return molecules;
    }

    private void onWallIneffectiveCollision(List<Molecule> molecules, int moleculeIndex, double[] energyBuffer, int pmCount, List<PhysicalMachineSpec> pms, List<VmSpec> vms, List<TaskSpec> tasks) {
        Molecule oldMolecule = molecules.get(moleculeIndex);
        Solution candidate = localPerturbation(oldMolecule.solution, pmCount, 0.12);
        candidate.metrics = evaluate(candidate, pms, vms, tasks);

        double oldEnergy = oldMolecule.potentialEnergy + oldMolecule.kineticEnergy;
        double newPotentialEnergy = candidate.metrics.fitness;

        if (oldEnergy >= newPotentialEnergy) {
            double releasedEnergy = oldEnergy - newPotentialEnergy;
            double kineticRate = KINETIC_LOSS_RATE + ((1.0 - KINETIC_LOSS_RATE) * random.nextDouble());
            double newKineticEnergy = releasedEnergy * kineticRate;
            energyBuffer[0] += releasedEnergy * (1.0 - kineticRate);

            int newHitCount = (newPotentialEnergy < oldMolecule.potentialEnergy) ? 0 : oldMolecule.hitCount + 1;
            molecules.set(moleculeIndex, new Molecule(candidate.copy(), newPotentialEnergy, newKineticEnergy, newHitCount));
        } else {
            oldMolecule.hitCount++;
        }
    }

    private void decompositionReaction(List<Molecule> molecules, int moleculeIndex, double[] energyBuffer, int pmCount, List<PhysicalMachineSpec> pms, List<VmSpec> vms, List<TaskSpec> tasks) {
        Molecule oldMolecule = molecules.get(moleculeIndex);
        Solution firstCandidate = vigorousPerturbation(oldMolecule.solution, pmCount, 0.35);
        Solution secondCandidate = vigorousPerturbation(opposition(oldMolecule.solution, pmCount), pmCount, 0.20);

        firstCandidate.metrics = evaluate(firstCandidate, pms, vms, tasks);
        secondCandidate.metrics = evaluate(secondCandidate, pms, vms, tasks);

        double oldEnergy = oldMolecule.potentialEnergy + oldMolecule.kineticEnergy;
        double requiredEnergy = firstCandidate.metrics.fitness + secondCandidate.metrics.fitness;

        if (oldEnergy + energyBuffer[0] >= requiredEnergy) {
            double remainingEnergy = (oldEnergy >= requiredEnergy) ? oldEnergy - requiredEnergy : 0.0;
            if (oldEnergy < requiredEnergy) {
                energyBuffer[0] -= (requiredEnergy - oldEnergy);
            }
            double split = random.nextDouble();
            molecules.remove(moleculeIndex);
            molecules.add(new Molecule(firstCandidate.copy(), firstCandidate.metrics.fitness, remainingEnergy * split, 0));
            molecules.add(new Molecule(secondCandidate.copy(), secondCandidate.metrics.fitness, remainingEnergy * (1.0 - split), 0));
        } else {
            oldMolecule.hitCount++;
            onWallIneffectiveCollision(molecules, moleculeIndex, energyBuffer, pmCount, pms, vms, tasks);
        }
    }

    private void interMolecularIneffectiveCollision(List<Molecule> molecules, int firstIndex, int secondIndex, List<PhysicalMachineSpec> pms, List<VmSpec> vms, List<TaskSpec> tasks) {
        if (firstIndex == secondIndex) return;
        Molecule firstMolecule = molecules.get(firstIndex);
        Molecule secondMolecule = molecules.get(secondIndex);

        Solution[] candidates = interMolecularOperator(firstMolecule.solution, secondMolecule.solution, pms.size());
        Solution firstCandidate = candidates[0];
        Solution secondCandidate = candidates[1];

        firstCandidate.metrics = evaluate(firstCandidate, pms, vms, tasks);
        secondCandidate.metrics = evaluate(secondCandidate, pms, vms, tasks);

        double oldEnergy = firstMolecule.potentialEnergy + firstMolecule.kineticEnergy + secondMolecule.potentialEnergy + secondMolecule.kineticEnergy;
        double newEnergy = firstCandidate.metrics.fitness + secondCandidate.metrics.fitness;

        if (oldEnergy >= newEnergy) {
            double remainingEnergy = oldEnergy - newEnergy;
            double split = random.nextDouble();
            molecules.set(firstIndex, new Molecule(firstCandidate.copy(), firstCandidate.metrics.fitness, remainingEnergy * split, firstMolecule.hitCount + 1));
            molecules.set(secondIndex, new Molecule(secondCandidate.copy(), secondCandidate.metrics.fitness, remainingEnergy * (1.0 - split), secondMolecule.hitCount + 1));
        } else {
            firstMolecule.hitCount++;
            secondMolecule.hitCount++;
        }
    }

    private void synthesisReaction(List<Molecule> molecules, int firstIndex, int secondIndex, List<PhysicalMachineSpec> pms, List<VmSpec> vms, List<TaskSpec> tasks) {
        if (firstIndex == secondIndex) return;
        Molecule firstMolecule = molecules.get(firstIndex);
        Molecule secondMolecule = molecules.get(secondIndex);

        Solution candidate = synthesisOperator(firstMolecule.solution, secondMolecule.solution, pms.size());
        candidate.metrics = evaluate(candidate, pms, vms, tasks);

        double oldEnergy = firstMolecule.potentialEnergy + firstMolecule.kineticEnergy + secondMolecule.potentialEnergy + secondMolecule.kineticEnergy;
        double newPotentialEnergy = candidate.metrics.fitness;

        if (oldEnergy >= newPotentialEnergy) {
            Molecule newMolecule = new Molecule(candidate.copy(), newPotentialEnergy, oldEnergy - newPotentialEnergy, 0);
            if (firstIndex > secondIndex) {
                int temp = firstIndex; firstIndex = secondIndex; secondIndex = temp;
            }
            molecules.remove(secondIndex);
            molecules.remove(firstIndex);
            molecules.add(newMolecule);
        } else {
            firstMolecule.hitCount++;
            secondMolecule.hitCount++;
            interMolecularIneffectiveCollision(molecules, firstIndex, secondIndex, pms, vms, tasks);
        }
    }

    private Solution localPerturbation(Solution solution, int pmCount, double mutationRate) {
        Solution candidate = solution.copy();
        int changeCount = Math.max(1, (int) Math.round(candidate.placement.length * mutationRate));
        for (int i = 0; i < changeCount; i++) {
            candidate.placement[random.nextInt(candidate.placement.length)] = random.nextInt(pmCount);
        }
        candidate.updatePositionFromPlacement();
        return candidate;
    }

    private Solution vigorousPerturbation(Solution solution, int pmCount, double mutationRate) {
        Solution candidate = solution.copy();
        for (int i = 0; i < candidate.placement.length; i++) {
            if (random.nextDouble() < mutationRate) {
                candidate.placement[i] = random.nextInt(pmCount);
            }
        }
        if (random.nextDouble() < 0.50) {
            int start = random.nextInt(candidate.placement.length);
            int end = random.nextInt(candidate.placement.length);
            if (start > end) {
                int temp = start; start = end; end = temp;
            }
            int replacementPm = random.nextInt(pmCount);
            for (int i = start; i <= end; i++) {
                candidate.placement[i] = replacementPm;
            }
        }
        candidate.updatePositionFromPlacement();
        return candidate;
    }

    private Solution[] interMolecularOperator(Solution first, Solution second, int pmCount) {
        int length = first.placement.length;
        int cutPoint = random.nextInt(length);
        int[] firstChildPlacement = new int[length];
        int[] secondChildPlacement = new int[length];

        for (int i = 0; i < length; i++) {
            if (i <= cutPoint) {
                firstChildPlacement[i] = first.placement[i];
                secondChildPlacement[i] = second.placement[i];
            } else {
                firstChildPlacement[i] = second.placement[i];
                secondChildPlacement[i] = first.placement[i];
            }
        }
        Solution firstChild = new Solution(firstChildPlacement);
        Solution secondChild = new Solution(secondChildPlacement);

        if (random.nextDouble() < 0.35) firstChild = localPerturbation(firstChild, pmCount, 0.08);
        if (random.nextDouble() < 0.35) secondChild = localPerturbation(secondChild, pmCount, 0.08);

        firstChild.updatePositionFromPlacement();
        secondChild.updatePositionFromPlacement();
        return new Solution[]{firstChild, secondChild};
    }

    private Solution synthesisOperator(Solution first, Solution second, int pmCount) {
        Solution better = first.metrics.fitness <= second.metrics.fitness ? first : second;
        Solution worse = first.metrics.fitness <= second.metrics.fitness ? second : first;
        int[] placement = new int[better.placement.length];

        for (int i = 0; i < placement.length; i++) {
            placement[i] = random.nextDouble() < 0.70 ? better.placement[i] : worse.placement[i];
            if (random.nextDouble() < 0.05) placement[i] = random.nextInt(pmCount);
        }
        Solution candidate = new Solution(placement);
        candidate.updatePositionFromPlacement();
        return candidate;
    }

    private void oppositionBasedGenerationJumping(List<Molecule> molecules, int pmCount, List<PhysicalMachineSpec> pms, List<VmSpec> vms, List<TaskSpec> tasks) {
        sortMolecules(molecules);
        int jumpingCount = Math.max(1, (int) Math.round(molecules.size() * 0.25));

        for (int i = 0; i < jumpingCount; i++) {
            int index = molecules.size() - 1 - i;
            if (index < 0) break;

            Molecule current = molecules.get(index);
            Solution opposite = opposition(current.solution, pmCount);

            if (random.nextDouble() < 0.40) {
                opposite = localPerturbation(opposite, pmCount, 0.08);
            }
            opposite.metrics = evaluate(opposite, pms, vms, tasks);

            if (opposite.metrics.fitness < current.potentialEnergy) {
                molecules.set(index, new Molecule(opposite.copy(), opposite.metrics.fitness, current.kineticEnergy, 0));
            }
        }
    }

    private void normalizeMoleculePopulation(List<Molecule> molecules, int pmCount, List<PhysicalMachineSpec> pms, List<VmSpec> vms, List<TaskSpec> tasks) {
        sortMolecules(molecules);
        while (molecules.size() > populationSize) {
            molecules.remove(molecules.size() - 1);
        }
        while (molecules.size() < populationSize) {
            Solution base = molecules.get(random.nextInt(molecules.size())).solution.copy();
            Solution candidate = vigorousPerturbation(base, pmCount, 0.25);
            candidate.metrics = evaluate(candidate, pms, vms, tasks);
            molecules.add(new Molecule(candidate.copy(), candidate.metrics.fitness, INITIAL_KE, 0));
            sortMolecules(molecules);
        }
    }

    private Solution getBestSolution(List<Molecule> molecules) {
        sortMolecules(molecules);
        return molecules.get(0).solution.copy();
    }

    private void sortMolecules(List<Molecule> molecules) {
        molecules.sort((a, b) -> Double.compare(a.potentialEnergy, b.potentialEnergy));
    }

    private static class Molecule {
        Solution solution;
        double potentialEnergy;
        double kineticEnergy;
        int hitCount;

        Molecule(Solution solution, double potentialEnergy, double kineticEnergy, int hitCount) {
            this.solution = solution;
            this.potentialEnergy = potentialEnergy;
            this.kineticEnergy = Math.max(0.0, kineticEnergy);
            this.hitCount = hitCount;
        }
    }
}