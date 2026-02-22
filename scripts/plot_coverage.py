import matplotlib.pyplot as plt
import os
import pandas as pd

# ============================================================================
# PATH CONFIGURATION
# ============================================================================
COVERAGE_DIR = "C:/Projects/results/test1/"
HASH = "726eca42e2f257ba680f82bcca5e8cf70782e7fe82074233c74d465f7d5e19e4"

OUTPUT_PATH = COVERAGE_DIR

# ============================================================================
# Y-AXIS LIMITS CONFIGURATION
# ============================================================================
Y_MIN = 0    # Set to None for automatic, or specify value
Y_MAX = 105  # Set to None for automatic, or specify value

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

# Okabe-Ito colorblind-safe palette
COLOR_COVERAGE = '#0072B2'

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

# ============================================================================
# CREATE FIGURE
# ============================================================================
fig, ax = plt.subplots(figsize=(FIGURE_WIDTH, FIGURE_HEIGHT))

# ============================================================================
# PLOT COVERAGE
# ============================================================================
ax.plot(coverage_stats[:, 0] / 1000.0,
        coverage_stats[:, 1],
        color=COLOR_COVERAGE, linewidth=1.2, linestyle='-',
        label='Coverage of Deployment Region',
        zorder=3)

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
ax.legend(loc='lower right',
          fontsize=FONT_SIZE_LEGEND,
          framealpha=1.0,
          edgecolor='black',
          fancybox=False,
          frameon=True,
          handlelength=1.5,
          handletextpad=0.5)

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

# ============================================================================
# STATISTICS OUTPUT
# ============================================================================
time_s   = coverage_stats[:, 0] / 1000.0
coverage = coverage_stats[:, 1]

print(f"\nCoverage Statistics:")
print(f"  Mean : {coverage.mean():.2f}%")
print(f"  Std  : {coverage.std():.2f}%")
print(f"  Min  : {coverage.min():.2f}%")
print(f"  Max  : {coverage.max():.2f}%")
print(f"\nTime Range:")
print(f"  Start   : {time_s.min():.1f} s")
print(f"  End     : {time_s.max():.1f} s")
print(f"  Duration: {time_s.max() - time_s.min():.1f} s")

plt.show()