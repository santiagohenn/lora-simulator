"""Plot the simulated-annealing history written by the Java program ``saoptimization.SAOptimization``
(``results/sa_results/sa_history.csv``). The figure is written to the same folder.
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
SA_HISTORY_PATH = str(RESULTS_DIR / "sa_results" / "sa_history.csv")
OUTPUT_PATH     = str(RESULTS_DIR / "sa_results")

# ============================================================================
# IEEE PUBLICATION SETTINGS
# ============================================================================
FIGURE_WIDTH  = 7.0   # two-column IEEE width
FIGURE_HEIGHT = 5.0

FONT_SIZE_LABELS = 8
FONT_SIZE_TICKS  = 7
FONT_SIZE_LEGEND = 7
FONT_SIZE_TITLE  = 8

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
    'lines.linewidth': 0.8,
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
df = pd.read_csv(SA_HISTORY_PATH)
# private static final String CSV_HEADER =
            # "iter,T,profit,accessPct,dataRatePct,h,inc,phase,pTx," +
            # "bestProfit,bestAccessPct,bestDataRatePct,bestH,bestInc,bestPhase,bestPTx";
iterations    = df['iter'].values
temperature   = df['T'].values
currentPTx    = df['pTx'].values
bestPTx       = df['bestPTx'].values
currentH      = df['h'].values
bestH         = df['bestH'].values
currentInc    = df['inc'].values
bestInc       = df['bestInc'].values
currentPhase  = df['phase'].values
bestPhase     = df['bestPhase'].values

# ============================================================================
# COLORMAP — temperature-driven coloring so the reader can see heat
# ============================================================================
cmap = cm.get_cmap('turbo')
norm    = mcolors.Normalize(vmin=temperature.min(), vmax=temperature.max())
colors  = cmap(norm(temperature))

# ============================================================================
# PANEL DEFINITIONS
# ============================================================================
panels = [
    {
        'current_y':  currentPTx,
        'best_y':     bestPTx,
        'ylabel':     'Potencia TX $p_{Tx}$ (dBm)',
        'title':      '(a) Potencia de transmisión',
        'best_label': 'Solución Óptima',
    },
    {
        'current_y':  currentH,
        'best_y':     bestH,
        'ylabel':     'Altitud $h$ (km)',
        'title':      '(b) Altitud del satélite',
        'best_label': 'Solución Óptima',
    },
    {
        'current_y':  currentInc,
        'best_y':     bestInc,
        'ylabel':     'Inclinación (°)',
        'title':      '(c) Inclinación',
        'best_label': 'Solución Óptima',
    },
    {
        'current_y':  currentPhase,
        'best_y':     bestPhase,
        'ylabel':     'Separación de fase (°)',
        'title':      '(d) Separación de anomalía',
        'best_label': 'Solución Óptima',
    },
]

# ============================================================================
# CREATE 2×2 FIGURE
# ============================================================================
fig, axes = plt.subplots(2, 2, figsize=(FIGURE_WIDTH, FIGURE_HEIGHT))
axes = axes.flatten()

for ax, panel in zip(axes, panels):

    # ── Scatter of current values, coloured by temperature ──────────────────
    sc = ax.scatter(
        iterations, panel['current_y'],
        c=colors,
        s=4.0,                 # larger markers
        alpha=0.65,
        linewidths=0,
        zorder=2,
        label='Solución explorada (color = temperatura)',
    )

    # ── Best-so-far trajectory ───────────────────────────────────────────────
    ax.plot(
        iterations, panel['best_y'],
        color="#000000",
        linewidth=1.2,
        linestyle='-',
        zorder=3,
        label='Solución optimizada',
    )

    # ── Labels and title ─────────────────────────────────────────────────────
    ax.set_xlabel('Iteración', fontsize=FONT_SIZE_LABELS)
    ax.set_ylabel(panel['ylabel'], fontsize=FONT_SIZE_LABELS)
    ax.set_title(panel['title'], fontsize=FONT_SIZE_TITLE, pad=3)

    ax.margins(x=0.01)
    ax.xaxis.set_major_locator(plt.MaxNLocator(nbins=5, integer=True))
    ax.yaxis.set_major_locator(plt.MaxNLocator(nbins=5))
    ax.tick_params(axis='both', labelsize=FONT_SIZE_TICKS)

    # ── Grid and frame ───────────────────────────────────────────────────────
    ax.grid(True, linestyle=':', linewidth=LINEWIDTH_GRID,
            color='gray', alpha=0.5, zorder=0)
    ax.set_axisbelow(True)
    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)
    ax.spines['left'].set_linewidth(LINEWIDTH_AXES)
    ax.spines['bottom'].set_linewidth(LINEWIDTH_AXES)
    ax.set_facecolor('white')

    # ── Legend — removed per-panel legend; shared legend at bottom instead ───
    # (delete the ax.legend(...) block entirely)

# ============================================================================
# SHARED COLORBAR + LEGEND  (two-column layout at the bottom)
# ============================================================================
fig.patch.set_facecolor('white')
plt.tight_layout(pad=0.4, rect=[0, 0.13, 1, 1])

# Left column: legend
legend_ax = fig.add_axes([0.08, 0.02, 0.35, 0.10])
legend_ax.axis('off')
from matplotlib.lines import Line2D
legend_handles = [
    Line2D([0], [0], marker='o', color='none', markerfacecolor=cmap(norm(temperature.mean())),
           markersize=4, label='Solución actual'),
    Line2D([0], [0], color='#7AD151', linewidth=1.2, label='Solución optimizada'),
]
legend_ax.legend(
    handles=legend_handles,
    loc='center',
    fontsize=FONT_SIZE_LEGEND,
    framealpha=1.0,
    edgecolor='black',
    fancybox=False,
    handlelength=1.5,
    handletextpad=0.5,
    borderpad=0.6,
    labelspacing=0.4,
    ncol=1,
)

# Right column: colorbar
cbar_ax = fig.add_axes([0.57, 0.055, 0.35, 0.022])  # shifted right to make room for label
sm = cm.ScalarMappable(cmap=cmap, norm=norm)
sm.set_array([])
cbar = fig.colorbar(sm, cax=cbar_ax, orientation='horizontal')
cbar.ax.tick_params(labelsize=FONT_SIZE_TICKS, which='both', direction='out', pad=3)
cbar.outline.set_linewidth(LINEWIDTH_AXES)

# Temperature label to the left of the colorbar, vertically centred on it
fig.text(0.535, 0.066, 'Temperatura $T$',
         ha='right', va='center',
         fontsize=FONT_SIZE_LABELS)

# ============================================================================
# EXPORT
# ============================================================================
pdf_path = os.path.join(OUTPUT_PATH, 'sa_search_history.pdf')
png_path = os.path.join(OUTPUT_PATH, 'sa_search_history.png')

plt.savefig(pdf_path, format='pdf', bbox_inches='tight', pad_inches=0.05, dpi=600)
plt.savefig(png_path, format='png', bbox_inches='tight', pad_inches=0.05, dpi=600)

print(f"Figura guardada → {pdf_path}")
print(f"                  {png_path}")
print(f"Total de iteraciones graficadas: {len(iterations)}")
print(f"Rango de temperatura: {temperature.min():.4f} → {temperature.max():.4f}")
print(f"\nMejores valores finales:")
print(f"  pTx        : {bestPTx[-1]:.2f} dBm")
print(f"  h          : {bestH[-1]:.2f} km")
print(f"  Inclinación: {bestInc[-1]:.2f} °")
print(f"  Fase       : {bestPhase[-1]:.2f} °")

plt.show()
