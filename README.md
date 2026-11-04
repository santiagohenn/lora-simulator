# LoRa Direct-to-Satellite Simulator

Simulation and analytical-model toolkit for **LoRa uplinks from ground end devices (EDs) directly to a
constellation of Low-Earth-Orbit (LEO) satellites** acting as gateways.

The software answers the question: *given a satellite constellation, a region of interest (ROI) and a
population of duty-cycle-limited LoRa devices, what uplink throughput does the network achieve, and
how much can a coverage-aware back-off improve it over plain ALOHA?* It contains

* an **event-based Monte Carlo simulator** (Java) that propagates the satellites with Orekit, computes
  device-to-satellite visibility, generates traffic under three channel-access policies and detects
  collisions at every satellite; and
* an **analytical throughput model** (Python) driven by the ROI coverage time series of the
  constellation, together with the scripts that compare model and simulation and produce the figures.

A second, independent set of programs explores **constellation design** for ROI coverage
(RAAN/phasing sweeps, differential evolution and simulated annealing over altitude, inclination,
phasing and inter-satellite-link power).

> **Associated publication:** S. M. Henn, J. A. Fraire, N. Accettura, S. Céspedes, H. Hermanns,
> *Multi-Gateway LoRaWAN Throughput Modeling in Direct-to-Satellite IoT Constellations*, 2024,
> [hal-04749023](https://hal.science/hal-04749023v1). The throughput experiment (simulator, analytical model
> and `scripts/plot_avg_sim_vs_model.py`) is the part of this repository associated with that paper; the
> constellation-design programs are a separate line of work.

---

## Contents

