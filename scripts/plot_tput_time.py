import numpy as np
import matplotlib.pyplot as plt
import os
import json
import pandas as pd
import seaborn as sns
from lora_model import get_tput
from structures.constellation import Constellation

def average_matrices(matrices):
    time_steps = len(matrices[0])
    average_matrix = np.zeros((time_steps, 8))
    for matrix in matrices:
        for row_idx, row in enumerate(matrix):
            for col_idx, col in enumerate(row):
                average_matrix[row_idx, col_idx] = average_matrix[row_idx, col_idx] + col

    average_matrix = average_matrix / len(matrices)
    return average_matrix

def deviation_matrices(matrices):
    matrices_without_timestamps = [np.array([row for row in matrix]) for matrix in matrices]
    average_matrix = np.std(matrices_without_timestamps, axis=0)
    return average_matrix

def load_simulation_stats(simulations_dir, file_name):
    file_name = os.path.join(simulations_dir, f'{file_name}.json')
    print("loading simulation file: ", file_name)
    coverage_stats = pd.read_json(file_name).values
    return coverage_stats

def load_simulations_results(simulations_dir, nodes, sample_time_ms, time_on_air_ms, satellite_height_km):
    
    simulation_file_name = f'{chosen_node_count}ED_{sample_time_ms}ms_{time_on_air_ms}ms_{satellite_height_km}km'
    print(f'loading simulation for {nodes} EDs: {simulation_file_name}')

    simulation_results = load_simulation_stats(simulations_dir, simulation_file_name)
    simulation_time_vector = np.array([step[0] / 1000.0 for step in simulation_results[0]])
    average_simulation_results = average_matrices(simulation_results)
    std_simulation_results = deviation_matrices(simulation_results)
    
    # now exctract the successful packets:
    successful_packets = average_simulation_results[:, 3]
    successful_packets_dev = std_simulation_results[:, 3]
    
    # Translate to TPUT:
    simulation_tput_avg = successful_packets * (time_on_air_ms / sample_time_ms)
    simulation_tput_std = successful_packets_dev * (time_on_air_ms / sample_time_ms)
        
    return simulation_time_vector, simulation_tput_avg, simulation_tput_std

color_palette = sns.color_palette()

# --- Parameters ---
results_dir = "C:/Projects/results/"
case_folder = "test1"
params_file = os.path.join(results_dir, case_folder, "simulation-parameters.json")
with open(params_file, "r") as f:
    params = json.load(f)

chosen_node_count = 60  # <-- Set your desired node count here

time_step_seconds = float(params["propagator_time_step_seconds"])
dc_percentage = float(params["duty_cycle_percentage"])
n_ch = 1
area_roi_m2 = float(params["roi_surface"])
time_on_air_seconds = float(params["time_on_air_seconds"])
avg_inter_arrival_time = float(params["average_inter_arrival_time_seconds"])
satellite_height_km = params["satellites"][0][24:28]
sample_time_seconds = float(params["sample_step_seconds"])
sample_time_ms = int(sample_time_seconds * 1000.0)
time_on_air_ms = int(time_on_air_seconds * 1000.0)

# --- Load constellation and coverage ---
constellation = Constellation()
constellation.read_from_csv(params["satellites_file"])
hash = constellation.generate_hash_256()
coverage_dir = os.path.join(results_dir, case_folder)
coverage_stats = np.loadtxt(os.path.join(coverage_dir, f'coverage_{hash}.csv'), delimiter=',')
time_steps = coverage_stats.shape[0]

# --- Model throughput vs time ---
ed_density = chosen_node_count / area_roi_m2
time_vector_model, model_tput_vs_time = get_tput(coverage_stats, dc_percentage, n_ch, ed_density, time_on_air_seconds, avg_inter_arrival_time, area_roi_m2)

# --- Simulation throughput vs time ---
simulations_dir = os.path.join(results_dir, case_folder)
simulation_time_vector, simulation_tput_avg, simulation_tput_std = load_simulations_results(simulations_dir, chosen_node_count, sample_time_ms, time_on_air_ms, satellite_height_km)

# --- Plot ---
color_palette = sns.color_palette()
plt.figure(figsize=(7, 4), dpi=150)
plt.plot(time_vector_model, model_tput_vs_time, label='Model Throughput', color=color_palette[0], linewidth=1.5)
plt.plot(simulation_time_vector, simulation_tput_avg, label='Simulation Throughput', color=color_palette[1], alpha=0.8, linewidth=1.5)
plt.fill_between(simulation_time_vector, 
                 simulation_tput_avg - simulation_tput_std, 
                 simulation_tput_avg + simulation_tput_std, 
                 color='orange', alpha=0.18, label='Simulation Std. Dev.')

plt.margins(0)
plt.grid(True, which='both', linestyle='--', linewidth=0.5, alpha=0.7)
plt.ylabel('Throughput', fontsize=12)
plt.xlabel('Time [s]', fontsize=12)
plt.xticks(fontsize=11)
plt.yticks(fontsize=11)
plt.legend(frameon=False, fontsize=11, loc='best')
plt.tight_layout(pad=1.5)
plt.box(False)
# plt.savefig('tput_vs_time_results.pdf', format='pdf', bbox_inches='tight', dpi=300)
plt.show()