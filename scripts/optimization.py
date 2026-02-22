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

def load_simulation_stats(simulations_dir, file_name):
    file_name = os.path.join(simulations_dir, f'{file_name}.json')
    print(file_name)
    coverage_stats = pd.read_json(file_name).values
    return coverage_stats

def average_matrices(matrices):
    time_steps = len(matrices[0])
    average_matrix = np.zeros((time_steps, 8))
    for matrix in matrices:
        for row_idx, row in enumerate(matrix):
            for col_idx, col in enumerate(row):
                average_matrix[row_idx, col_idx] = average_matrix[row_idx, col_idx] + col

    average_matrix = average_matrix / len(matrices)
    return average_matrix

def integrate_time_series(signal, std_devs):
    timestamps = signal[:, 0]
    metrics = signal[:, 1:]
    integrated_metrics = simpson(y=metrics, x=timestamps, axis=0)
    
    h = timestamps[1] - timestamps[0]
    # Error propagation for standard deviation of the integral using simpson's rule
    integral_variance = (h / 3)**2 * (
        std_devs[0]**2 + 
        4 * np.sum(std_devs[1:-1:2]**2) + 
        2 * np.sum(std_devs[2:-2:2]**2) + 
        std_devs[-1]**2
    )
    
    integral_std_dev = np.sqrt(integral_variance)
    return integrated_metrics, integral_std_dev

def deviation_matrices(matrices):

    # Convert the matrices into NumPy arrays (excluding the timestamp column)
    matrices_without_timestamps = [np.array([row for row in matrix]) for matrix in matrices]
    
    # Stack the matrices and compute the mean across the first axis (averaging the matrices)
    average_matrix = np.std(matrices_without_timestamps, axis=0)

    return average_matrix

def load_simulations_results(simulations_dir, nodes, sample_time_ms, time_on_air_ms, satellite_height_km):
    
    node_n_of_steps = len(nodes)
    simulation_tput_avg = np.zeros(node_n_of_steps)
    simulation_tput_std = np.zeros(node_n_of_steps)

    for idx, node in enumerate(nodes):
        
        print(f'loading_simulation_for_{node}_nodes')
        node_n_of_steps = (max_nodes - nodes_start) // node_step + 1

        simulation_file_name = f'{node}ED_{sample_time_ms}ms_{time_on_air_ms}ms_{satellite_height_km}km'

        simulation_results = load_simulation_stats(simulations_dir, simulation_file_name)
        average_simulation_results = average_matrices(simulation_results)
        std_simulation_results = deviation_matrices(simulation_results)
        
        steps_with_access = average_simulation_results[np.any(average_simulation_results[:,1:], axis = 1), :]
        std_for_steps_with_access = std_simulation_results[np.any(std_simulation_results[:,1:], axis = 1), :]
        
        # index 3 are successful transmissions
        signal_mean, signal_std_dev = integrate_time_series(steps_with_access, std_for_steps_with_access)
        
        signal_mean = signal_mean / (steps_with_access[-1, 0] - steps_with_access[0][0])
        signal_std_dev = signal_std_dev / (steps_with_access[-1, 0] - steps_with_access[0][0])

        # now exctract the successful packets:
        successful_packets = signal_mean[3]
        successful_packets_dev = signal_std_dev[3]
        
        # Translate to TPUT:
        simulation_tput_avg[idx] = successful_packets * (time_on_air_ms / sample_time_ms)
        simulation_tput_std[idx] = successful_packets_dev * (time_on_air_ms / sample_time_ms)

    return simulation_tput_avg, simulation_tput_std

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

aaps_to_plot = []
hash = constellation.generate_hash_256()
aaps_to_plot.append(hash)

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

## Simulation
simulations_dir = os.path.join(results_dir, case_folder)
simulation_tput_avg, simulation_tput_std = load_simulations_results(simulations_dir, nodes, sample_time_ms, time_on_air_ms, satellite_height_km)


# Plotting
color_palette = sns.color_palette()

plt.figure(figsize=(6, 4), dpi=150)

## Pure aloha
plt.plot(nodes, signal_mean_model, alpha=0.8, label='Model (Pure-Aloha)', linestyle='-', marker=None, color=color_palette[0])
plt.fill_between(nodes, 
                 simulation_tput_avg - simulation_tput_std, 
                 simulation_tput_avg + simulation_tput_std, 
                 color=color_palette[0], alpha=0.15)
plt.plot(nodes, simulation_tput_avg, alpha=0.5, linestyle='dashed', label=r'Simulation $\pm 1 \sigma$ (Pure-Aloha)', marker='.', color=color_palette[0])
errors_mean_model = (abs(signal_mean_model - simulation_tput_avg) / signal_mean_model) * 100.0

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

