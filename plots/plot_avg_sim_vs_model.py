from lora_model import get_tput,get_tput_wca,get_tput_wca_nr
from structures.constellation import Constellation
import numpy as np
import matplotlib.pyplot as plt
import pandas as pd
import os
import seaborn as sns
from scipy.integrate import simpson

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

start_nodes = 10
step_nodes = 10
end_nodes = 130
nodes = range(start_nodes, end_nodes + 1, step_nodes)

# TODO: load all of these from an output file from the simulation

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
area_roi_m2 = 7.824428691510342E11      # ROI Area
satellite_height = 7471                 # Satellite height

sample_time_ms = 15000                  # Sample time of simulations

case_dir = "test_game_pdf"

parent_dir = os.path.dirname(__file__)
parent_dir = os.path.join(parent_dir, case_dir)

constellation = Constellation()
constellation.read_from_csv(os.path.join(parent_dir, "satellites.csv"))

aaps_to_plot = []
hash = constellation.generate_hash_256()
aaps_to_plot.append(hash)

coverage_dir = os.path.join(parent_dir, "pure-aloha")
coverage_stats = load_coverage_stats(coverage_dir, hash)
time_steps = coverage_stats.shape[0]
n_gw = coverage_stats.shape[1] - 1
node_n_of_steps = (max_nodes - nodes_start) // node_step + 1

## Model pure-aloha
node_n_of_steps = (max_nodes - nodes_start) // node_step + 1
mu_analysis = np.zeros((time_steps, node_n_of_steps))

for mu_idx,node in enumerate(nodes):
    print(f'computing_for_{node}_nodes')
    ed_density = node / area_roi_m2
    mu_analysis[:, mu_idx] = get_tput(coverage_stats, dc_percentage, n_ch, ed_density, time_on_air, avg_inter_arrival_time, area_roi_m2)

steps_with_access = mu_analysis[np.any(mu_analysis, axis=1), :]
time_vector = np.linspace(0, len(steps_with_access) * time_step_seconds, len(steps_with_access))
signal_mean_model = np.mean(steps_with_access, axis=0)

## Model with coverage-awareness
mu_analysis = np.zeros((time_steps, node_n_of_steps))

for mu_idx, node in enumerate(nodes):
    print(f'computing_for_{node}_nodes')
    ed_density = node / area_roi_m2
    mu_analysis[:, mu_idx] = get_tput_wca(coverage_stats, dc_percentage, n_ch, ed_density, time_on_air, avg_inter_arrival_time, area_roi_m2)

steps_with_access = mu_analysis[np.any(mu_analysis, axis=1), :]
time_vector = np.linspace(0, len(steps_with_access) * time_step_seconds, len(steps_with_access))
signal_mean_model_wca = np.mean(steps_with_access, axis=0)

## Model with coverage-awareness & no DC restrictions
mu_analysis = np.zeros((time_steps, node_n_of_steps))

for mu_idx, node in enumerate(nodes):
    print(f'computing_for_{node}_nodes')
    ed_density = node / area_roi_m2
    mu_analysis[:, mu_idx] = get_tput_wca_nr(coverage_stats, dc_percentage, n_ch, ed_density, time_on_air, avg_inter_arrival_time, area_roi_m2)

steps_with_access = mu_analysis[np.any(mu_analysis, axis=1), :]
time_vector = np.linspace(0, len(steps_with_access) * time_step_seconds, len(steps_with_access))
signal_mean_model_wca_nr = np.mean(steps_with_access, axis=0)
print(signal_mean_model_wca_nr)

## Simulation
simulations_dir = os.path.join(parent_dir, "pure-aloha")
simulation_tput_avg, simulation_tput_std = load_simulations_results(simulations_dir, nodes, sample_time_ms, time_on_air_ms, satellite_height)

color_palette = sns.color_palette()

plt.figure(figsize=(6, 4), dpi=300)

## Pure aloha
plt.plot(nodes, signal_mean_model, alpha=0.8, label='Model (Pure-Aloha)', linestyle='-', marker=None, color=color_palette[0])
plt.fill_between(nodes, 
                 simulation_tput_avg - simulation_tput_std, 
                 simulation_tput_avg + simulation_tput_std, 
                 color=color_palette[0], alpha=0.15)
plt.plot(nodes, simulation_tput_avg, alpha=0.5, linestyle='dashed', label=r'Simulation $\pm 1 \sigma$ (Pure-Aloha)', marker='.', color=color_palette[0])
errors_mean_model = (abs(signal_mean_model - simulation_tput_avg) / signal_mean_model) * 100.0

## Coverage aware
simulations_dir = os.path.join(parent_dir, "coverage-aware")
simulation_tput_avg, simulation_tput_std = load_simulations_results(simulations_dir, nodes, sample_time_ms, time_on_air_ms, satellite_height)
plt.plot(nodes, signal_mean_model_wca, alpha=0.8, label='Model w/ Coverage Awareness', linestyle='-', marker=None, color=color_palette[1])
plt.fill_between(nodes, 
                 simulation_tput_avg - simulation_tput_std, 
                 simulation_tput_avg + simulation_tput_std, 
                 color=color_palette[1], alpha=0.15)
plt.plot(nodes, simulation_tput_avg, alpha=0.5, linestyle='dashed', label=r'Simulation w/ Coverage Awareness $\pm 1 \sigma$', marker='.', color=color_palette[1])
errors_mean_model_wca = (abs(signal_mean_model_wca - simulation_tput_avg) / signal_mean_model_wca) * 100.0
improvement_wca = (abs(simulation_tput_avg - signal_mean_model) / simulation_tput_avg) * 100.0

## Coverage aware no restrictions
simulations_dir = os.path.join(parent_dir, "coverage-aware-nr")
simulation_tput_avg, simulation_tput_std = load_simulations_results(simulations_dir, nodes, sample_time_ms, time_on_air_ms, satellite_height)
plt.plot(nodes, signal_mean_model_wca_nr, alpha=0.8, label='Model w/ CA, no DC restrictions', linestyle='-', marker=None, color=color_palette[2])
plt.fill_between(nodes, 
                 simulation_tput_avg - simulation_tput_std, 
                 simulation_tput_avg + simulation_tput_std, 
                 color=color_palette[2], alpha=0.15)
plt.plot(nodes, simulation_tput_avg, alpha=0.5, linestyle='dashed', label=r'Simulation, no DC restrictions $\pm 1 \sigma$', marker='.', color=color_palette[2])
errors_mean_model_wca_nr = (abs(signal_mean_model_wca_nr - simulation_tput_avg) / signal_mean_model_wca_nr) * 100.0
improvement_wca_nr = (abs(simulation_tput_avg - signal_mean_model) / simulation_tput_avg) * 100.0

print(errors_mean_model)
print(errors_mean_model_wca)
print(errors_mean_model_wca_nr)
print("Improvements w/ respect to pure aloha")
print("[",",".join(map(str, improvement_wca)),"]")
print()
print("[",",".join(map(str, improvement_wca_nr)),"]")

plt.margins(0)
plt.grid(False)
plt.ylabel('Throughput [0 - 1]')
# plt.ylim([0, 0.25])
plt.xlabel('N° of End Devices')
plt.legend()
plt.tight_layout()
plt.savefig('coverage_awareness_results.pdf', format='pdf', bbox_inches='tight')

plt.show()


