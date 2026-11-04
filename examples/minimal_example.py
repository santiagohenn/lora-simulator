"""Minimal, self-contained example of the analytical throughput model (scripts/lora_model.py).

It evaluates the three channel-access policies studied in this repository

  * PA    - pure ALOHA with a 1 % duty cycle,
  * CA    - coverage-aware back-off, never shorter than the duty-cycle silent time,
  * CA-NR - coverage-aware back-off without the duty-cycle lower bound,

for 50..80 end devices spread over a spherical-cap region of interest, using the same traffic
parameters as the Java launcher ``Main`` (ToA = 0.5 s, IAT = 0.1 s, DC = 1 %, 1 channel).

By default the ROI coverage time series is SYNTHETIC (a single satellite pass whose footprint covers
up to 60 % of the ROI, see ``synthetic_coverage``), so the example needs no Java run and no input
files. Pass ``--coverage results/pure-aloha/coverage_<hash>.csv`` to use a coverage file produced by
the simulator instead (then also pass the matching ``--roi-area-m2`` from simulation-parameters.json).

Usage:
    python examples/minimal_example.py [--coverage FILE] [--roi-area-m2 A] [--no-plot]

Outputs (in examples/output/):
    minimal_example_throughput.csv  mean throughput over the time steps with coverage, per policy
    minimal_example_throughput.png  the same, plotted
"""
import argparse
import math
import sys
from pathlib import Path

import numpy as np

REPO_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO_ROOT / "scripts"))
from lora_model import get_tput, get_tput_wca, get_tput_wca_nr  # noqa: E402

OUTPUT_DIR = Path(__file__).resolve().parent / "output"

# Parameters of the throughput experiment (see src/main/java/Main.java)
TIME_ON_AIR_S = 0.5
AVG_INTER_ARRIVAL_TIME_S = 0.1
DUTY_CYCLE_PERCENT = 1.0
N_CHANNELS = 1
NODES = range(50, 81, 10)
ROI_RADIUS_DEG = 7.1946
EARTH_RADIUS_M = 6371e3


def spherical_cap_area_m2(radius_deg, earth_radius_m=EARTH_RADIUS_M):
    """Area of a spherical cap of angular radius ``radius_deg`` [m^2]."""
    return 2 * math.pi * earth_radius_m ** 2 * (1 - math.cos(math.radians(radius_deg)))


def synthetic_coverage(duration_s=1800, step_s=15, pass_start_s=600, pass_duration_s=600,
                       peak_coverage_percent=60.0, n_columns=3):
    """Synthetic coverage table with the layout of ``coverage_<hash>.csv``.

    Column 0 is time [ms]; column 1 is a half-sine pass profile peaking at ``peak_coverage_percent``;
    columns 2..n_columns are zero (a single satellite in view). Illustrative only.
    """
    t = np.arange(0, duration_s + step_s, step_s, dtype=float)
    phase = (t - pass_start_s) / pass_duration_s
    profile = np.where((phase >= 0) & (phase <= 1), np.sin(np.pi * np.clip(phase, 0, 1)), 0.0)
    table = np.zeros((t.size, 1 + n_columns))
    table[:, 0] = t * 1000.0
    table[:, 1] = peak_coverage_percent * profile
    return table


def mean_over_covered_steps(series):
    """Average of a throughput time series over the steps with non-zero throughput (as in the paper scripts)."""
    covered = series[series > 0]
    return covered.mean() if covered.size else 0.0


def main():
    parser = argparse.ArgumentParser(description="Analytical throughput of PA, CA and CA-NR policies")
    parser.add_argument("--coverage", help="coverage_<hash>.csv produced by the simulator (default: synthetic)")
    parser.add_argument("--roi-area-m2", type=float, default=None,
                        help="ROI surface [m^2] (default: spherical cap of radius 7.1946 deg)")
    parser.add_argument("--no-plot", action="store_true", help="skip the PNG figure")
    args = parser.parse_args()

    coverage = np.loadtxt(args.coverage, delimiter=",") if args.coverage else synthetic_coverage()
    area_m2 = args.roi_area_m2 or spherical_cap_area_m2(ROI_RADIUS_DEG)
    source = args.coverage or "synthetic single-pass coverage"

    rows = []
    for n in NODES:
        density = n / area_m2
        common = (coverage, DUTY_CYCLE_PERCENT, N_CHANNELS, density, TIME_ON_AIR_S,
                  AVG_INTER_ARRIVAL_TIME_S, area_m2)
        _, s_pa = get_tput(*common)
        with np.errstate(divide="ignore", invalid="ignore"):   # zero-coverage steps divide by 0 inside the model
            s_ca = get_tput_wca(*common)
            s_nr = get_tput_wca_nr(*common)
        rows.append((n, mean_over_covered_steps(s_pa), mean_over_covered_steps(s_ca), mean_over_covered_steps(s_nr)))

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    csv_path = OUTPUT_DIR / "minimal_example_throughput.csv"
    np.savetxt(csv_path, np.array(rows), delimiter=",", fmt=["%d", "%.6f", "%.6f", "%.6f"],
               header="end_devices,throughput_PA,throughput_CA,throughput_CA_NR", comments="")

    print(f"Coverage: {source} | ROI area: {area_m2:.4e} m^2")
    print(f"{'EDs':>5} {'PA':>10} {'CA':>10} {'CA-NR':>10}")
    for n, pa, ca, nr in rows:
        print(f"{n:>5} {pa:>10.4f} {ca:>10.4f} {nr:>10.4f}")
    print("(PA and CA coincide whenever the coverage-aware back-off is shorter than the duty-cycle silent time.)")
    print(f"Wrote {csv_path}")

    if not args.no_plot:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        data = np.array(rows)
        fig, ax = plt.subplots(figsize=(4.5, 3))
        styles = (("Model PA", "o", "-"), ("Model CA", "s", "--"), ("Model CA-NR", "^", "-"))
        for col, (label, marker, ls) in zip((1, 2, 3), styles):
            ax.plot(data[:, 0], data[:, col], marker=marker, linestyle=ls, markerfacecolor="none", label=label)
        ax.set_xlabel("Number of end devices")
        ax.set_ylabel("Mean throughput")
        ax.set_title("Analytical model (" + ("synthetic coverage" if not args.coverage else "simulator coverage") + ")",
                     fontsize=9)
        ax.grid(True, linestyle=":")
        ax.legend()
        fig.tight_layout()
        png_path = OUTPUT_DIR / "minimal_example_throughput.png"
        fig.savefig(png_path, dpi=150)
        print(f"Wrote {png_path}")


if __name__ == "__main__":
    main()
