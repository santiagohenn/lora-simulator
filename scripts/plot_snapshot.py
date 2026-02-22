import numpy as np
import os
import matplotlib.pyplot as plt
import matplotlib.lines as mlines
import matplotlib.patches as mpatches
from matplotlib.offsetbox import OffsetImage, AnnotationBbox
import geopandas as gpd
import json
from shapely.geometry import Point, Polygon, LineString
from structures.geotools import deploy_roi

# ============================================================================
# PATH CONFIGURATION
# ============================================================================
OUTPUT_ROOT = 'C:/Projects/results/test1'
FILE_HASH   = "726eca42e2f257ba680f82bcca5e8cf70782e7fe82074233c74d465f7d5e19e4"

WORLD_BOUNDARIES = os.path.join(os.path.dirname(__file__), "resources/countries/ne_110m_admin_0_countries.shp")
SAT_IMAGE        = os.path.join(os.path.dirname(__file__), "resources/satellite.png")

OUTPUT_PATH  = OUTPUT_ROOT

# ============================================================================
# PLOT CONFIGURATION
# ============================================================================
N_SATS        = 3
SAVE_FIG      = True
PLOT_SAT      = True

ROI_PARAMS    = [-10.0, -60.0, 4.5, 100]
SNAPSHOT_TIME = 600000
TIME_STEP     = 15000

LONGITUDE_LIMITS = [-100, -20]
LATITUDE_LIMITS  = [-80,   30]

# ============================================================================
# IEEE PUBLICATION SETTINGS
# ============================================================================
FIGURE_WIDTH  = 3.5
FIGURE_HEIGHT = 3.5      # Square — geographic maps benefit from equal aspect

FONT_SIZE_LABELS = 8
FONT_SIZE_TICKS  = 7
FONT_SIZE_LEGEND = 7

LINEWIDTH_AXES = 0.6
LINEWIDTH_GRID = 0.4

# Okabe-Ito colorblind-safe palette
COLOR_ROI = '#0072B2'    # Deployment region
# Satellite access area uses viridis palette indexed by n_gw_in_sight (kept as-is)
COLOR_WORLD_FACE = 'lightgray'
COLOR_WORLD_EDGE = '#444444'

SAT_ZOOM       = 0.5
SAT_POSITION   = (-72, -35)   # (lon, lat)

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
world        = gpd.read_file(WORLD_BOUNDARIES)
snapshot_dir = os.path.join(OUTPUT_ROOT, f'snapshot_ne_polygons_{FILE_HASH}.json')

with open(snapshot_dir) as f:
    json_data = json.load(f)

# ============================================================================
# CREATE FIGURE
# ============================================================================
fig, ax = plt.subplots(figsize=(FIGURE_WIDTH, FIGURE_HEIGHT))

# ============================================================================
# PLOT WORLD MAP
# ============================================================================
world.plot(
    ax=ax,
    color=COLOR_WORLD_FACE,
    edgecolor=COLOR_WORLD_EDGE,
    linewidth=0.3,
    alpha=0.6,
    zorder=1
)

# ============================================================================
# PLOT ROI
# ============================================================================
lats, longs = deploy_roi(ROI_PARAMS[0], ROI_PARAMS[1], ROI_PARAMS[2], ROI_PARAMS[3])
polygon     = Polygon(zip(longs, lats))
gdf_polygon = gpd.GeoDataFrame(geometry=[polygon])
gdf_polygon.plot(
    ax=ax,
    edgecolor=COLOR_ROI,
    facecolor=COLOR_ROI,
    linewidth=0.8,
    alpha=0.4,
    zorder=2
)

# ============================================================================
# PLOT SATELLITE ACCESS AREAS
# ============================================================================
from matplotlib.cm import get_cmap
viridis = get_cmap('viridis')

