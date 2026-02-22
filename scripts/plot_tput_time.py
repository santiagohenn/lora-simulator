import numpy as np
import matplotlib.pyplot as plt
import os
import json
import pandas as pd
from lora_model import get_tput
from structures.constellation import Constellation

# ============================================================================
# PATH CONFIGURATION
# ============================================================================
RESULTS_DIR = "C:/Projects/results/"
CASE_FOLDER = "test1"

OUTPUT_PATH = RESULTS_DIR

# ============================================================================
# PLOT CONFIGURATION
# ============================================================================
CHOSEN_NODE_COUNT = 120  # Set your desired node count here

# ============================================================================
# Y-AXIS LIMITS CONFIGURATION
# ============================================================================
Y_MIN = None  # Set to None for automatic, or specify value
Y_MAX = None  # Set to None for automatic, or specify value

# ============================================================================
# IEEE PUBLICATION SETTINGS
# ============================================================================
FIGURE_WIDTH  = 3.5
FIGURE_HEIGHT = 2.5

FONT_SIZE_LABELS = 8
FONT_SIZE_TICKS  = 7
FONT_SIZE_LEGEND = 7

LINEWIDTH_AXES = 0.6
LINEWIDTH_GRID = 0.4

# Okabe-Ito colorblind-safe palette — consistent across all scripts
COLOR_MODEL = '#D55E00'   # Model throughput      (same as COLOR_PA)
COLOR_SIM   = '#0072B2'   # Simulation throughput (same as COLOR_CA)

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

def load_simulation_stats(simulations_dir, file_name):
    file_path = os.path.join(simulations_dir, f'{file_name}.json')
    print(f"Loading simulation file: {file_path}")
    return pd.read_json(file_path).values

def load_simulations_results(simulations_dir, nodes, sample_time_ms, time_on_air_ms, satellite_height_km):
    simulation_file_name = f'{nodes}ED_{sample_time_ms}ms_{time_on_air_ms}ms_{satellite_height_km}km'
    print(f'Loading simulation for {nodes} EDs: {simulation_file_name}')

    simulation_results   = load_simulation_stats(simulations_dir, simulation_file_name)
    simulation_time_vector = np.array([step[0] / 1000.0 for step in simulation_results[0]])

    average_simulation_results = average_matrices(simulation_results)
    std_simulation_results     = deviation_matrices(simulation_results)

    successful_packets     = average_simulation_results[:, 3]
    successful_packets_dev = std_simulation_results[:, 3]

    simulation_tput_avg = successful_packets     * (time_on_air_ms / sample_time_ms)
    simulation_tput_std = successful_packets_dev * (time_on_air_ms / sample_time_ms)

    return simulation_time_vector, simulation_tput_avg, simulation_tput_std

# ============================================================================
# LOAD PARAMETERS
# ============================================================================
params_file = os.path.join(RESULTS_DIR, CASE_FOLDER, "simulation-parameters.json")
with open(params_file, "r") as f:
    params = json.load(f)

time_step_seconds      = float(params["propagator_time_step_seconds"])
dc_percentage          = float(params["duty_cycle_percentage"])
n_ch                   = 1
area_roi_m2            = float(params["roi_surface"])
time_on_air_seconds    = float(params["time_on_air_seconds"])
avg_inter_arrival_time = float(params["average_inter_arrival_time_seconds"])
satellite_height_km    = params["satellites"][0][24:28]
sample_time_seconds    = float(params["sample_step_seconds"])
sample_time_ms         = int(sample_time_seconds * 1000.0)
time_on_air_ms         = int(time_on_air_seconds * 1000.0)

# ============================================================================
# LOAD CONSTELLATION AND COVERAGE
# ============================================================================
constellation = Constellation()
constellation.read_from_csv(params["satellites_file"])
hash        = constellation.generate_hash_256()
coverage_dir = os.path.join(RESULTS_DIR, CASE_FOLDER)
coverage_stats = np.loadtxt(os.path.join(coverage_dir, f'coverage_{hash}.csv'), delimiter=',')

