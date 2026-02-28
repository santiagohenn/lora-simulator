from matplotlib.colors import LogNorm, PowerNorm
import matplotlib.pyplot as plt
import os
import json
import numpy as np

# ============================================================================
# PATH CONFIGURATION
# ============================================================================
INPUT_FILE = "C:/projects/results/access_vs_raan_and_phase/coverage_heatmap_raan-10_10_phase1_15.json"

OUTPUT_PATH = os.path.dirname(INPUT_FILE)

# ============================================================================
# SATELLITE SELECTION
# ============================================================================
# N_SATS = 1 -> averages[0] (acceso a 1 satélite)
# N_SATS = 2 -> averages[1] (acceso a 2 satélites), etc.
N_SATS = 1

# ============================================================================
# COLORMAP LIMITS
# ============================================================================
# None -> derive from data automatically
HEAT_MIN = None
HEAT_MAX = None

# True  -> LogNorm  (amplifica diferencias en valores bajos)
# False -> escala lineal
LOG_SCALE = False

# ============================================================================
# IEEE PUBLICATION SETTINGS
# ============================================================================
FIGURE_WIDTH  = 3.5
FIGURE_HEIGHT = 3.5

FONT_SIZE_LABELS   = 12
FONT_SIZE_TICKS    = 7
FONT_SIZE_COLORBAR = 12

LINEWIDTH_AXES = 0.6

# ============================================================================
# CONFIGURE MATPLOTLIB FOR IEEE STYLE
# ============================================================================
plt.rcdefaults()
plt.rcParams.update({
    'font.family': 'serif',
    'font.serif': ['Times New Roman', 'Times', 'DejaVu Serif'],
    'font.size': FONT_SIZE_TICKS,
    'axes.linewidth': LINEWIDTH_AXES,
    'grid.linewidth': 0.4,
    'lines.linewidth': 1.0,
    'xtick.major.width': LINEWIDTH_AXES,
    'ytick.major.width': LINEWIDTH_AXES,
    'xtick.major.size': 3,
    'ytick.major.size': 3,
    'pdf.fonttype': 42,
    'ps.fonttype': 42,
    'text.usetex': False,
})

# ============================================================================
# LOAD DATA
# ============================================================================
with open(INPUT_FILE, 'r') as f:
    data = json.load(f)

results = data['results']
col_idx = N_SATS - 1  # 0-based index into averages

# ============================================================================
# BUILD PIVOT GRID  (rows = phase, columns = raan)
# ============================================================================
raan_vals  = sorted(set(r['raan']  for r in results))
phase_vals = sorted(set(r['phase'] for r in results))

raan_arr  = np.array(raan_vals)
phase_arr = np.array(phase_vals)

heat = np.full((len(phase_arr), len(raan_arr)), np.nan)

raan_idx  = {v: i for i, v in enumerate(raan_vals)}
phase_idx = {v: i for i, v in enumerate(phase_vals)}

for r in results:
    ri   = raan_idx[r['raan']]
    pi   = phase_idx[r['phase']]
    avgs = r['averages']
    if col_idx < len(avgs):
        heat[pi, ri] = avgs[col_idx]

# ============================================================================
# CREATE FIGURE
# ============================================================================
fig, ax = plt.subplots(figsize=(FIGURE_WIDTH, FIGURE_HEIGHT))

# ============================================================================
# HEATMAP — plasma: perceptually uniform, print-safe, IEEE-suitable
# ============================================================================
step_raan  = raan_arr[1]  - raan_arr[0]  if len(raan_arr)  > 1 else 1.0
step_phase = phase_arr[1] - phase_arr[0] if len(phase_arr) > 1 else 1.0

extent = [
    raan_arr[0]   - step_raan  / 2,
    raan_arr[-1]  + step_raan  / 2,
    phase_arr[0]  - step_phase / 2,
    phase_arr[-1] + step_phase / 2,
]

# Build colour normalisation
if LOG_SCALE:
    _pos = heat[heat > 0]
    _vmin = (HEAT_MIN if (HEAT_MIN is not None and HEAT_MIN > 0)
             else (float(_pos.min()) if _pos.size else 1e-3))
    _vmax = HEAT_MAX if HEAT_MAX is not None else float(np.nanmax(heat))
    _norm = LogNorm(vmin=_vmin, vmax=_vmax)
    _vmin_arg, _vmax_arg = None, None
else:
    _norm     = None
    _vmin_arg = HEAT_MIN
    _vmax_arg = HEAT_MAX

im = ax.imshow(
    heat,
    aspect='auto',
    origin='lower',
    extent=extent,
    cmap='Blues',
    interpolation='nearest',
    norm=_norm,
    vmin=_vmin_arg,
    vmax=_vmax_arg,
)

# ============================================================================
# COLORBAR
# ============================================================================
cbar = fig.colorbar(im, ax=ax, pad=0.03, fraction=0.046)
cbar.set_label(f'Acceso promedio a {N_SATS} sat. (%)', fontsize=FONT_SIZE_COLORBAR, fontweight='normal')
cbar.ax.tick_params(labelsize=FONT_SIZE_TICKS, width=LINEWIDTH_AXES)
cbar.outline.set_linewidth(LINEWIDTH_AXES)

# ============================================================================
# AXIS CONFIGURATION
# ============================================================================
ax.set_xlabel('RAAN (\u00b0)', fontsize=FONT_SIZE_LABELS, weight='normal')
ax.set_ylabel('Distancia intersatelital (\u00b0)',  fontsize=FONT_SIZE_LABELS, weight='normal')
ax.tick_params(axis='both', labelsize=FONT_SIZE_TICKS)

ax.xaxis.set_major_locator(plt.MaxNLocator(nbins=6, integer=True))
ax.yaxis.set_major_locator(plt.MaxNLocator(nbins=6, integer=True))

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

base_name = os.path.splitext(os.path.basename(INPUT_FILE))[0]
out_stem  = f"{base_name}_sat{N_SATS}"

plt.savefig(os.path.join(OUTPUT_PATH, f'{out_stem}.pdf'),
            format='pdf', bbox_inches='tight', pad_inches=0.01, dpi=600)

print(f"Figura guardada en : {OUTPUT_PATH}")
print(f"Dimensiones        : {FIGURE_WIDTH} \u00d7 {FIGURE_HEIGHT} pulgadas")
print(f"Satélite           : N_SATS={N_SATS} (columna {col_idx} de 'averages')")
print(f"RAAN               : {raan_arr[0]:.1f}\u00b0 \u2192 {raan_arr[-1]:.1f}\u00b0  ({len(raan_arr)} valores)")
print(f"Fase               : {phase_arr[0]:.1f}\u00b0 \u2192 {phase_arr[-1]:.1f}\u00b0  ({len(phase_arr)} valores)")
print(f"Acceso m\u00ednimo       : {np.nanmin(heat):.4f}%")
print(f"Acceso m\u00e1ximo       : {np.nanmax(heat):.4f}%")
print(f"Acceso medio global: {np.nanmean(heat):.4f}%")

plt.show()