last_n_gw = 0
for polygon_idx in range(len(json_data)):
    lats         = json_data[polygon_idx]['lats']
    longs        = json_data[polygon_idx]['longs']
    n_gw_in_sight = json_data[polygon_idx]['nOfGwsInSight']
    last_n_gw    = n_gw_in_sight

    points   = [Point(lon, lat) for lat, lon in zip(lats, longs)]
    line     = LineString(points)
    gdf_line = gpd.GeoDataFrame(geometry=[line])
    gdf_line.plot(
        ax=ax,
        color=viridis(n_gw_in_sight / max(N_SATS, 1)),
        linewidth=1.2,
        alpha=0.7,
        zorder=3
    )

# ============================================================================
# SATELLITE IMAGE ANNOTATION
# ============================================================================
if PLOT_SAT:
    img      = plt.imread(SAT_IMAGE)
    imagebox = OffsetImage(img, zoom=SAT_ZOOM)
    ab       = AnnotationBbox(imagebox, SAT_POSITION, frameon=False, zorder=5)
    ax.add_artist(ab)

# ============================================================================
# AXIS CONFIGURATION
# ============================================================================
ax.set_xlim(LONGITUDE_LIMITS)
ax.set_ylim(LATITUDE_LIMITS)

ax.set_xlabel('Longitude (°)', fontsize=FONT_SIZE_LABELS, weight='normal')
ax.set_ylabel('Latitude (°)',  fontsize=FONT_SIZE_LABELS, weight='normal')

ax.xaxis.set_major_locator(plt.MaxNLocator(nbins=5))
ax.yaxis.set_major_locator(plt.MaxNLocator(nbins=5))
ax.tick_params(axis='both', labelsize=FONT_SIZE_TICKS)

# ============================================================================
# GRID AND FRAME
# ============================================================================
ax.grid(True, linestyle=':', linewidth=LINEWIDTH_GRID,
        color='gray', alpha=0.5, zorder=0)
ax.set_axisbelow(True)

ax.spines['top'].set_linewidth(LINEWIDTH_AXES)
ax.spines['right'].set_linewidth(LINEWIDTH_AXES)
ax.spines['left'].set_linewidth(LINEWIDTH_AXES)
ax.spines['bottom'].set_linewidth(LINEWIDTH_AXES)

# ============================================================================
# LEGEND
# ============================================================================
roi_patch  = mpatches.Patch(facecolor=COLOR_ROI, edgecolor=COLOR_ROI,
                             alpha=0.4, linewidth=0.8,
                             label='End Device Deployment Area')
sat_line   = mlines.Line2D([], [], color=viridis(last_n_gw / max(N_SATS, 1)),
                            linewidth=1.2, alpha=0.7,
                            label='Satellite Access Area')

ax.legend(handles=[roi_patch, sat_line],
          loc='lower left',
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

if SAVE_FIG:
    plt.savefig(os.path.join(OUTPUT_PATH, f'snapshot_{FILE_HASH}.pdf'),
                format='pdf', bbox_inches='tight', pad_inches=0.01, dpi=600)
    plt.savefig(os.path.join(OUTPUT_PATH, f'snapshot_{FILE_HASH}.png'),
                format='png', bbox_inches='tight', pad_inches=0.01, dpi=600)
    print(f"Figure saved to: {OUTPUT_PATH}snapshot_{FILE_HASH}.pdf")

print(f"Figure dimensions: {FIGURE_WIDTH} × {FIGURE_HEIGHT} inches")

# ============================================================================
# STATISTICS OUTPUT
# ============================================================================
n_polygons = len(json_data)
gw_counts  = [json_data[i]['nOfGwsInSight'] for i in range(n_polygons)]

print(f"\nSnapshot Statistics:")
print(f"  Polygons loaded  : {n_polygons}")
print(f"  Snapshot time    : {SNAPSHOT_TIME / 1000:.1f} s")
print(f"  GW in sight — mean: {np.mean(gw_counts):.2f}")
print(f"  GW in sight — min : {np.min(gw_counts)}")
print(f"  GW in sight — max : {np.max(gw_counts)}")

plt.show()

