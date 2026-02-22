from lora_model import get_tput, get_tput_wca, get_tput_wca_nr
from structures.constellation import Constellation
import numpy as np
import matplotlib.pyplot as plt
import pandas as pd
import os
import json
from scipy.integrate import simpson

# ============================================================================
# PATH CONFIGURATION
# ============================================================================
RESULTS_DIR = "C:/Projects/results/"
CASE_FOLDER = "bigsteps"
PARAMS_FOLDER = "pure-aloha"

OUTPUT_PATH = "./"

# ============================================================================
# Y-AXIS LIMITS CONFIGURATION
# ============================================================================
Y_MIN = None  # Set to None for automatic, or specify value
Y_MAX = None  # Set to None for automatic, or specify value

# ============================================================================
# IEEE PUBLICATION SETTINGS
# ============================================================================
FIGURE_WIDTH = 3.5
FIGURE_HEIGHT = 2.5

FONT_SIZE_LABELS = 8
FONT_SIZE_TICKS = 7
FONT_SIZE_LEGEND = 7

LINEWIDTH_AXES = 0.6
LINEWIDTH_GRID = 0.4

# Okabe-Ito colorblind-safe palette
COLOR_PA  = '#D55E00'   # Pure Aloha
COLOR_CA  = '#0072B2'   # Coverage Aware
COLOR_NR  = '#009E73'   # Coverage Aware No DC Restriction

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
# HELPER FUNCTIONS
# ============================================================================
def load_simulation_stats(simulations_dir, file_name):
    file_name = os.path.join(simulations_dir, f'{file_name}.json')
    print(file_name)
    return pd.read_json(file_name).values

def average_matrices(matrices):
    time_steps = len(matrices[0])
    average_matrix = np.zeros((time_steps, 8))
    for matrix in matrices:
        for row_idx, row in enumerate(matrix):
            for col_idx, col in enumerate(row):
                average_matrix[row_idx, col_idx] += col
    return average_matrix / len(matrices)

def deviation_matrices(matrices):
    matrices_without_timestamps = [np.array([row for row in matrix]) for matrix in matrices]
    return np.std(matrices_without_timestamps, axis=0)

def integrate_time_series(signal, std_devs):
    timestamps = signal[:, 0]
    metrics = signal[:, 1:]
    integrated_metrics = simpson(y=metrics, x=timestamps, axis=0)
    h = timestamps[1] - timestamps[0]
    integral_variance = (h / 3)**2 * (
        std_devs[0]**2 +
        4 * np.sum(std_devs[1:-1:2]**2) +
        2 * np.sum(std_devs[2:-2:2]**2) +
        std_devs[-1]**2
    )
    return integrated_metrics, np.sqrt(integral_variance)

def load_simulations_results(simulations_dir, nodes, sample_time_ms, time_on_air_ms, satellite_height_km):
    node_n_of_steps = len(nodes)
    simulation_tput_avg = np.zeros(node_n_of_steps)
    simulation_tput_std = np.zeros(node_n_of_steps)

    for idx, node in enumerate(nodes):
        print(f'loading_simulation_for_{node}_nodes')
        simulation_file_name = f'{node}ED_{sample_time_ms}ms_{time_on_air_ms}ms_{satellite_height_km}km'
        simulation_results = load_simulation_stats(simulations_dir, simulation_file_name)
        average_simulation_results = average_matrices(simulation_results)
        std_simulation_results = deviation_matrices(simulation_results)

        steps_with_access = average_simulation_results[np.any(average_simulation_results[:, 1:], axis=1), :]
        std_for_steps_with_access = std_simulation_results[np.any(std_simulation_results[:, 1:], axis=1), :]

        signal_mean, signal_std_dev = integrate_time_series(steps_with_access, std_for_steps_with_access)

        duration = steps_with_access[-1, 0] - steps_with_access[0][0]
        signal_mean /= duration
        signal_std_dev /= duration

        successful_packets = signal_mean[3]
        successful_packets_dev = signal_std_dev[3]

        simulation_tput_avg[idx] = successful_packets * (time_on_air_ms / sample_time_ms)
        simulation_tput_std[idx] = successful_packets_dev * (time_on_air_ms / sample_time_ms)

    return simulation_tput_avg, simulation_tput_std

# ============================================================================
# LOAD PARAMETERS
# ============================================================================
params_file = os.path.join(RESULTS_DIR, CASE_FOLDER, PARAMS_FOLDER, "simulation-parameters.json")
with open(params_file, "r") as f:
    params = json.load(f)

time_step_seconds       = float(params["propagator_time_step_seconds"])
nodes_start             = int(params["initial_nodes"])
node_step               = int(params["step_nodes"])
max_nodes               = int(params["max_nodes"])
avg_inter_arrival_time  = float(params["average_inter_arrival_time_seconds"])
time_on_air_seconds     = float(params["time_on_air_seconds"])
time_on_air_ms          = int(time_on_air_seconds * 1000.0)
dc_percentage           = float(params["duty_cycle_percentage"])
n_ch                    = 1
area_roi_m2             = float(params["roi_surface"])
satellite_height_km     = params["satellites"][0][24:28]
sample_time_seconds     = float(params["sample_step_seconds"])
sample_time_ms          = int(sample_time_seconds * 1000.0)

