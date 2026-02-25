import matplotlib.pyplot as plt
import matplotlib.cm as cm
import matplotlib.colors as mcolors
import os
import pandas as pd
import numpy as np

# ============================================================================
# PATH CONFIGURATION
# ============================================================================
COVERAGE_DIR = "C:/Projects/results/optimization/"
HASH = "4c12aca38ff26b86b20b476712abb4e2517cd24c8b2d4a5756fe72dd8615c3cd"

OUTPUT_PATH = COVERAGE_DIR

# ============================================================================
# SERIES LIMIT CONFIGURATION
# ============================================================================
# None -> plot all available "Access to N satellites" columns
# int  -> plot only Access to 1..N (capped to available columns)
MAX_SATELLITES_TO_PLOT = 6

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

# ============================================================================
# COLORMAP  — same viridis palette used across all scripts
# ============================================================================
viridis = cm.get_cmap('viridis')
norm = mcolors.Normalize(vmin=0, vmax=max(1, n_series))

# ============================================================================
# CREATE FIGURE
# ============================================================================
fig, ax = plt.subplots(figsize=(FIGURE_WIDTH, FIGURE_HEIGHT))

# ============================================================================
# PLOT SERIES
# ============================================================================
for i in range(n_series):
    col_idx = i + 1
    n_sats = i + 1
    label = f'Access to {n_sats} satellite{"s" if n_sats != 1 else ""}'

    ax.plot(
        time_s,
        coverage_stats[:, col_idx],
        color=viridis(norm(n_sats)),
        linewidth=1.2,
        linestyle='-',
        label=label,
        zorder=3,
    )

# ============================================================================
# AXIS CONFIGURATION
# ============================================================================
ax.set_xlabel('Time (s)', fontsize=FONT_SIZE_LABELS, weight='normal')
ax.set_ylabel('Coverage (%)', fontsize=FONT_SIZE_LABELS, weight='normal')

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
ax.legend(loc='lower left',
          fontsize=FONT_SIZE_LEGEND,
          framealpha=1.0,
          edgecolor='black',
          fancybox=False,
          frameon=True,
          handlelength=1.5,
          handletextpad=0.5,
          borderpad=0.6,
          labelspacing=0.4)

# ============================================================================
# BACKGROUND
# ============================================================================
ax.set_facecolor('white')
fig.patch.set_facecolor('white')

# ============================================================================
# TIGHT LAYOUT AND EXPORT
# ============================================================================
plt.tight_layout(pad=0.2)

plt.savefig(os.path.join(OUTPUT_PATH, f'coverage_{HASH}.pdf'),
            format='pdf', bbox_inches='tight', pad_inches=0.01, dpi=600)
plt.savefig(os.path.join(OUTPUT_PATH, f'coverage_{HASH}.png'),
            format='png', bbox_inches='tight', pad_inches=0.01, dpi=600)

print(f"Figure saved to: {OUTPUT_PATH}coverage_{HASH}.pdf")
print(f"Figure dimensions: {FIGURE_WIDTH} × {FIGURE_HEIGHT} inches")
print(f"Series plotted: {n_series} (Access to 1–{n_series} satellites)")
if n_series < total_series:
    print(f"Total available series: {total_series} (limited by MAX_SATELLITES_TO_PLOT={MAX_SATELLITES_TO_PLOT})")

# ============================================================================
# STATISTICS OUTPUT
# ============================================================================
print(f"\nCoverage Statistics:")
print(f"  Time range : {time_s.min():.1f} s → {time_s.max():.1f} s ({time_s.max() - time_s.min():.1f} s)")
for i in range(n_series):
    col = coverage_stats[:, i + 1]
    n_sats = i + 1
    print(f"\n  Access to {n_sats} satellite{'s' if n_sats != 1 else ''}:")
    print(f"    Mean : {col.mean():.2f}%")
    print(f"    Std  : {col.std():.2f}%")
    print(f"    Min  : {col.min():.2f}%")
    print(f"    Max  : {col.max():.2f}%")

plt.show()