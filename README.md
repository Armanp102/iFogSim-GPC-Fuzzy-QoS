# iFogSim2 Research Project: GPC-Fuzzy QoS Resource Allocation

This repository contains an iFogSim2-based research project for evaluating fog/edge resource-allocation algorithms, together with the simulation source code, datasets/topologies, and the result files supplied with the project.

> **Research reproducibility note:** The repository contains two groups of artifacts. The `GpcFuzzyQosExperiment` program implements the GPC-Fuzzy QoS comparison and writes its own CSV outputs when executed. The pre-existing Excel/PDF files under `results/` are supplied legacy DCNS/VRGame experiment reports; do not cite them as GPC-Fuzzy QoS results unless their relationship to the paper has been independently verified.

## Research code

Main experiment entry point:

```text
org.fog.test.perfeval.GpcFuzzyQosExperiment
```

Main algorithm implementations:

- `GpcAllocator` — proposed GPC-Fuzzy QoS allocator.
- `CroOblAllocator` — CRO-OBL comparison allocator.
- `MomfoAllocator` — MOMFO comparison allocator.
- `BaseAllocator` — shared allocator functionality.

Supporting experiment models and utilities are in:

```text
src/org/fog/test/perfeval/models/
src/org/fog/test/perfeval/utils/
```

The experiment source currently defines three scenarios, four fitness-weight configurations, ten repeated runs per weight configuration, and a population size of 40 over 25 iterations. See `GpcFuzzyQosExperiment.java` for the authoritative parameters and implementation.

## Repository layout

```text
src/                         Java source code (iFogSim2 and research code)
jars/                        Required Java libraries
 dataset/                    Mobility/resource datasets and configuration
topologies/                  Simulation topology input files
results/                     Supplied Excel/ODS/PDF reports and text results
results/raw-logs/             Compressed raw simulation logs
run-experiment.sh             Linux/macOS build-and-run helper
run-experiment.bat            Windows build-and-run helper
```

## Requirements

- JDK 8 or a newer JDK capable of compiling Java 8 source/target code.
- No Maven or Gradle project is included; dependencies are provided in `jars/`.

Some source files contain legacy non-UTF-8 bytes. The build commands therefore explicitly use `ISO-8859-1` as the source encoding. Do not bulk-convert source files to UTF-8 without checking those files first.

## Build and run

### Windows

Open Command Prompt in the repository root and run:

```bat
run-experiment.bat
```

### Linux / macOS

From the repository root:

```bash
chmod +x run-experiment.sh
./run-experiment.sh
```

The experiment can also be launched from an IDE by running `org.fog.test.perfeval.GpcFuzzyQosExperiment` with the libraries in `jars/` on the classpath.

The GPC-Fuzzy experiment writes six CSV files (detailed convergence and final summary for each of the three scenarios) into `results/algorithm-comparison/`.

> **Runtime note:** The configured experiment performs many optimization runs and may take a while. Keep the parameter values unchanged when reproducing the reported experiment; if you change them, document the change in the paper/repository.

## Datasets and topologies

- `dataset/` contains the mobility/resource data files and the `config.properties` file supplied with this project.
- `topologies/` contains the topology descriptions used by the simulation examples.

Please retain the original dataset provenance/citations required by the dataset providers when publishing results based on these files.

## Existing results and charts

See [`results/README.md`](results/README.md) for a description of the supplied result workbooks and raw logs. Several Excel workbooks contain embedded charts. The archive did not contain a separate set of exported paper-figure image files.

## Upstream simulator citation

If this work uses iFogSim2, cite the simulator paper:

```bibtex
@article{mahmud2021ifogsim2,
  title={iFogSim2: An Extended iFogSim Simulator for Mobility, Clustering, and Microservice Management in Edge and Fog Computing Environments},
  author={Mahmud, Redowan and Pallewatta, Samodha and Goudarzi, Mohammad and Buyya, Rajkumar},
  journal={arXiv preprint arXiv:2109.05636},
  year={2021}
}
```

Also review the original iFogSim/CloudSim references and the upstream project documentation before publication.

## License and third-party notices

No top-level `LICENSE` file was present in the supplied archive. This repository includes upstream iFogSim2/CloudSim code and third-party JAR dependencies. **Do not select or add a license for the whole repository until the applicable upstream and dependency licenses have been checked.** Preserve required copyright and attribution notices.

## Before submitting the repository with a paper

1. Replace this README's generic project description with the exact paper title and author/affiliation details, if appropriate.
2. Add the paper DOI/preprint URL once available.
3. Confirm which supplied result files correspond to the final paper tables and figures.
4. Run the experiment from a clean checkout and archive the generated CSV files.
5. Record the JDK version, operating system, random seeds, and any parameter changes used for the final reported results.
