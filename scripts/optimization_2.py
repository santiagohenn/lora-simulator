from lora_model import get_tput,get_tput_wca,get_tput_wca_nr
from structures.constellation import Constellation
import numpy as np
import matplotlib.pyplot as plt
import pandas as pd
import os
import seaborn as sns
from scipy.integrate import simpson
import json

def load_coverage_stats(coverage_dir, hash):
    file_name = os.path.join(coverage_dir, f'coverage_{hash}.csv')
    coverage_stats = pd.read_csv(file_name, header=None).values
    return coverage_stats

results_dir = "C:/Projects/results/"
case_folder = "test2"

# Load simulation parameters from JSON file
params_file = os.path.join(results_dir, case_folder, "simulation-parameters.json")
with open(params_file, "r") as f:
    params = json.load(f)
    
time_step_seconds = float(params["propagator_time_step_seconds"])
at_least_n_gws = 1
nodes_start = int(params["initial_nodes"])
node_step = int(params["step_nodes"])
max_nodes = int(params["max_nodes"])
avg_inter_arrival_time = float(params["average_inter_arrival_time_seconds"])
time_on_air = float(params["time_on_air_seconds"])
time_on_air_ms = int(time_on_air * 1000.0)
dc_percentage = float(params["duty_cycle_percentage"])
n_ch = 1  # number of channels
area_roi_m2 = float(params["roi_surface"])
satellite_height_km = params["satellites"][0][24:28]
sample_time_seconds = float(params["sample_step_seconds"])
sample_time_ms = int(sample_time_seconds * 1000.0)

nodes = range(nodes_start, max_nodes + 1, node_step)

parent_dir = os.path.dirname(__file__)
#parent_dir = os.path.join(parent_dir, results_dir)

constellation = Constellation()
constellation.read_from_csv(params["satellites_file"])

hash = constellation.generate_hash_256()

coverage_dir = os.path.join(results_dir, case_folder)
coverage_stats = load_coverage_stats(coverage_dir, hash)
time_steps = coverage_stats.shape[0]
n_gw = coverage_stats.shape[1] - 1
node_n_of_steps = (max_nodes - nodes_start) // node_step + 1

# Log parameters
print(f"""
propagator_time_step_seconds: {time_step_seconds}
at_least_n_gws: {at_least_n_gws}
nodes_start: {nodes_start}
node_step: {node_step}
max_nodes: {max_nodes}
avg_inter_arrival_time: {avg_inter_arrival_time}
time_on_air: {time_on_air}
time_on_air_ms: {time_on_air_ms}
dc_percentage: {dc_percentage}
n_ch: {n_ch}
area_roi_m2: {area_roi_m2}
satellite_height_km: {satellite_height_km}
sample_time_seconds: {sample_time_seconds}
sample_time_ms: {sample_time_ms}
coverage_dir: {coverage_dir}
time_steps: {time_steps}
n_gw: {n_gw}
node_n_of_steps: {node_n_of_steps}
constellation: {constellation}
""")

## Model pure-aloha

mu_analysis = np.zeros((time_steps, node_n_of_steps))

for mu_idx, node in enumerate(nodes):
    print(f'computing_for_{node}_nodes')
    ed_density = node / area_roi_m2
    time_vector_model, mu_analysis[:, mu_idx] = get_tput(coverage_stats, dc_percentage, n_ch, ed_density, time_on_air, avg_inter_arrival_time, area_roi_m2)

steps_with_access = mu_analysis[np.any(mu_analysis, axis=1), :]
signal_mean_model = np.mean(steps_with_access, axis=0)


# Plotting
color_palette = sns.color_palette()

plt.figure(figsize=(6, 4), dpi=150)

## Pure aloha
plt.plot(nodes, signal_mean_model, alpha=0.8, label='Model (Pure-Aloha)', linestyle='-', marker=None, color=color_palette[0])

plt.margins(0)
plt.grid(True, which='both', linestyle='--', linewidth=0.5, alpha=0.7)
plt.ylabel('Throughput', fontsize=12)
plt.xlabel('Number of End Devices', fontsize=12)
plt.xticks(fontsize=11)
plt.yticks(fontsize=11)
plt.legend(frameon=False, fontsize=11, loc='best')
plt.tight_layout(pad=1.5)
plt.box(False)
# plt.savefig('coverage_awareness_results.pdf', format='pdf', bbox_inches='tight', dpi=300)
plt.show()

print("Coverage stats shape:", coverage_stats.shape)

