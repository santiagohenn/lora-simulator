"""ILLUSTRATIVE figure: model coverage curves with SYNTHETIC Gaussian perturbations.

WARNING: this script does NOT read any grid-based coverage computation. The curves labelled
"Grid = 0.5°/2.5°/4.5°" are the analytical coverage of ``coverage_<HASH>.csv`` plus zero-mean
Gaussian noise whose standard deviations are hard-coded in ``SIGMA_LEVELS`` (seeded with
``RANDOM_SEED``). Do not present its output as grid-based results without stating this.
See REPOSITORY_REVIEW.md.
"""
from repo_paths import RESULTS_DIR
import matplotlib.pyplot as plt
import matplotlib.cm as cm
import matplotlib.colors as mcolors
import os
import pandas as pd
import numpy as np

# ============================================================================
# PATH CONFIGURATION
# ============================================================================
COVERAGE_DIR = str(RESULTS_DIR / "optimization")
HASH = "4c12aca38ff26b86b20b476712abb4e2517cd24c8b2d4a5756fe72dd8615c3cd"

OUTPUT_PATH = COVERAGE_DIR

# ============================================================================
# SERIES LIMIT CONFIGURATION
# ============================================================================
# None -> plot all available "Access to N satellites" columns
# int  -> plot only Access to 1..N (capped to available columns)
MAX_SATELLITES_TO_PLOT = 6

# ============================================================================
# NOISE CONFIGURATION
# ============================================================================
# Sigma per label and per K (satellite count).
# Shape: [len(SIGMA_LABELS)][K], where K index 0 -> K=1, index 1 -> K=2, ...
SIGMA_LEVELS = [
    [0, 0, 0, 0, 0, 0],  # Model
    [1.7, 1.6, 1.7, 1.281, 1.325, 0.682],  # Grid = 0.5°
    [1.9, 1.511, 2.265, 2.205, 2.295, 1.402],  # Grid = 2.5°
    [3.16, 1.965, 3.93, 4.278, 4.425, 2.785],  # Grid = 4.5°
]
SIGMA_LABELS = ["Model", "Grid = 0.5°", "Grid = 2.5°", "Grid = 4.5°"]
RANDOM_SEED = 42

# ============================================================================
# Y-AXIS LIMITS CONFIGURATION
# ============================================================================
Y_MIN = 0
Y_MAX = 105

# ============================================================================
# IEEE PUBLICATION SETTINGS
# ============================================================================
FIGURE_WIDTH  = 3.5
FIGURE_HEIGHT = 2.5

FONT_SIZE_LABELS = 8
FONT_SIZE_TICKS  = 7
FONT_SIZE_LEGEND = 7

LINEWIDTH_AXES = 0.6
LINEWIDTH_GRID = 0.4

# ============================================================================
# CONFIGURE MATPLOTLIB FOR IEEE STYLE
# ============================================================================
plt.rcdefaults()
plt.rcParams.update({
    'font.family': 'serif',
    'font.serif': ['Times New Roman', 'Times', 'DejaVu Serif'],
    'font.size': FONT_SIZE_TICKS,
    'axes.linewidth': LINEWIDTH_AXES,
    'grid.linewidth': LINEWIDTH_GRID,
    'lines.linewidth': 1.0,
    'xtick.major.width': LINEWIDTH_AXES,
    'ytick.major.width': LINEWIDTH_AXES,
    'xtick.major.size': 3,
    'ytick.major.size': 3,
    'legend.frameon': True,
    'legend.framealpha': 1.0,
    'legend.edgecolor': 'black',
    'legend.fancybox': False,
    'pdf.fonttype': 42,
    'ps.fonttype': 42,
    'text.usetex': False,
})

# ============================================================================
# LOAD DATA
# ============================================================================
def load_coverage_stats(coverage_dir, hash_id):
    file_name = os.path.join(coverage_dir, f'coverage_{hash_id}.csv')
    return pd.read_csv(file_name, header=None).values

coverage_stats = load_coverage_stats(COVERAGE_DIR, HASH)

