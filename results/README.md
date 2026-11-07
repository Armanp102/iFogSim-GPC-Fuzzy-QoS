# Supplied simulation results

This folder contains result artifacts that were present in the uploaded project archive.

## Workbooks and reports

- `DCNS_Results.xlsx`, `DCNS_Results_EXTENDED.xlsx`, `DCNS_POWER_CORRECTED.xlsx`, `DCNS_POWER_CORRECTED.ods`
- `VRGame_Results.xlsx`, `VR_GAME_RESULTS_EXTENDED.xlsx`, `VR_GAME_POWER_CORRECTED.xlsx`, `VR_GAME_POWER_CORRECTED.ods`, `VR_GAME_POWER_CORRECTED.pdf`
- `results-dcns`, `results-vrgame` — text output files from the supplied DCNS and VRGame experiments.

Several Excel workbooks contain embedded charts. The uploaded archive did not include separate exported chart images for the paper.

## GPC-Fuzzy QoS experiment output

Running `org.fog.test.perfeval.GpcFuzzyQosExperiment` writes the detailed convergence and final summary CSV files to `results/algorithm-comparison/`. That directory is created automatically by the experiment. These CSVs were not present in the uploaded archive at the time this repository package was prepared.

## Raw logs

`raw-logs/` contains compressed versions of the unique raw output logs. In the original archive, `output/dcnsCloud` and `output/vrGameCloud` were byte-for-byte identical, so only one copy was retained to avoid duplicating a 50 MB log. The two fog logs were retained separately. The original names are reflected in the compressed filenames.

## Important

The pre-existing DCNS/VRGame result workbooks and logs are not automatically evidence for the GPC-Fuzzy QoS experiment. Verify their correspondence to the paper before citing them.