nodes = range(nodes_start, max_nodes + 1, node_step)

# ============================================================================
# LOAD CONSTELLATION AND COVERAGE
# ============================================================================
constellation = Constellation()
constellation.read_from_csv(params["satellites_file"])
hash = constellation.generate_hash_256()
coverage_dir = os.path.join(RESULTS_DIR, CASE_FOLDER, PARAMS_FOLDER)
coverage_stats = np.loadtxt(os.path.join(coverage_dir, f'coverage_{hash}.csv'), delimiter=',')
time_steps = coverage_stats.shape[0]
node_n_of_steps = (max_nodes - nodes_start) // node_step + 1

# ============================================================================
# COMPUTE MODEL: PURE ALOHA
# ============================================================================
mu_analysis = np.zeros((time_steps, node_n_of_steps))
for mu_idx, node in enumerate(nodes):
    print(f'computing_for_{node}_nodes (PA)')
    ed_density = node / area_roi_m2
    _, tput_result = get_tput(coverage_stats, dc_percentage, n_ch, ed_density,
                              time_on_air_seconds, avg_inter_arrival_time, area_roi_m2)
    mu_analysis[:, mu_idx] = tput_result

steps_with_access = mu_analysis[np.any(mu_analysis, axis=1), :]
signal_mean_model = np.mean(steps_with_access, axis=0)

# ============================================================================
# COMPUTE MODEL: COVERAGE-AWARE
# ============================================================================
mu_analysis = np.zeros((time_steps, node_n_of_steps))
for mu_idx, node in enumerate(nodes):
    print(f'computing_for_{node}_nodes (CA)')
    ed_density = node / area_roi_m2
    mu_analysis[:, mu_idx] = get_tput_wca(coverage_stats, dc_percentage, n_ch, ed_density,
                                           time_on_air_seconds, avg_inter_arrival_time, area_roi_m2)

steps_with_access = mu_analysis[np.any(mu_analysis, axis=1), :]
signal_mean_model_wca = np.mean(steps_with_access, axis=0)

# ============================================================================
# COMPUTE MODEL: COVERAGE-AWARE NO DC RESTRICTION
# ============================================================================
mu_analysis = np.zeros((time_steps, node_n_of_steps))
for mu_idx, node in enumerate(nodes):
    print(f'computing_for_{node}_nodes (CA-NR)')
    ed_density = node / area_roi_m2
    mu_analysis[:, mu_idx] = get_tput_wca_nr(coverage_stats, dc_percentage, n_ch, ed_density,
                                              time_on_air_seconds, avg_inter_arrival_time, area_roi_m2)

steps_with_access = mu_analysis[np.any(mu_analysis, axis=1), :]
signal_mean_model_wca_nr = np.mean(steps_with_access, axis=0)

# ============================================================================
# CREATE FIGURE
# ============================================================================
fig, ax = plt.subplots(figsize=(FIGURE_WIDTH, FIGURE_HEIGHT))

nodes_list = list(nodes)

# ============================================================================
# PLOT: PURE ALOHA
# ============================================================================
simulations_dir = os.path.join(RESULTS_DIR, CASE_FOLDER, "pure-aloha")
sim_avg_pa, sim_std_pa = load_simulations_results(
    simulations_dir, nodes, sample_time_ms, time_on_air_ms, satellite_height_km)

ax.plot(nodes_list, signal_mean_model,
        color=COLOR_PA, linewidth=1.2, linestyle='-',
        label='Model PA', zorder=3)
ax.fill_between(nodes_list,
                sim_avg_pa - sim_std_pa,
                sim_avg_pa + sim_std_pa,
                color=COLOR_PA, alpha=0.15, zorder=2)
ax.plot(nodes_list, sim_avg_pa,
        color=COLOR_PA, linewidth=1.0, linestyle='--',
        marker='o', markersize=3, markerfacecolor='white', markeredgewidth=0.8,
        label='Sim. PA', zorder=4)

# ============================================================================
# PLOT: COVERAGE-AWARE
# ============================================================================
simulations_dir = os.path.join(RESULTS_DIR, CASE_FOLDER, "coverage-aware")
sim_avg_ca, sim_std_ca = load_simulations_results(
    simulations_dir, nodes, sample_time_ms, time_on_air_ms, satellite_height_km)

ax.plot(nodes_list, signal_mean_model_wca,
        color=COLOR_CA, linewidth=1.2, linestyle='-',
        label='Model CA', zorder=3)
ax.fill_between(nodes_list,
                sim_avg_ca - sim_std_ca,
                sim_avg_ca + sim_std_ca,
                color=COLOR_CA, alpha=0.15, zorder=2)
ax.plot(nodes_list, sim_avg_ca,
        color=COLOR_CA, linewidth=1.0, linestyle='--',
        marker='s', markersize=3, markerfacecolor='white', markeredgewidth=0.8,
        label='Sim. CA', zorder=4)