# First column is time; remaining columns are Access to 1, 2, ... N satellites
time_s = coverage_stats[:, 0] / 1000.0
total_series = coverage_stats.shape[1] - 1

if MAX_SATELLITES_TO_PLOT is None:
    n_series = total_series
else:
    n_series = min(max(int(MAX_SATELLITES_TO_PLOT), 0), total_series)

if n_series < 1:
    raise ValueError("No satellite access series selected to plot. Check MAX_SATELLITES_TO_PLOT.")

if len(SIGMA_LEVELS) != len(SIGMA_LABELS):
    raise ValueError("SIGMA_LEVELS must have one row per SIGMA_LABELS entry.")

for row_idx, row in enumerate(SIGMA_LEVELS):
    if len(row) < n_series:
        raise ValueError(
            f"SIGMA_LEVELS[{row_idx}] has {len(row)} values but needs at least {n_series} (K=1..K={n_series})."
        )

# ============================================================================
# GENERATE NOISY DATA
# ============================================================================
rng = np.random.default_rng(RANDOM_SEED)

# shape: (n_sigma, n_time, n_series)
noisy_data = np.zeros((len(SIGMA_LEVELS), len(time_s), n_series), dtype=float)

for s_idx in range(len(SIGMA_LEVELS)):
    for i in range(n_series):
        base = coverage_stats[:, i + 1]
        sigma_k = SIGMA_LEVELS[s_idx][i]  # sigma depends on label and K

        # If access is 0% or 100%, enforce sigma=0 for that sample (no perturbation).
        sigma_per_sample = np.where((base <= 0.0) | (base >= 100.0), 0.0, sigma_k)

        noise = rng.normal(loc=0.0, scale=sigma_per_sample, size=base.shape)
        noisy = np.clip(base + noise, 0.0, 100.0)  # keep percentages valid
        noisy_data[s_idx, :, i] = noisy

# ============================================================================
# COLORMAP — Okabe-Ito (colorblind-safe, high distinguishability)
# ============================================================================
# Order maps to SIGMA_LABELS:
# [Model, Grid = 0.5°, Grid = 2.5°, Grid = 4.5°]
sigma_colors = [
    "#2B2B2B",  # black (Model)
    "#009E73",  # blue
    "#D55E00",  # vermillion
    "#0072B2",  # bluish green
]

if len(SIGMA_LEVELS) > len(sigma_colors):
    raise ValueError("Not enough Okabe-Ito colors configured for SIGMA_LEVELS.")

sigma_styles = [
    dict(lw=1.0, ls='-',  alpha=1.00, zorder=4),  # Model (more noticeable)
    dict(lw=1.0, ls='--', alpha=0.8, zorder=3),
    dict(lw=1.0, ls='--', alpha=0.8, zorder=3),
    dict(lw=1.0, ls='--', alpha=0.8, zorder=3),
]

# ============================================================================
# CREATE FIGURE
# ============================================================================
fig, ax = plt.subplots(figsize=(FIGURE_WIDTH, FIGURE_HEIGHT))

# ============================================================================
# PLOT SERIES (all K for each sigma level)
# Legend is only sigma/model labels
# ============================================================================
for i in range(n_series):
    for s_idx, (sigma_label, style) in enumerate(zip(SIGMA_LABELS, sigma_styles)):
        ax.plot(
            time_s,
            noisy_data[s_idx, :, i],
            color=sigma_colors[s_idx],
            linewidth=style["lw"],
            linestyle=style["ls"],
            alpha=style["alpha"],
            zorder=style["zorder"],
            label=sigma_label if i == 0 else None,
        )

# ============================================================================
# K ANNOTATIONS (placeholder positions; adjust freely)
# ============================================================================
arrow_symbols = ["→", "←", "↗", "↘"]

# ax.text(
#     380,
#     58,
#     f"K = {1} {arrow_symbols[0]}",
#     fontsize=FONT_SIZE_TICKS,
#     ha='left',
#     va='center',
#     color='black',
#     bbox=dict(facecolor='white', edgecolor='none', alpha=0.85, pad=0.2),
#     zorder=5,
# )

