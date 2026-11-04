"""Analytical throughput model for LoRa direct-to-satellite uplinks over a LEO constellation.

The ROI coverage time series produced by constellation-toolkit (``coverage_<hash>.csv``) is combined
with a pure-ALOHA/duty-cycle traffic model to compute, at every time step, the expected normalised
throughput S (successful frames per time-on-air unit, summed over the access areas).

Common arguments
----------------
coverage_stats : ndarray, shape (T, 1 + K)
    Column 0: time [ms]; column s (1..K): coverage of the ROI [%] for the s-th coverage column of
    constellation-toolkit (K = ``max_subset_size``).
dc_percentage : float
    Duty cycle [%]. The silent time after a frame is modelled as ``(100 - dc_percentage) * time_on_air``.
n_ch : int
    Number of channels.
ed_density : float
    End-device density [devices / m^2] (number of devices / ``area_roi_m2``).
time_on_air : float
    Frame time on air [s].
avg_inter_arrival_time : float
    Mean frame inter-arrival time per device [s]. The normalised offered load is
    ``G = time_on_air / avg_inter_arrival_time``.
area_roi_m2 : float
    ROI surface [m^2] (``roi_surface`` in simulation-parameters.json).

The same conventions (G = ToA / IAT, silent time = (100 - DC) * ToA) are used by the Java simulator.
"""
import numpy as np
import pandas as pd
import math
import matplotlib.pyplot as plt
import os

parent_dir = os.path.join(os.path.dirname(__file__), '..')

def get_tput(coverage_stats, dc_percentage, n_ch, ed_density, time_on_air, avg_inter_arrival_time, area_roi_m2):
    """Pure ALOHA with duty-cycle restriction. Returns ``(time_s, throughput)``, both of length T."""

    if ed_density == 0:
        return coverage_stats[:, 0] / 1000.0, np.zeros(coverage_stats.shape[0])
    
    n_of_sats = coverage_stats.shape[1] - 1                         # Number of satellites (according to "max_subset_size" property)
    time_steps = coverage_stats.shape[0]                            # Number of time steps
    # time_step = coverage_stats[1, 0] - coverage_stats[0, 0]         # Timestep in ms
    time_vector_model = coverage_stats[:, 0] / 1000.0               # Time vector in seconds

    dc_backoff_time = (100 - dc_percentage) * time_on_air           # DC backoff time [seconds]
    epsilon = (time_on_air + dc_backoff_time) / time_on_air         # Frame duration - (Transmission time units + silent time units)
    avg_frame_generation_rate = time_on_air / avg_inter_arrival_time

    # (g)
    avg_transmission_rate = avg_frame_generation_rate / (1 + avg_frame_generation_rate * epsilon)  # Average transmission rate (g)

    # (q)
    prob_of_no_interference = 1 - (1 / n_ch) * (
        (avg_frame_generation_rate * min(epsilon, 2) + 1 - np.exp(avg_frame_generation_rate * min(epsilon - 2, 0)))
        / (1 + avg_frame_generation_rate * epsilon)
    )                                                               # Probability of no interference on the same channel

    tput_model = np.zeros(time_steps)       # Throughput stats for the model

    # At each timestep
    for ts in range(time_steps):
        S_aap = 0

        for s in range(n_of_sats):
            aap_area = coverage_stats[ts, s + 1] / 100 * area_roi_m2
            Q_u = np.exp(- (1 - prob_of_no_interference) * ed_density * aap_area)
            S_theta = avg_transmission_rate * ed_density * aap_area * Q_u
            S_aap += S_theta

        tput_model[ts] = S_aap

    return time_vector_model, tput_model

