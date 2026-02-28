import numpy as np
import os
import matplotlib.pyplot as plt
import matplotlib.lines as mlines
import matplotlib.patches as mpatches
from matplotlib.offsetbox import OffsetImage, AnnotationBbox
from matplotlib.colors import Normalize
from matplotlib.cm import get_cmap
import json
from shapely.geometry import Point, Polygon, LineString
from structures.geotools import deploy_roi, deploy_roi_from_file
import cartopy.crs as ccrs
import cartopy.feature as cfeature
from cartopy.mpl.gridliner import LongitudeFormatter, LatitudeFormatter

# ============================================================================
# PATH CONFIGURATION
# ============================================================================
OUTPUT_ROOT = "C:/Projects/results/optimization"
FILE_HASH   = "6fa75f57d16493f962811740a38565f1aeed535e0cfaeddf6f6b1b2d6596b9cd"
ROI_FILE    = "C:/Projects/lora-simulator/inputs/rois/argentina_eez.csv"

SAT_IMAGE   = os.path.join(os.path.dirname(__file__), "resources/satellite.png")
OUTPUT_PATH = OUTPUT_ROOT

# ============================================================================
# PROJECTION CONFIGURATION
# ============================================================================
# LONGITUDE_LIMITS = [-180, 180]
# LATITUDE_LIMITS  = [ -90,  90]
LONGITUDE_LIMITS = [-97, -24]
LATITUDE_LIMITS  = [ -75,  -11]

# ============================================================================
# PLOT CONFIGURATION
# ============================================================================
N_SATS       = 4
SAVE_FIG     = True
PLOT_SAT     = False
USE_ROI_FILE = True

ROI_PARAMS    = [-10.0, -60.0, 4.5, 100]
SNAPSHOT_TIME = 600000
TIME_STEP     = 15000

# ============================================================================
# IEEE PUBLICATION SETTINGS  (mirrors plot_snapshot_polar.py)
# ============================================================================
FIGURE_WIDTH  = 3.5
FIGURE_HEIGHT = 3.5

FONT_SIZE_LABELS = 8
FONT_SIZE_TICKS  = 7
FONT_SIZE_LEGEND = 11

LINEWIDTH_AXES = 0.6
LINEWIDTH_GRID = 0.4

COLOR_ROI        = '#0072B2'
COLOR_WORLD_FACE = 'lightgray'
COLOR_WORLD_EDGE = '#444444'

SAT_ZOOM     = 0.5
SAT_POSITION = (-72, -35)   # (lon, lat)

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
# PROJECTION  — PlateCarree is the standard flat lat/lon projection
# ============================================================================
PROJECTION = ccrs.PlateCarree()
GEODETIC   = ccrs.Geodetic()

# ============================================================================
# LOAD DATA
# ============================================================================
snapshot_dir = os.path.join(OUTPUT_ROOT, f'snapshot_ne_polygons_{FILE_HASH}.json')
with open(snapshot_dir) as f:
    json_data = json.load(f)

gw_counts_all = sorted(set(json_data[i]['nOfGwsInSight'] for i in range(len(json_data))))

# ============================================================================
# CREATE FIGURE
# ============================================================================
fig = plt.figure(figsize=(FIGURE_WIDTH, FIGURE_HEIGHT))
ax  = fig.add_subplot(1, 1, 1, projection=PROJECTION)

ax.set_extent(
    [LONGITUDE_LIMITS[0], LONGITUDE_LIMITS[1],
     LATITUDE_LIMITS[0],  LATITUDE_LIMITS[1]],
    crs=PROJECTION
)

# ============================================================================
# PLOT WORLD MAP
# ============================================================================
ax.add_feature(
    cfeature.NaturalEarthFeature(
        'physical', 'land', '110m',
        facecolor=COLOR_WORLD_FACE,
        edgecolor=COLOR_WORLD_EDGE,
        linewidth=0.3,
    ),
    alpha=0.6,
    zorder=1
)
ax.add_feature(cfeature.OCEAN, facecolor='#e8f4f8', zorder=0)

# ============================================================================
# GRIDLINES (graticule) — same style as plot_snapshot_polar.py
# ============================================================================
gl = ax.gridlines(
    draw_labels=True,
    linewidth=LINEWIDTH_GRID,
    color='gray',
    alpha=0.5,
    linestyle=':',
    zorder=2
)
gl.top_labels   = False
gl.right_labels = False
gl.xformatter   = LongitudeFormatter()
gl.yformatter   = LatitudeFormatter()
gl.xlocator     = plt.FixedLocator(range(-180, 181, 60))   # every 60° lon
gl.ylocator     = plt.FixedLocator(range( -90,  91, 30))   # every 30° lat
gl.xlabel_style = {'size': FONT_SIZE_TICKS, 'color': 'gray', 'alpha': 0.8}
gl.ylabel_style = {'size': FONT_SIZE_TICKS, 'color': 'gray', 'alpha': 0.8}