# ============================================================================
# COMPUTE MODEL THROUGHPUT VS TIME
# ============================================================================
ed_density = CHOSEN_NODE_COUNT / area_roi_m2
time_vector_model, model_tput_vs_time = get_tput(
    coverage_stats, dc_percentage, n_ch, ed_density,
    time_on_air_seconds, avg_inter_arrival_time, area_roi_m2)

# ============================================================================
# LOAD SIMULATION THROUGHPUT VS TIME
# ============================================================================
simulations_dir = os.path.join(RESULTS_DIR, CASE_FOLDER)
simulation_time_vector, simulation_tput_avg, simulation_tput_std = load_simulations_results(
    simulations_dir, CHOSEN_NODE_COUNT, sample_time_ms, time_on_air_ms, satellite_height_km)

# ============================================================================
# CREATE FIGURE
# ============================================================================
fig, ax = plt.subplots(figsize=(FIGURE_WIDTH, FIGURE_HEIGHT))

# ============================================================================
# PLOT MODEL
# ============================================================================
ax.plot(time_vector_model, model_tput_vs_time,
        color=COLOR_MODEL, linewidth=1.2, linestyle='-',
        label='Model', zorder=3)

# ============================================================================
# PLOT SIMULATION
# ============================================================================
ax.fill_between(simulation_time_vector,
                simulation_tput_avg - simulation_tput_std,
                simulation_tput_avg + simulation_tput_std,
                color=COLOR_SIM, alpha=0.15, zorder=2)
ax.plot(simulation_time_vector, simulation_tput_avg,
        color=COLOR_SIM, linewidth=1.0, linestyle='--',
        marker='o', markersize=3, markerfacecolor='white', markeredgewidth=0.8,
        label='Simulation', zorder=4)

# ============================================================================
# AXIS CONFIGURATION
# ============================================================================
ax.set_xlabel('Time (s)', fontsize=FONT_SIZE_LABELS, weight='normal')
ax.set_ylabel('Throughput', fontsize=FONT_SIZE_LABELS, weight='normal')

if Y_MIN is not None or Y_MAX is not None:
    current_ylim = ax.get_ylim()
    y_min = Y_MIN if Y_MIN is not None else current_ylim[0]
    y_max = Y_MAX if Y_MAX is not None else current_ylim[1]
    ax.set_ylim(y_min, y_max)

ax.margins(x=0)
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
ax.legend(loc='best',
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

plt.savefig(os.path.join(OUTPUT_PATH, f'tput_vs_time_{CHOSEN_NODE_COUNT}ED.pdf'),
            format='pdf', bbox_inches='tight', pad_inches=0.01, dpi=600)
plt.savefig(os.path.join(OUTPUT_PATH, f'tput_vs_time_{CHOSEN_NODE_COUNT}ED.png'),
            format='png', bbox_inches='tight', pad_inches=0.01, dpi=600)

print(f"Figure saved to: {OUTPUT_PATH}tput_vs_time_{CHOSEN_NODE_COUNT}ED.pdf")
print(f"Figure dimensions: {FIGURE_WIDTH} × {FIGURE_HEIGHT} inches")

# ============================================================================
# STATISTICS OUTPUT
# ============================================================================
print(f"\nModel Throughput:")
print(f"  Mean : {np.mean(model_tput_vs_time):.4f}")
print(f"  Std  : {np.std(model_tput_vs_time):.4f}")
print(f"  Min  : {np.min(model_tput_vs_time):.4f}")
print(f"  Max  : {np.max(model_tput_vs_time):.4f}")

print(f"\nSimulation Throughput:")
print(f"  Mean : {np.mean(simulation_tput_avg):.4f}")
print(f"  Std  : {np.mean(simulation_tput_std):.4f}")
print(f"  Min  : {np.min(simulation_tput_avg):.4f}")
print(f"  Max  : {np.max(simulation_tput_avg):.4f}")

print(f"\nNode Count  : {CHOSEN_NODE_COUNT} EDs")
print(f"Time on Air : {time_on_air_ms} ms")
print(f"Sample Time : {sample_time_ms} ms")

plt.show()