def get_tput_wca(coverage_stats, dc_percentage, n_ch, ed_density, time_on_air, avg_inter_arrival_time, area_roi_m2):
    """Coverage-aware back-off, bounded below by the duty-cycle silent time. Returns the throughput (length T)."""

    if ed_density == 0:
        return np.zeros(coverage_stats.shape[0])

    n_of_sats = coverage_stats.shape[1] - 1                         # Number of satellites (according to "max_subset_size" property)
    time_steps = coverage_stats.shape[0]                            # Number of time steps

    dc_backoff_time = (100 - dc_percentage) * time_on_air           # DC backoff time [seconds]
    epsilon = (time_on_air + dc_backoff_time) / time_on_air         # Frame duration - (Transmission time units + silent time units)
    
    avg_frame_generation_rate = time_on_air / avg_inter_arrival_time

    # (q)
    prob_of_no_interference = 1 - (1 / n_ch) * (
        (avg_frame_generation_rate * min(epsilon, 2) + 1 - np.exp(avg_frame_generation_rate * min(epsilon - 2, 0)))
        / (1 + avg_frame_generation_rate * epsilon)
    )                                                               # Probability of no interference on the same channel

    tput_model = np.zeros(time_steps)       # Throughput stats for the model
    
    dc_bot_min = (100 - dc_percentage) * time_on_air
    eps_min = 1 + dc_bot_min / time_on_air

    # At each timestep
    for ts in range(time_steps):
        S_aap = 0

        for s in range(n_of_sats):
            aap_area = coverage_stats[ts, s + 1] / 100 * area_roi_m2
            
            # (g)
            avg_transmission_rate = n_ch / (2 * aap_area * ed_density) # Average transmission rate (g)

            dc_backoff_time = (1 / avg_transmission_rate - 1 / avg_frame_generation_rate - 1) * time_on_air
            avg_transmision_rate_max = 1 / (1 / avg_frame_generation_rate + eps_min)
            avg_transmission_rate = min(avg_transmission_rate, avg_transmision_rate_max)
            dc_backoff_time = max(dc_backoff_time, (100 - dc_percentage) * time_on_air)
            
            # (q)
            prob_of_no_interference =  1 - (2/n_ch) * avg_transmission_rate
            
            Q_u = np.exp(- (1 - prob_of_no_interference) * ed_density * aap_area)
            S_theta = avg_transmission_rate * ed_density * aap_area * Q_u
            S_aap += S_theta

        tput_model[ts] = S_aap

    return tput_model

def get_tput_wca_nr(coverage_stats, dc_percentage, n_ch, ed_density, time_on_air, avg_inter_arrival_time, area_roi_m2):
    """Coverage-aware back-off without duty-cycle lower bound ("no restriction"). Returns the throughput (length T)."""

    if ed_density == 0:
        return np.zeros(coverage_stats.shape[0])

    n_of_sats = coverage_stats.shape[1] - 1                         # Number of satellites (according to "max_subset_size" property)
    time_steps = coverage_stats.shape[0]                            # Number of time steps

    avg_frame_generation_rate = time_on_air / avg_inter_arrival_time                                                         # Probability of no interference on the same channel

    tput_model = np.zeros(time_steps)       # Throughput stats for the model

    # At each timestep
    for ts in range(time_steps):
        S_aap = 0

        for s in range(n_of_sats):
            aap_area = coverage_stats[ts, s + 1] / 100 * area_roi_m2
            
            if aap_area <= 0 or ed_density <= 0:
                continue
            
            # (g)
            avg_transmission_rate = n_ch / (2 * aap_area * ed_density) # Average transmission rate (g)

            dc_backoff_time = (1 / avg_transmission_rate - 1 / avg_frame_generation_rate - 1) * time_on_air
            epsilon = 1 + dc_backoff_time / time_on_air
            
            if epsilon >= 2:
                # (q)
                prob_of_no_interference =  1 - (2/n_ch) * avg_transmission_rate
            else:
                prob_of_no_interference =  1 - 1/n_ch * (avg_frame_generation_rate * epsilon + 1 - math.exp(avg_frame_generation_rate * (epsilon - 2))) / (1 + avg_frame_generation_rate * epsilon)
            
            Q_u = np.exp(- (1 - prob_of_no_interference) * ed_density * aap_area)
            S_theta = avg_transmission_rate * ed_density * aap_area * Q_u
            S_aap += S_theta

        tput_model[ts] = S_aap

    return tput_model

def plot_coverage(csv_path):

    plt.figure()
    data = pd.read_csv(csv_path, header=None)
    x_values = data.iloc[:, 0]
    y_values = data.iloc[:, 1:]
    
    for col in y_values.columns:
        plt.plot(x_values / 1000.0, y_values[col], label=f'Series {col}')

    plt.grid(True)
    plt.xlabel('Time [s]')
    plt.ylabel('Coverage [%]')
    plt.legend()


def plot_coverage_list(folder, hash_list):

    plt.figure()

    for hash in hash_list:
        file_name = os.path.join(parent_dir, 'output', folder, f'coverage_{hash}.csv')
        data = pd.read_csv(file_name, header=None)
        x_values = data.iloc[:, 0]
        y_values = data.iloc[:, 1:]
        
        for col in y_values.columns:
            plt.plot(x_values / 1000.0, y_values[col], label=f'Series {col}')

        plt.grid(True)
        plt.xlabel('Time [s]')
        plt.ylabel('Coverage [%]')
        plt.legend()