- [Overview](#overview)
- [Repository structure](#repository-structure)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick start](#quick-start)
- [Reproducing the experiments](#reproducing-the-experiments)
- [Configuration](#configuration)
- [Input data](#input-data)
- [Outputs](#outputs)
- [Code organization](#code-organization)
- [Reproducibility notes and model assumptions](#reproducibility-notes-and-model-assumptions)
- [Citation](#citation)
- [License](#license)
- [Authors and contact](#authors-and-contact)

---

## Overview

**Throughput experiment (main workflow).**

```
satellites.csv ─┐
ROI (-roi)      ├─► constellation-toolkit ─► coverage_<hash>.csv  (ROI coverage [%] vs time)
coverage config ┘                                   │
                                                    ├──► coverage-aware back-off schedule
                                                    ▼
          LoraConstellationSimulator (Java): for N = start..end EDs, for each Monte Carlo run
            1. deploy N EDs uniformly at random in the ROI (spherical cap)
            2. ED–satellite access intervals (Orekit, minimum elevation -vt)
            3. traffic generation: PA | CA | CA-NR policy, propagation delay included
            4. collision detection per satellite, sampled statistics every -ss seconds
                                                    ▼
                         <N>ED_<ss>ms_<ToA>ms_<a>km.json  (per-run time series)
                                                    ▼
      scripts/plot_avg_sim_vs_model.py: analytical model (scripts/lora_model.py) on the same
      coverage series vs. simulation average ± 1σ  ─►  coverage_awareness_results.pdf/png
```

Channel-access policies:

| Policy | CLI flag | Behaviour after each frame |
|---|---|---|
| **PA** — pure ALOHA | `-simulate-pure-aloha` | silent for the duty-cycle time, then Poisson (re)transmissions |
| **CA** — coverage aware | `-simulate-with-ca` | back-off computed from the number of devices under the current satellite footprint, never shorter than the duty-cycle time |
| **CA-NR** — coverage aware, no restriction | `-simulate-with-ca-nr` | same back-off without the duty-cycle lower bound |

**Constellation-design programs** (`AccessVsRAANAndPhase`, `AreaOptimization`, `deoptimization.DEMain`,
`saoptimization.SAOptimization`) only use constellation-toolkit coverage and a Friis/Shannon ISL link
budget; they do not run the LoRa traffic simulator.

## Repository structure

```
lora-simulator/
├── pom.xml                         Maven build of the Java simulator
├── requirements.txt                Python dependencies (analysis and plots)
├── requirements-maps.txt           Optional Python dependencies for map figures
├── CITATION.cff                    citation metadata of the associated publication
├── config/
│   ├── throughput_experiment.properties     constellation-toolkit config for the throughput experiment
│   └── optimization_argentina_eez.properties  config for the constellation-design programs
├── examples/
│   ├── minimal_example.py          self-contained analytical-model example (no inputs needed)
│   └── satellites_example.csv      example 3-satellite constellation (input format reference)
├── inputs/                         local inputs, NOT versioned (see inputs/README.md)
├── results/                        default output root, NOT versioned (created on first run)
├── src/main/java/
│   ├── Main.java                   runs the throughput experiment for the three policies
│   ├── LoraConstellationSimulator.java  command-line entry point of the simulator
│   ├── NetworkSimulator.java       Monte Carlo loop and result files
│   ├── network/                    access intervals (Network) and collision/statistics (NetworkAnalyzer)
│   ├── generators/                 device positions, traffic generation, JSON writers
│   ├── assets/, structures/        Transmission/Transmitter data types, TxStatus
│   ├── models/LogNormalCapture.java  stand-alone capture-probability demo (not used by the simulator)
│   ├── AccessVsRAANAndPhase.java   RAAN × anomaly-separation coverage sweep
│   ├── AreaOptimization.java       single coverage run of a re-phased baseline constellation
│   ├── deoptimization/             differential-evolution constellation optimiser (DEMain)
│   └── saoptimization/             simulated-annealing constellation optimiser (SAOptimization)
├── src/test/java/                  JUnit 4 tests (Transmission equality, exploratory tests)
├── scripts/
│   ├── lora_model.py               analytical throughput model (PA, CA, CA-NR)
│   ├── plot_avg_sim_vs_model.py    main figure: throughput vs number of EDs, model vs simulation
│   ├── plot_tput_time.py           throughput vs time for one N
│   ├── plot_coverage.py            coverage time series of a constellation
│   ├── plot_heatmap_raan_and_phase.py  heat map from AccessVsRAANAndPhase
│   ├── plot_sa_history.py, plot_sa_profit.py  simulated-annealing convergence plots
│   ├── plot_snapshot.py, plot_snapshot_polar.py  access-area maps (cartopy)
│   ├── plot_coverage_grid.py       ILLUSTRATIVE only: adds synthetic noise (see file header)
│   ├── repo_paths.py               repository-relative default paths
│   ├── structures/                 Constellation (CSV reader, coverage-file hash), ROI helpers
│   ├── misc/                       statistics of the Celestrak active-satellite catalogue
│   ├── resources/                  Natural Earth countries shapefile, Celestrak snapshot, icon
│   └── legacy/                     superseded scripts kept for traceability (not maintained)
└── archive/                        historical optimiser log (see archive/README.md)
```

## Requirements

**Java simulator**

* JDK **17** or newer (the sources use Java 16+ features; JDK 11 does not compile them). Maven 3.8+.
* Two libraries by the same author that are **not on Maven Central** and must be installed locally
  (see [Installation](#installation)):
  [`satellite-tools`](https://github.com/santiagohenn/satellite-tools) 2.1.1 (orbit propagation and
  access intervals, wraps Orekit) and
  [`constellation-toolkit`](https://github.com/santiagohenn/constellation-toolkit) 1.2.8 (ROI coverage
  of multi-satellite constellations).
* Orekit 13.1 (downloaded by Maven) and an **Orekit data** folder (Earth orientation parameters, leap
  seconds, …), about 100 MB.
* All other Java dependencies are resolved by Maven from `pom.xml`.

**Python scripts**

* Python 3.10+ (tested with 3.11). Core packages in `requirements.txt`: numpy, pandas, matplotlib,
  scipy, seaborn.
* Map figures additionally need shapely, geopandas and cartopy (`requirements-maps.txt`); cartopy
  downloads Natural Earth data on first use.

The code is OS-independent; it was developed on Windows. All paths are relative to the repository
root, and **Java programs must be launched from the repository root** (this is what `mvn exec:java` does).

## Installation

```bash
git clone https://github.com/santiagohenn/lora-simulator.git
cd lora-simulator

# 1. Python environment (analysis, plots, minimal example)
python -m venv .venv
source .venv/bin/activate            # Windows: .venv\Scripts\activate
pip install -r requirements.txt
# optional, for map figures: pip install -r requirements-maps.txt

# 2. Java dependencies that are not on Maven Central (build and install into ~/.m2)
git clone https://github.com/santiagohenn/satellite-tools.git ../satellite-tools
(cd ../satellite-tools && mvn -q install -DskipTests)
git clone https://github.com/santiagohenn/constellation-toolkit.git ../constellation-toolkit
(cd ../constellation-toolkit && mvn -q install -DskipTests)
#    pom.xml expects satellite-tools 2.1.1 and constellation-toolkit 1.2.8; if the installed versions
#    differ, check `mvn dependency:tree` or edit the versions in pom.xml.

# 3. Orekit data, expected in ./orekit-data (path set in config/*.properties)
git clone https://github.com/santiagohenn/orekit-data.git orekit-data

# 4. Build the simulator
mvn -q compile
```

## Quick start

**Analytical model only (no Java, no input files, a few seconds):**

```bash
python examples/minimal_example.py
```

This evaluates the three policies for 50–80 EDs over a synthetic single-satellite pass and prints

```
Coverage: synthetic single-pass coverage | ROI area: 2.0080e+12 m^2
  EDs         PA         CA      CA-NR
   50     0.1201     0.1201     0.1918
   60     0.1318     0.1318     0.1918
   70     0.1406     0.1406     0.1918
   80     0.1471     0.1471     0.1918
(PA and CA coincide whenever the coverage-aware back-off is shorter than the duty-cycle silent time.)
Wrote .../examples/output/minimal_example_throughput.csv
Wrote .../examples/output/minimal_example_throughput.png
```

and writes `examples/output/minimal_example_throughput.{csv,png}`. Throughput is normalised
(successful frame time per unit time, summed over satellite footprints). PA and CA coincide when the
coverage-aware back-off is shorter than the duty-cycle silent time, as happens at these densities.

**One small simulation (Java):**

```bash
mvn -q compile exec:java -Dexec.mainClass=LoraConstellationSimulator -Dexec.args="\
  -simulate-pure-aloha --save-analysis-datasets -o results/quickstart/ \
  -s examples/satellites_example.csv -cov-cfg config/throughput_experiment.properties \
  -toa 0.5 -dc 1.0 -nodes 10,20,10 -runs 2 -roi -21.0,-58.0,7.1946,150 -vt 20"
python examples/minimal_example.py --coverage results/quickstart/coverage_<hash>.csv \
  --roi-area-m2 <roi_surface from results/quickstart/simulation-parameters.json>
```

Expected output in `results/quickstart/`: `simulation-parameters.json`, `coverage_<hash>.csv`,
`10ED_15000ms_500ms_6921km.json`, `20ED_15000ms_500ms_6921km.json`, `avg_tx_10N.json`, `avg_tx_20N.json`.

> The Java commands in this README could not be executed during the pre-publication review because the
> review environment had no access to Maven Central or GitHub (see `REPOSITORY_REVIEW.md`). The command-line
> parsing and the Python side of the workflow were verified.

## Reproducing the experiments

### 1. Throughput vs. number of end devices (model vs. simulation, three policies)

| | |
|---|---|
| Entry point | `Main` (runs `LoraConstellationSimulator` for PA, CA and CA-NR) |
| Configuration | parameters in `src/main/java/Main.java`; coverage settings in `config/throughput_experiment.properties` |
| Required input | `inputs/satellites.csv` (constellation), Orekit data |
| Command | `mvn -q compile exec:java -Dexec.mainClass=Main` (or `-Dexec.args="pure-aloha"` for one policy) |
| Output | `results/{pure-aloha,coverage-aware,coverage-aware-nr}/` |
| Figure | `python scripts/plot_avg_sim_vs_model.py --results-dir results` → `results/coverage_awareness_results.{pdf,png}` |

Parameters used by `Main`: ToA = 0.5 s, duty cycle = 1 %, 50–80 EDs in steps of 10, 20 runs, ROI =
spherical cap centred at 21° S, 58° W with 7.1946° radius (150-segment polygon), minimum elevation 20°,
propagator step 15 s, statistics every 15 s, 2025-01-01 16:00–16:30 UTC, IAT = 0.1 s.
The figure shows, for each policy, the model (solid) and the simulation mean ± 1σ over runs (dashed,
shaded), each averaged over the time steps with coverage; the script also prints the model-vs-simulation
relative error per policy. `python scripts/plot_tput_time.py --case-dir results/pure-aloha --nodes 80`
shows the same comparison as a time series for one N.

### 2. Coverage and constellation design

These programs read `config/optimization_argentina_eez.properties` (1-hour scenario on 2025-12-04,
60 s step, 45° minimum elevation, up to 4 simultaneous satellites) and need
`inputs/satellites/constellation_baseline.csv` and `inputs/rois/argentina_eez.csv`. Each accepts
optional arguments `[config] [roi] [outputDir]` (see the class Javadoc).

| Study | Command | Output → figure |
|---|---|---|
| Coverage of the baseline, re-phased by 2° | `mvn -q compile exec:java -Dexec.mainClass=AreaOptimization` | `results/optimization/coverage_<hash>.csv` → `scripts/plot_coverage.py`, `scripts/plot_snapshot.py` (set `HASH`/`FILE_HASH` in the script) |
| RAAN (−10…10°) × anomaly separation (1…15°) sweep | `... -Dexec.mainClass=AccessVsRAANAndPhase` | `results/access_vs_raan_and_phase/coverage_heatmap_*.json` → `scripts/plot_heatmap_raan_and_phase.py` |
| Differential evolution (h, i, separation, P_tx) | `... -Dexec.mainClass=deoptimization.DEMain` | `results/differential_evolution/de_history.csv` |
| Simulated annealing (same variables) | `... -Dexec.mainClass=saoptimization.SAOptimization` | `results/sa_results/sa_history.csv` → `scripts/plot_sa_history.py`, `scripts/plot_sa_profit.py` |

The optimisers evaluate hundreds to thousands of coverage runs and can take hours. DE is seeded
(`seed(38)`), SA is seeded (`withSeed(42)`).

### What cannot be reproduced exactly

* The **input files used for the publication** (`inputs/…`) are not in the repository; results depend on them.
* The traffic simulator uses unseeded `SecureRandom`: individual runs are not bit-for-bit repeatable;
  only statistics over runs are.
* See `REPOSITORY_REVIEW.md` for open points that may affect numerical agreement with the paper.

## Configuration

**Simulator command line** (`LoraConstellationSimulator`; run without arguments for the full list):

| Option | Meaning | Default |
|---|---|---|
| `-o <dir>` | output folder (required, created if missing) | — |
| `-simulate-pure-aloha` / `-simulate-with-ca` / `-simulate-with-ca-nr` | policy (one required) | — |
| `-s <file>` | satellites CSV | `<o>/satellites.csv` |
| `-cov-cfg <file>` | constellation-toolkit properties file | `<o>/config.d3co.properties` |
| `-nodes start,end[,step]` | number of EDs, inclusive end | `10,130,10` |
| `-runs <n>` | Monte Carlo runs per N | 10 |
| `-toa <s>` / `-time-on-air` | frame time on air [s] | 0.1 |
| `-iat <s>` | mean inter-arrival time per device [s] | 0.1 |
| `-dc <%>` / `-duty-cycle-percentage` | duty cycle [%] (see assumptions below) | 1.0 |
| `-roi lat,lon,radius,segments` | spherical-cap ROI [deg] | `0,0,1,50` |
| `-vt <deg>` | minimum elevation for ED–satellite links | 20 |
| `-ts <s>` / `-time-step` | propagator step for access intervals [s] | 15 |
| `-ss <s>` / `-sample-step` | statistics sampling step [s] | 15 |
| `-start`, `-end` | scenario interval, ISO 8601 UTC | 2025-01-01T16:00:00.000 – 16:30:00.000 |
| `--save-analysis-datasets`, `--save-intervals`, `--save-destinations`, `--save-transmissions` | extra outputs | off |

**Coverage configuration** (`config/*.properties`, read by constellation-toolkit), documented inline.
The keys that matter most:

```properties
start_date=2025-01-01T16:00:00.000   # must equal -start
end_date=2025-01-01T16:30:00.000     # must equal -end
time_step_seconds=15                 # coverage time step [s]
visibility_threshold=20              # minimum elevation [deg]; should equal -vt
max_subset_size=3                    # number of coverage columns in coverage_<hash>.csv
orekit_data_path=orekit-data/
```

**Constellation-design programs**: bounds, weights and budgets are constants at the top of
`deoptimization/DEMain.java` and `saoptimization/SAOptimization.java`; the ISL link-budget constants
are in `deoptimization/ConstellationSimEvaluator.java` and `SAOptimization.java`.

## Input data

| Data | Source | Format | In repository |
|---|---|---|---|
| Constellation | user-defined | CSV, no header: `epoch,a[m],e,i[deg],RAAN[deg],ω[deg],ν[deg]` | example only (`examples/satellites_example.csv`) |
| ROI polygons | user-defined (e.g. Argentine EEZ) | CSV `lat,lon` [deg], clockwise | no (`inputs/rois/`) |
| Orekit data | [orekit-data](https://github.com/santiagohenn/orekit-data) | Orekit folder layout | no (clone to `orekit-data/`) |
| Countries | Natural Earth 1:110m admin-0 | shapefile | `scripts/resources/countries/` |
| Active satellites | Celestrak GP catalogue, retrieved 2024-08-16 | CSV (OMM fields) | `scripts/resources/active_satellites.csv` (used only by `scripts/misc/`) |

For the throughput experiment the ROI is a spherical cap given on the command line; no ROI file is needed.
Everything under `results/` is generated.

## Outputs

Per simulator output folder (`-o`):

| File | Content |
|---|---|
| `simulation-parameters.json` | all parameters of the run, ROI surface [m²] and satellite elements (read by the plot scripts) |
| `coverage_<hash>.csv` | constellation-toolkit ROI coverage: column 0 time since start [ms], columns 1..`max_subset_size` coverage [%]. `<hash>` is a SHA-256 of the orbital elements (reproduced in `scripts/structures/constellation.py`) |
| `surface_metrics_<hash>.json`, `snapshot_ne_polygons_<hash>.json`, … | further constellation-toolkit outputs (depending on the configuration) |
| `<N>ED_<ss>ms_<ToA>ms_<a>km.json` | list over runs of per-sample rows `[t since start (ms), receptions, distinct uplinks, successful receptions, successful distinct uplinks, partial successful, partial collided, not heard]`; `<a>` is the semi-major axis of the first satellite in km |
| `avg_tx_<N>N.json` | mean number of transmissions per device, averaged over runs |
| `TX_<N>N.json`, `intervals_<N>N.json`, `destinations_<N>N.json` | optional (`--save-*`), last run only |

The plotted throughput is `successful receptions × ToA / sample step`.

## Code organization

| Role | Files |
|---|---|
| Experiment drivers | `Main`, `LoraConstellationSimulator`, `AccessVsRAANAndPhase`, `AreaOptimization`, `deoptimization/DEMain`, `saoptimization/SAOptimization` |
| Simulation core | `NetworkSimulator`, `network/Network` (access intervals), `generators/TrafficFactory` (policies), `network/NetworkAnalyzer` (collisions, statistics), `generators/PositionsFactory` |
| Optimisation | `deoptimization/*` (DE/current-to-best/1/bin), `saoptimization/*` (simulated annealing) |
| Analytical model | `scripts/lora_model.py` |
| Analysis and plots | `scripts/plot_*.py` |
| Utilities | `generators/ReportFactory` (JSON output), `scripts/repo_paths.py`, `scripts/structures/` |

## Reproducibility notes and model assumptions

* **Units and time.** Dates are ISO 8601 UTC; internally Unix epoch milliseconds. ToA, IAT and back-off
  times are in seconds at the interfaces and milliseconds inside the traffic generator. Angles in degrees.
  Orbital elements are osculating Keplerian elements (semi-major axis in metres, true anomaly) propagated by
  satellite-tools/Orekit; access uses EME2000 and ITRF (IERS 2010) frames.
* **Two sources of scenario settings.** Coverage (`coverage_<hash>.csv`) uses dates, step and elevation from
  the properties file; the traffic simulation uses the command-line values. Keep them identical.
* **Devices** are placed at 0 m altitude, uniformly in the spherical-cap ROI (planar-disc approximation), and
  are re-drawn at every run.
* **Traffic.** Offered load G = ToA / IAT. After each frame a device is silent for `(100 − DC) · ToA`
  (exact for DC = 1 %), or for the coverage-aware back-off `(2·N_ED·c − 1/G − 1)·ToA` (c: covered fraction of
  the ROI for the number of satellites in view) interpolated from the coverage series (CA, CA-NR); CA is
  lower-bounded by the duty-cycle time. Frames overlapping the start/end of
  an access interval are partial receptions. Propagation delay (slant range / c) is applied per satellite.
* **Collisions.** Single channel, no capture effect: any overlap of two receptions at the same satellite
  destroys both. A frame heard by k satellites counts as k receptions.
* **Model vs. simulation.** Both average throughput over the time steps with non-zero coverage.
* **Constellation design.** Altitude h maps to semi-major axis `h + 6371 km`; access is the mean ROI coverage
  over time steps with non-zero coverage; ISL rate uses Friis + Shannon capped at 7.5 Mbit/s.
* **Randomness.** The traffic simulator cannot be seeded (`SecureRandom`); DE and SA are seeded.

Known issues that may matter for numerical comparison with the paper are listed in `REPOSITORY_REVIEW.md`.

## Citation

If you use this software, please cite the associated publication (machine-readable metadata in
[`CITATION.cff`](CITATION.cff)):

> Santiago M. Henn, Juan Andrés Fraire, Nicola Accettura, Sandra Céspedes, Holger Hermanns.
> *Multi-Gateway LoRaWAN Throughput Modeling in Direct-to-Satellite IoT Constellations.* 2024.
> ⟨hal-04749023⟩

```bibtex
@unpublished{henn2024multigateway,
  author = {Henn, Santiago M. and Fraire, Juan Andr{\'e}s and Accettura, Nicola and
            C{\'e}spedes, Sandra and Hermanns, Holger},
  title  = {Multi-Gateway {LoRaWAN} Throughput Modeling in Direct-to-Satellite {IoT} Constellations},
  year   = {2024},
  note   = {HAL preprint hal-04749023},
  url    = {https://hal.science/hal-04749023v1}
}
```

If the paper appears in a journal or conference, cite that version instead.

## License

MIT, see [LICENSE](LICENSE).

## Authors and contact

Santiago Henn ([@santiagohenn](https://github.com/santiagohenn)). Questions and bug reports:
[GitHub issues](https://github.com/santiagohenn/lora-simulator/issues).
