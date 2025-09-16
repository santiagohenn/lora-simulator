import numpy as np
import os
import matplotlib.pyplot as plt
import seaborn as sns
import geopandas as gpd
import json
from shapely.geometry import Point, Polygon
from shapely.geometry import Point, LineString
import matplotlib.pyplot as plt
import matplotlib.lines as mlines
from structures.geotools import deploy_roi
import matplotlib.patches as mpatches
from matplotlib.offsetbox import OffsetImage, AnnotationBbox

OUTPUT_ROOT = 'C:/Projects/results/test1'
file_hash = "726eca42e2f257ba680f82bcca5e8cf70782e7fe82074233c74d465f7d5e19e4"

n_sats = 3
save_fig = False
plot_sat = True

longitude_limits = [-100, -20]
latitude_limits = [-80, 30]

WORLD_BOUNDRIES = os.path.join(os.path.dirname(__file__), "resources/countries/ne_110m_admin_0_countries.shp")
SAT_IMAGE = os.path.join(os.path.dirname(__file__), "resources/satelite.png")

snapshot_dir = f'{OUTPUT_ROOT}/snapshot_ne_polygons_{file_hash}.json'

case_dir = ""
roi_params = [-10.0, -60.0, 4.5, 100]
snapshot_time = 600000
time_step = 15000

santi_blue = [109/255,175/255,204/255]
color_palette = sns.color_palette("rocket")

world = gpd.read_file(WORLD_BOUNDRIES)

# Empty figure with axis
fig = plt.figure(figsize=(5, 5))
ax = fig.add_subplot()

# Basic worldmap
world.plot(
    ax=ax,
    color="lightgray",
    edgecolor="black",
    alpha=0.5,
    legend=True
)

# turn off axis ticks
ax.set_xticks([])
ax.set_yticks([])

# Plot ROI:
lats, longs = deploy_roi(roi_params[0], roi_params[1], roi_params[2], roi_params[3])
polygon = Polygon(zip(longs, lats))
gdf_polygon = gpd.GeoDataFrame(geometry=[polygon])
gdf_polygon.plot(ax=ax, edgecolor=santi_blue, facecolor=santi_blue, alpha=0.5, label = "End devices deployment area")

with open(snapshot_dir) as aap:
    json_data = json.load(aap)

for polygon_idx in range(len(json_data)):

    snapshot_index = snapshot_time // time_step
    lats = json_data[polygon_idx]['lats']
    longs = json_data[polygon_idx]['longs']
    
    n_gw_in_sight = json_data[polygon_idx]['nOfGwsInSight']

    points = [Point(lon, lat) for lat, lon in zip(lats, longs)]

    # Create a LineString from the points
    line = LineString(points)
    gdf_line = gpd.GeoDataFrame(geometry=[line])
    # gdf_line.plot(ax=ax, color = colors[idx], linewidth=2, marker='.', alpha=0.5)
    gdf_line.plot(ax=ax, color=color_palette[n_gw_in_sight], linewidth=2, marker='.', alpha=0.5, label = "Satellite Access Area")

    # Add longitude and latitude gridlines
    ax.set_xticks(range(-180, 181, 10))  # Longitude ticks every 10 degrees
    ax.set_yticks(range(-90, 91, 10))    # Latitude ticks every 10 degrees

    # Add gridlines
    ax.grid(True, which='both', linestyle='--', linewidth=0.5)

    # Hacky patch
    polygon_patch = mpatches.Patch(color=santi_blue, alpha=0.5, label='End devices deployment area')
    line_legend = mlines.Line2D([], [], color=color_palette[n_gw_in_sight], linewidth=2, label='Satellite Access Area')
    
    # Add the legend to the plot
    ax.legend(handles=[polygon_patch, line_legend])

    if plot_sat:

        img = plt.imread(SAT_IMAGE)  # Replace with the path to your PNG file
        imagebox = OffsetImage(img, zoom=0.10) 
        ab = AnnotationBbox(imagebox, (-72, -35), frameon=False)  # (x, y) coordinates to place the image
        ax.add_artist(ab)
    

plt.grid(True)
plt.xlim(longitude_limits)
plt.ylim(latitude_limits)
plt.xlabel('Longitude [Degrees]')
plt.ylabel('Latitude [Degrees]')
plt.tight_layout()
if save_fig:
    plt.savefig(os.path.join(OUTPUT_ROOT, 'aap_and_deployment_area.pdf'), format='pdf', bbox_inches='tight')
plt.show()

