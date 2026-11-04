import numpy as np
import matplotlib.pyplot as plt
import pandas as pd
import os
from lora_model import get_tput, plot_coverage_list
from structures.constellation import Constellation
from worldmap_snapshots_aaps import plot_snapshots

# Constants
earth_radius = 6371                     # Earth's EQ radius
time_step_seconds = 15                  # Time step in seconds
at_least_n_gws = 1                      # Minimum N of GW to achieve comm
nodes_start = 10                        # Initial Nodes
node_step = 10                          # Step
max_nodes = 130                         # Max Nodes
avg_inter_arrival_time = 0.1            # Average inter-arrival time [seconds]
time_on_air_ms = 500                    # Time on air [milliseconds]
time_on_air = time_on_air_ms / 1000     # Time on air [seconds]
dc_percentage = 1                       # DC Percentage
n_ch = 1                                # N of Channels
area_roi_m2 = 2.4706113361588345E12     # ROI Area
parent_dir = os.path.join(os.path.dirname(__file__), '..')

constellation_incs = range(0, 180, 10)
labels = [f'{inc} [°]' for inc in constellation_incs]
max_mean_tput = np.zeros(len(constellation_incs))
max_peak_tput = np.zeros(len(constellation_incs))

constellation = Constellation()
constellation.read_from_csv("C:/Users/Santi/Desktop/Doctorado/d3co_analysis/satellites.csv")

aaps_to_plot = []

plt.figure()

for inc_idx, inc in enumerate(constellation_incs):

    for satellite in constellation.satellites:
        satellite.inclination = inc
    
    hash = constellation.generate_hash_256()
    aaps_to_plot.append(hash)

    print(f'reading_for_{inc}_degrees_of_inc','hash:',hash)

    file_name = os.path.join(parent_dir, 'output/delta_inc', f'coverage_{hash}.csv')
    coverage_stats = pd.read_csv(file_name, header=None).values
    time_steps = coverage_stats.shape[0]
    n_gw = coverage_stats.shape[1] - 1
    node_n_of_steps = (max_nodes - nodes_start) // node_step + 1

    mu_analysis = np.zeros((time_steps, node_n_of_steps))
    mu_model = np.zeros(time_steps)
    time_measure = np.zeros(node_n_of_steps)

    mu_idx = 0
    for nodes in range(nodes_start, max_nodes + 1, node_step):
        ed_density = nodes / area_roi_m2
        mu_analysis[:, mu_idx] = get_tput(coverage_stats, dc_percentage, n_ch, ed_density, time_on_air, avg_inter_arrival_time, area_roi_m2)
        mu_idx += 1

    steps_with_access = mu_analysis[np.any(mu_analysis, axis=1), :]
    
    time_vector = np.linspace(0, len(steps_with_access) * time_step_seconds, len(steps_with_access))

    # signal_mean_model = np.trapz(steps_with_access, dx=time_step_seconds)
    signal_mean_model = np.mean(steps_with_access, axis=0)

    # print("steps with access", steps_with_access)

    if steps_with_access.size > 0:
        max_peak_tput[inc_idx] = np.max(steps_with_access)
        max_mean_tput[inc_idx] = max(signal_mean_model)
    else:
        max_peak_tput[inc_idx] = 0
        max_mean_tput[inc_idx] = 0

plt.plot(range(nodes_start, max_nodes + 1, node_step), signal_mean_model, '.-')
plt.grid(True)
plt.ylabel('Throughput [0 - 1]')
plt.ylim([0, 0.20])
plt.xlabel('N° of Ground Devices')

# print(steps_with_access)

# Generate labels for the legend

# plt.legend(labels, ncol=2, loc='best')

# plt.figure()
# plt.grid(True)
# plt.plot(list(constellation_incs), max_mean_tput)
# plt.ylabel('Average throughput [0 - 1]')
# plt.xlabel('RAAN [degrees]')

# plt.figure()
# plt.grid(True)
# plt.plot(list(constellation_incs), max_peak_tput)
# plt.ylim([0, 0.5])
# plt.ylabel('Peak throughput achieved [0 - 1]')
# plt.xlabel('Inclination [degrees]')

# plot_snapshots('delta_inc', aaps_to_plot, labels, [0, 0, 8, 200])
# plot_coverage_list('delta_inc', aaps_to_plot)
# plt.legend(labels, ncol=2, loc='best')

plt.show()