# ax.text(
#     460,
#     47,
#     f"K = {2} {arrow_symbols[0]}",
#     fontsize=FONT_SIZE_TICKS,
#     ha='left',
#     va='center',
#     color='black',
#     bbox=dict(facecolor='white', edgecolor='none', alpha=0.85, pad=0.2),
#     zorder=5,
# )

# ax.text(
#     525,
#     31,
#     f"K = {3} {arrow_symbols[0]}",
#     fontsize=FONT_SIZE_TICKS,
#     ha='left',
#     va='center',
#     color='black',
#     bbox=dict(facecolor='white', edgecolor='none', alpha=0.85, pad=0.2),
#     zorder=5,
# )

# ax.text(
#     616,
#     18,
#     f"K = {4} {arrow_symbols[0]}",
#     fontsize=FONT_SIZE_TICKS,
#     ha='left',
#     va='center',
#     color='black',
#     bbox=dict(facecolor='white', edgecolor='none', alpha=0.85, pad=0.2),
#     zorder=5,
# )

# ax.text(
#     700,
#     10,
#     f"K = {5} {arrow_symbols[0]}",
#     fontsize=FONT_SIZE_TICKS,
#     ha='left',
#     va='center',
#     color='black',
#     bbox=dict(facecolor='white', edgecolor='none', alpha=0.85, pad=0.2),
#     zorder=5,
# )

# ax.text(
#     1164,
#     4,
#     f"K = {6}+",
#     fontsize=FONT_SIZE_TICKS,
#     ha='left',
#     va='center',
#     color='black',
#     bbox=dict(facecolor='white', edgecolor='none', alpha=0.85, pad=0.2),
#     zorder=5,
# )

# Third case: 

# ax.text(
#     1947,
#     96,
#     f"K = 1",
#     fontsize=FONT_SIZE_TICKS,
#     ha='left',
#     va='center',
#     color='black',
#     bbox=dict(facecolor='white', edgecolor='none', alpha=0.85, pad=0.2),
#     zorder=5,
# )

# ax.text(
#     827,
#     84,
#     f"K = 2 {arrow_symbols[0]}",
#     fontsize=FONT_SIZE_TICKS,
#     ha='left',
#     va='center',
#     color='black',
#     bbox=dict(facecolor='white', edgecolor='none', alpha=0.85, pad=0.2),
#     zorder=5,
# )

# ax.text(
#     1630,
#     40,
#     f"{arrow_symbols[1]} K = 3",
#     fontsize=FONT_SIZE_TICKS,
#     ha='left',
#     va='center',
#     color='black',
#     bbox=dict(facecolor='white', edgecolor='none', alpha=0.85, pad=0.2),
#     zorder=5,
# )

# ax.text(
#     1250,
#     30,
#     f"{arrow_symbols[1]} K = 4",
#     fontsize=FONT_SIZE_TICKS,
#     ha='left',
#     va='center',
#     color='black',
#     bbox=dict(facecolor='white', edgecolor='none', alpha=0.85, pad=0.2),
#     zorder=5,
# )

ax.text(
    1416,
    6,
    f"K = 1",
    fontsize=FONT_SIZE_TICKS,
    ha='left',
    va='center',
    color='black',
    bbox=dict(facecolor='white', edgecolor='none', alpha=0.85, pad=0.2),
    zorder=5,
)

ax.text(
    1362,
    35,
    f"K = 2",
    fontsize=FONT_SIZE_TICKS,
    ha='left',
    va='center',
    color='black',
    bbox=dict(facecolor='white', edgecolor='none', alpha=0.85, pad=0.2),
    zorder=5,
)

ax.text(
    853,
    72,
    f"K = 3 {arrow_symbols[0]}",
    fontsize=FONT_SIZE_TICKS,
    ha='left',
    va='center',
    color='black',
    bbox=dict(facecolor='white', edgecolor='none', alpha=0.85, pad=0.2),
    zorder=5,
)

