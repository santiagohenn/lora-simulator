import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns
import geopandas as gpd
import json
from shapely.geometry import Point, Polygon
from shapely.geometry import Point, LineString
import matplotlib.pyplot as plt
import matplotlib.lines as mlines

from structures.geotools import deploy_roi

scenario_hash = "15a7a35faadaa0b55668f734ecdb360316b026d82d0274e2b655007bca62adfc_1"
folder = "ss_550"
time_instant = 2580000
gw_in_sight = 1

# Low res world map
world = gpd.read_file("C:/Users/Santi/Desktop/Doctorado/d3co_analysis/resources/countries/ne_110m_admin_0_countries.shp")

with open('C:/Users/Santi/Desktop/Doctorado/d3co_analysis/output/' + folder + '/ne_polygons_' + scenario_hash + '.json') as aap:
    json_data = json.load(aap)

timestamped_aap = [aap_data for aap_data in json_data if aap_data.get("date") == time_instant and aap_data.get("nOfGwsInSight") == gw_in_sight]

lats = timestamped_aap[0]['lats']
longs = timestamped_aap[0]['longs']

print(lats)
print(longs)

# Empty figure with axis
fig = plt.figure(figsize=(10, 5))
ax = fig.add_subplot()

# Basic worldmap
world.plot(
    ax=ax,
    color="lightgray",
    edgecolor="black",
    alpha=0.5
)

# turn off axis ticks
ax.set_xticks([])
ax.set_yticks([])

polygon = Polygon(zip(longs, lats))
gdf_polygon = gpd.GeoDataFrame(geometry=[polygon])
gdf_polygon.plot(ax=ax, edgecolor='blue', facecolor='blue', alpha=0.5)

plt.show()

def plot_specific_hash(hash, folder):
    # Low res world map
    world = gpd.read_file("C:/Users/Santi/Desktop/Doctorado/d3co_analysis/resources/countries/ne_110m_admin_0_countries.shp")

    # Empty figure with axis
    fig = plt.figure(figsize=(10, 5))
    ax = fig.add_subplot()

    # Basic worldmap
    world.plot(
        ax=ax,
        color="lightgray",
        edgecolor="black",
        alpha=0.5
    )

    # turn off axis ticks
    ax.set_xticks([])
    ax.set_yticks([])

    # set the plot title
    plt.title("AAPs and ROI")

    with open('C:/Users/Santi/Desktop/Doctorado/d3co_analysis/output/' + folder + '/snapshot_ne_polygons_' + hash + '.json') as aap:
        json_data = json.load(aap)

    lats = json_data[0]['lats']
    longs = json_data[0]['longs']

    polygon = Polygon(zip(longs, lats))
    gdf_polygon = gpd.GeoDataFrame(geometry=[polygon])
    gdf_polygon.plot(ax=ax, edgecolor='blue', facecolor='blue', alpha=0.5)

    plt.show()

def plot_snapshots(folder, hash_list, labels, roi_params):

    colors = plt.cm.viridis(np.linspace(0, 1, len(hash_list)))

    # Low res world map
    world = gpd.read_file("C:/Users/Santi/Desktop/Doctorado/d3co_analysis/resources/countries/ne_110m_admin_0_countries.shp")

    # Empty figure with axis
    fig = plt.figure(figsize=(10, 5))
    ax = fig.add_subplot()

    # Basic worldmap
    world.plot(
        ax=ax,
        color="lightgray",
        edgecolor="black",
        alpha=0.5
    )

    # turn off axis ticks
    ax.set_xticks([])
    ax.set_yticks([])

    # Plot ROI:
    lats, longs = deploy_roi(roi_params[0], roi_params[1], roi_params[2], roi_params[3])
    polygon = Polygon(zip(longs, lats))
    gdf_polygon = gpd.GeoDataFrame(geometry=[polygon])
    gdf_polygon.plot(ax=ax, edgecolor='blue', facecolor='blue', alpha=0.5)

    legend_handles = list()
    for idx, hash in enumerate(hash_list):
        print('plotting',hash)
        with open('C:/Users/Santi/Desktop/Doctorado/d3co_analysis/output/' + folder + '/snapshot_ne_polygons_' + hash + '.json') as aap:
            json_data = json.load(aap)

        for sat_idx in range(3):

            lats = json_data[sat_idx]['lats']
            longs = json_data[sat_idx]['longs']

            points = [Point(lon, lat) for lat, lon in zip(lats, longs)]

            # Create a LineString from the points
            line = LineString(points)
            gdf_line = gpd.GeoDataFrame(geometry=[line])
            # gdf_line.plot(ax=ax, color = colors[idx], linewidth=2, marker='.', alpha=0.5)
            gdf_line.plot(ax=ax, color = colors[idx], linewidth=2, marker='.', alpha=0.5, legend=labels[idx])
            # Create a legend handle for each line
        
        legend_handles.append(mlines.Line2D([], [], color=colors[idx], linewidth=2, label=labels[idx]))
    #plt.legend(labels, ncol=2, loc='best')
    # plt.legend(ncol=2, loc='best')
    ax.legend(handles=legend_handles, ncol=2, loc='best')