# ============================================================================
# PLOT ROI
# ============================================================================
if USE_ROI_FILE:
    lats, longs, _ = deploy_roi_from_file(ROI_FILE)
else:
    lats, longs = deploy_roi(ROI_PARAMS[0], ROI_PARAMS[1], ROI_PARAMS[2], ROI_PARAMS[3])

roi_polygon = Polygon(zip(longs, lats))
ax.add_geometries(
    [roi_polygon],
    crs=GEODETIC,
    facecolor=COLOR_ROI,
    edgecolor=COLOR_ROI,
    linewidth=0.8,
    alpha=0.4,
    zorder=3
)

# ============================================================================
# PLOT SATELLITE ACCESS AREAS
# ============================================================================
viridis = get_cmap('viridis')
norm    = Normalize(vmin=0, vmax=N_SATS)

for polygon_idx in range(len(json_data)):
    seg_lats      = json_data[polygon_idx]['lats']
    seg_lons      = json_data[polygon_idx]['longs']
    n_gw_in_sight = json_data[polygon_idx]['nOfGwsInSight']

    if len(seg_lats) < 2:
        continue

    ax.plot(
        seg_lons, seg_lats,
        color=viridis(norm(n_gw_in_sight)),
        linewidth=1.2,
        alpha=0.7,
        transform=GEODETIC,
        zorder=4
    )

# ============================================================================
# SATELLITE IMAGE ANNOTATION
# ============================================================================
if PLOT_SAT:
    img      = plt.imread(SAT_IMAGE)
    imagebox = OffsetImage(img, zoom=SAT_ZOOM)
    sat_xy   = ax.projection.transform_point(SAT_POSITION[0], SAT_POSITION[1], GEODETIC)
    ab       = AnnotationBbox(imagebox, sat_xy, frameon=False, zorder=6)
    ax.add_artist(ab)

# ============================================================================
# FRAME
# ============================================================================
for spine in ax.spines.values():
    spine.set_linewidth(LINEWIDTH_AXES)

# ============================================================================
# LEGEND
# ============================================================================
roi_patch = mpatches.Patch(
    facecolor=COLOR_ROI, edgecolor=COLOR_ROI,
    alpha=0.4, linewidth=0.8,
    label='Región de Interés'
)

sat_handles = [
    mlines.Line2D([], [],
                  color=viridis(norm(n)),
                  linewidth=1.5,
                  alpha=0.9,
                  label=f'Acceso a {n} satélite{"s" if n != 1 else ""}')
    for n in gw_counts_all
]

ax.legend(
    handles=[roi_patch] + sat_handles,
    loc='lower right',
    fontsize=FONT_SIZE_LEGEND,
    framealpha=1.0,
    edgecolor='black',
    fancybox=False,
    frameon=True,
    handlelength=1.5,
    handletextpad=0.5,
    borderpad=0.6,
    labelspacing=0.4,
)

# ============================================================================
# BACKGROUND
# ============================================================================
ax.set_facecolor('white')
fig.patch.set_facecolor('white')

# ============================================================================
# TIGHT LAYOUT AND EXPORT
# ============================================================================
plt.tight_layout(pad=0.2)

if SAVE_FIG:
    out_pdf = os.path.join(OUTPUT_PATH, f'snapshot_{FILE_HASH}.pdf')
    out_png = os.path.join(OUTPUT_PATH, f'snapshot_{FILE_HASH}.png')
    plt.savefig(out_pdf, format='pdf', bbox_inches='tight', pad_inches=0.01, dpi=600)
    plt.savefig(out_png, format='png', bbox_inches='tight', pad_inches=0.01, dpi=600)
    print(f"Figure saved to: {out_pdf}")

print(f"Figure dimensions: {FIGURE_WIDTH} × {FIGURE_HEIGHT} inches")

# ============================================================================
# STATISTICS OUTPUT
# ============================================================================
n_polygons = len(json_data)
gw_counts  = [json_data[i]['nOfGwsInSight'] for i in range(n_polygons)]

print(f"\nSnapshot Statistics:")
print(f"  Polygons loaded   : {n_polygons}")
print(f"  Snapshot time     : {SNAPSHOT_TIME / 1000:.1f} s")
print(f"  GW in sight — mean: {np.mean(gw_counts):.2f}")
print(f"  GW in sight — min : {np.min(gw_counts)}")
print(f"  GW in sight — max : {np.max(gw_counts)}")
print(f"  Unique GW counts  : {gw_counts_all}")

plt.show()