# ============================================================================
# AXIS CONFIGURATION
# ============================================================================
ax.set_xlabel('Tiempo (s)', fontsize=FONT_SIZE_LABELS, weight='normal')
ax.set_ylabel('Porcentaje de acceso a la ROI (%)', fontsize=FONT_SIZE_LABELS, weight='normal')

if Y_MIN is not None or Y_MAX is not None:
    current_ylim = ax.get_ylim()
    y_min = Y_MIN if Y_MIN is not None else current_ylim[0]
    y_max = Y_MAX if Y_MAX is not None else current_ylim[1]
    ax.set_ylim(y_min, y_max)

ax.margins(x=0)
ax.xaxis.set_major_locator(plt.MaxNLocator(nbins=6))
ax.yaxis.set_major_locator(plt.MaxNLocator(nbins=6))
ax.tick_params(axis='both', labelsize=FONT_SIZE_TICKS)

# ============================================================================
# GRID AND FRAME
# ============================================================================
ax.grid(True, linestyle=':', linewidth=LINEWIDTH_GRID,
        color='gray', alpha=0.5, zorder=0)
ax.set_axisbelow(True)

ax.spines['top'].set_visible(False)
ax.spines['right'].set_visible(False)
ax.spines['left'].set_linewidth(LINEWIDTH_AXES)
ax.spines['bottom'].set_linewidth(LINEWIDTH_AXES)

# ============================================================================
# LEGEND
# ============================================================================
legend = ax.legend(
    loc='upper right',
    # bbox_to_anchor=(2010, 50),
    # bbox_transform=ax.transData,
    fontsize=FONT_SIZE_LEGEND,
    framealpha=1.0,
    edgecolor='black',
    fancybox=False,
    frameon=True,
    handlelength=1.8,
    handletextpad=0.6,
    borderpad=0.6,
    labelspacing=0.4,
)
# Make "Model" entry slightly more noticeable in legend text
if legend is not None and legend.get_texts():
    legend.get_texts()[0].set_weight('semibold')

# ============================================================================
# BACKGROUND
# ============================================================================
ax.set_facecolor('white')
fig.patch.set_facecolor('white')

# ============================================================================
# TIGHT LAYOUT AND EXPORT
# ============================================================================
plt.tight_layout(pad=0.2)

plt.savefig(os.path.join(OUTPUT_PATH, f'coverage_comparison_{HASH}.pdf'),
            format='pdf', bbox_inches='tight', pad_inches=0.01, dpi=600)
plt.savefig(os.path.join(OUTPUT_PATH, f'coverage_comparison_{HASH}.png'),
            format='png', bbox_inches='tight', pad_inches=0.01, dpi=600)

print(f"Figure saved to: {OUTPUT_PATH}coverage_comparison_{HASH}.pdf")
print(f"Figure dimensions: {FIGURE_WIDTH} × {FIGURE_HEIGHT} inches")
print(f"Series plotted: {n_series} (K = 1 to K = {n_series})")
print(f"Noise sigmas used: {SIGMA_LEVELS}")

if n_series < total_series:
    print(f"Total available series: {total_series} (limited by MAX_SATELLITES_TO_PLOT={MAX_SATELLITES_TO_PLOT})")

# ============================================================================
# STATISTICS OUTPUT
# ============================================================================
print(f"\nCoverage statistics:")
print(f"  Time range: {time_s.min():.1f} s → {time_s.max():.1f} s ({time_s.max() - time_s.min():.1f} s)")
for i in range(n_series):
    print(f"\n  K = {i + 1}:")
    for s_idx, sigma_label in enumerate(SIGMA_LABELS):
        col = noisy_data[s_idx, :, i]
        print(f"    {sigma_label}: mean={col.mean():.2f}% | std={col.std():.2f}% | min={col.min():.2f}% | max={col.max():.2f}%")

print("Noise sigmas used (by label, K=1..K=n):")
for s_idx, sigma_label in enumerate(SIGMA_LABELS):
    print(f"  {sigma_label}: {SIGMA_LEVELS[s_idx][:n_series]}")

plt.show()