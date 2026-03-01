import matplotlib.pyplot as plt
import matplotlib.cm as cm
import numpy as np
import pandas as pd
import os

# ============================================================================
# PATH CONFIGURATION
# ============================================================================
INPUT_FILE  = "C:/projects/results/pareto/pareto_front.csv"
OUTPUT_PATH = os.path.dirname(INPUT_FILE)

# ============================================================================
# PHYSICAL SCALING
# ============================================================================
# Maximum ISL data rate of the hardware — set to match ISL_R_MAX_BPS in Pareto.java
MAX_DATARATE_MBPS = 7.5  # <-- SET THIS

# ============================================================================
# IEEE PUBLICATION SETTINGS
# ============================================================================
FIGURE_WIDTH  = 3.5
FIGURE_HEIGHT = 3.5

FONT_SIZE_LABELS = 9
FONT_SIZE_TICKS  = 7
FONT_SIZE_LEGEND = 7

LINEWIDTH_AXES   = 0.6
LINEWIDTH_FRONT  = 1.0
MARKER_SIZE_FRONT = 36

N_OF_SATELLITES = 1

Y_LIM_BOTTOM = None  # set to a float to fix the lower y-axis limit, e.g. 0.0
Y_LIM_TOP    = None  # set to a float to fix the upper y-axis limit, e.g. 100.0

# ============================================================================
# CONFIGURE MATPLOTLIB FOR IEEE STYLE
# ============================================================================
plt.rcdefaults()
plt.rcParams.update({
    'font.family':          'serif',
    'font.serif':           ['Times New Roman', 'Times', 'DejaVu Serif'],
    'font.size':            FONT_SIZE_TICKS,
    'axes.linewidth':       LINEWIDTH_AXES,
    'grid.linewidth':       0.4,
    'lines.linewidth':      LINEWIDTH_FRONT,
    'xtick.major.width':    LINEWIDTH_AXES,
    'ytick.major.width':    LINEWIDTH_AXES,
    'xtick.major.size':     3,
    'ytick.major.size':     3,
    'pdf.fonttype':         42,
    'ps.fonttype':          42,
    'text.usetex':          False,
})

# ============================================================================
# LOAD DATA
# ============================================================================
df = pd.read_csv(INPUT_FILE)

# ============================================================================
# PARETO FRONT  (maximise both objectives)
# A point i is non-dominated if no other point j satisfies:
#   data_rate[j] >= data_rate[i]  AND  avg_access[j] >= avg_access[i]
#   with at least one strict inequality.
# ============================================================================
def compute_pareto_mask(x, y):
    n = len(x)
    is_pareto = np.ones(n, dtype=bool)
    for i in range(n):
        if not is_pareto[i]:
            continue
        dominated = (
            (x >= x[i]) & (y >= y[i]) &
            ((x > x[i]) | (y > y[i]))
        )
        dominated[i] = False
        if dominated.any():
            is_pareto[i] = False
    return is_pareto

# ============================================================================
# CREATE FIGURE
# ============================================================================
fig, ax = plt.subplots(figsize=(FIGURE_WIDTH, FIGURE_HEIGHT))

# ============================================================================
# ONE PARETO FRONT PER POWER LEVEL
# ============================================================================
power_levels = sorted(df['power_db'].unique())
n_levels     = len(power_levels)

# Sequential colormap: darker = higher power
cmap   = cm.get_cmap('Blues', n_levels + 2)   # +2 to avoid the lightest shades
colors = [cmap(2 + i) for i in range(n_levels)]

for i, p in enumerate(power_levels):
    sub = df[df['power_db'] == p]

    data_rate  = sub['data_rate_pct'].values / 100.0 * MAX_DATARATE_MBPS  # Mbps
    avg_access = sub['avg_access_pct'].values                              # %

    pareto_mask = compute_pareto_mask(data_rate, avg_access)

    pf_x = data_rate[pareto_mask]
    pf_y = avg_access[pareto_mask]

    sort_idx     = np.argsort(pf_x)
    pf_x_sorted  = pf_x[sort_idx]
    pf_y_sorted  = pf_y[sort_idx]

    ax.scatter(
        pf_x_sorted,
        pf_y_sorted,
        s=MARKER_SIZE_FRONT,
        color=colors[i],
        marker='o',
        edgecolors='white',
        linewidths=0.4,
        zorder=4,
        label=f'P = {p:.0f} dBm',
    )

# ============================================================================
# AXIS CONFIGURATION
# ============================================================================
ax.set_xlabel('Tasa de datos (Mbps)', fontsize=FONT_SIZE_LABELS, weight='normal')
ax.set_ylabel(f'Acceso promedio (%) - Al menos {N_OF_SATELLITES} satélite(s)',  fontsize=FONT_SIZE_LABELS, weight='normal')
ax.tick_params(axis='both', labelsize=FONT_SIZE_TICKS)

ax.xaxis.set_major_locator(plt.MaxNLocator(nbins=6))
ax.yaxis.set_major_locator(plt.MaxNLocator(nbins=6))

ax.set_xlim(left=0)
ax.set_ylim(bottom=Y_LIM_BOTTOM, top=Y_LIM_TOP)

# ============================================================================
# GRID
# ============================================================================
ax.grid(True, linestyle='--', linewidth=0.4, color='#CCCCCC', zorder=1)
ax.set_axisbelow(True)

# ============================================================================
# LEGEND
# ============================================================================
legend = ax.legend(
    title='Potencia de transmisión',
    title_fontsize=FONT_SIZE_LEGEND,
    fontsize=FONT_SIZE_LEGEND,
    frameon=True,
    framealpha=1.0,
    edgecolor='#AAAAAA',
    fancybox=False,
    loc='lower left',
)
legend.get_frame().set_linewidth(LINEWIDTH_AXES)

# ============================================================================
# FRAME
# ============================================================================
for spine in ax.spines.values():
    spine.set_linewidth(LINEWIDTH_AXES)

# ============================================================================
# BACKGROUND
# ============================================================================
fig.patch.set_facecolor('white')
ax.set_facecolor('white')

# ============================================================================
# TIGHT LAYOUT AND EXPORT
# ============================================================================
plt.tight_layout(pad=0.2)

out_stem = os.path.splitext(os.path.basename(INPUT_FILE))[0]

plt.savefig(os.path.join(OUTPUT_PATH, f'{out_stem}.pdf'),
            format='pdf', bbox_inches='tight', pad_inches=0.01, dpi=600)
plt.savefig(os.path.join(OUTPUT_PATH, f'{out_stem}.png'),
            format='png', bbox_inches='tight', pad_inches=0.01, dpi=300)

print(f"Output path     : {OUTPUT_PATH}")
print(f"Figure size     : {FIGURE_WIDTH} x {FIGURE_HEIGHT} in")
print(f"MAX_DATARATE    : {MAX_DATARATE_MBPS} Mbps")
print(f"Niveles de potencia: {power_levels}")
print(f"Total puntos    : {len(df)}")

plt.show()