# ============================================================================
# PLOT: COVERAGE-AWARE NO DC RESTRICTION
# ============================================================================
simulations_dir = os.path.join(RESULTS_DIR, CASE_FOLDER, "coverage-aware-nr")
sim_avg_nr, sim_std_nr = load_simulations_results(
    simulations_dir, nodes, sample_time_ms, time_on_air_ms, satellite_height_km)

ax.plot(nodes_list, signal_mean_model_wca_nr,
        color=COLOR_NR, linewidth=1.2, linestyle='-',
        label='Model CA-NDC%R', zorder=3)
ax.fill_between(nodes_list,
                sim_avg_nr - sim_std_nr,
                sim_avg_nr + sim_std_nr,
                color=COLOR_NR, alpha=0.15, zorder=2)
ax.plot(nodes_list, sim_avg_nr,
        color=COLOR_NR, linewidth=1.0, linestyle='--',
        marker='^', markersize=3, markerfacecolor='white', markeredgewidth=0.8,
        label='Sim. CA-NR', zorder=4)

# ============================================================================
# AXIS CONFIGURATION
# ============================================================================
ax.set_xlabel('Number of End Devices', fontsize=FONT_SIZE_LABELS, weight='normal')
ax.set_ylabel('Throughput', fontsize=FONT_SIZE_LABELS, weight='normal')

if Y_MIN is not None or Y_MAX is not None:
    current_ylim = ax.get_ylim()
    y_min = Y_MIN if Y_MIN is not None else current_ylim[0]
    y_max = Y_MAX if Y_MAX is not None else current_ylim[1]
    ax.set_ylim(y_min, y_max)

ax.xaxis.set_major_locator(plt.MaxNLocator(nbins=6))
ax.yaxis.set_major_locator(plt.MaxNLocator(nbins=6))
ax.tick_params(axis='both', labelsize=FONT_SIZE_TICKS)

# ============================================================================
# GRID AND FRAME
# ============================================================================
ax.grid(True, linestyle=':', linewidth=LINEWIDTH_GRID,
        color='gray', alpha=0.5, zorder=0)
ax.set_axisbelow(True)

ax.spines['top'].set_visible(False)
ax.spines['right'].set_visible(False)
ax.spines['left'].set_linewidth(LINEWIDTH_AXES)
ax.spines['bottom'].set_linewidth(LINEWIDTH_AXES)

# ============================================================================
# LEGEND
# ============================================================================
ax.legend(loc='upper center',
          bbox_to_anchor=(0.5, 1.0),
          fontsize=FONT_SIZE_LEGEND,
          framealpha=1.0,
          edgecolor='black',
          fancybox=False,
          frameon=True,
          handlelength=1.5,
          handletextpad=0.5,
          columnspacing=0.8,
          ncol=3)

# ============================================================================
# BACKGROUND
# ============================================================================
ax.set_facecolor('white')
fig.patch.set_facecolor('white')

# ============================================================================
# TIGHT LAYOUT AND EXPORT
# ============================================================================
plt.tight_layout(pad=0.2)

plt.savefig(f'{OUTPUT_PATH}coverage_awareness_results.pdf',
            format='pdf', bbox_inches='tight', pad_inches=0.01, dpi=600)
plt.savefig(f'{OUTPUT_PATH}coverage_awareness_results.png',
            format='png', bbox_inches='tight', pad_inches=0.01, dpi=600)

print(f"Figure saved to: {OUTPUT_PATH}coverage_awareness_results.pdf")
print(f"Figure dimensions: {FIGURE_WIDTH} × {FIGURE_HEIGHT} inches")

# ============================================================================
# STATISTICS OUTPUT
# ============================================================================
errors_pa   = (abs(signal_mean_model     - sim_avg_pa) / signal_mean_model)     * 100.0
errors_ca   = (abs(signal_mean_model_wca - sim_avg_ca) / signal_mean_model_wca) * 100.0
errors_nr   = (abs(signal_mean_model_wca_nr - sim_avg_nr) / signal_mean_model_wca_nr) * 100.0
improvement_ca = (abs(sim_avg_ca - signal_mean_model) / sim_avg_ca) * 100.0
improvement_nr = (abs(sim_avg_nr - signal_mean_model) / sim_avg_nr) * 100.0

print(f"\nModel error (%) vs. simulation:")
print(f"  PA  : mean={errors_pa.mean():.2f}%  min={errors_pa.min():.2f}%  max={errors_pa.max():.2f}%")
print(f"  CA  : mean={errors_ca.mean():.2f}%  min={errors_ca.min():.2f}%  max={errors_ca.max():.2f}%")
print(f"  CA-NR: mean={errors_nr.mean():.2f}%  min={errors_nr.min():.2f}%  max={errors_nr.max():.2f}%")
print(f"\nImprovement vs. PA model:")
print(f"  CA  : [{', '.join(map(str, improvement_ca))}]")
print(f"  CA-NR: [{', '.join(map(str, improvement_nr))}]")

plt.show()

