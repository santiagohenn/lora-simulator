import csv
import os
import numpy as np
import seaborn as sns
import matplotlib.pyplot as plt

# Set font for academic publication
plt.rcParams['font.family'] = 'serif'
plt.rcParams['font.size'] = 11
plt.rcParams['axes.labelsize'] = 12
plt.rcParams['axes.titlesize'] = 14
plt.rcParams['xtick.labelsize'] = 10
plt.rcParams['ytick.labelsize'] = 10
plt.rcParams['legend.fontsize'] = 11

parent_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'resources')
earth_gp = 3.986004418E14
earth_radius = 6371000

def read_csv(file_path):
    data = []
    with open(file_path, newline='') as csvfile:
        csvreader = csv.DictReader(csvfile)
        for row in csvreader:
            data.append(row)
    return data

file_path = os.path.join(parent_dir, 'active_satellites.csv') # Celestrak data obtained on 16/08/2024
print("reading", file_path)

satellite_data = read_csv(file_path)
satellites_data_leo = list(filter(lambda satellite : (earth_gp ** (1/3)) / ( (2 * float(satellite['MEAN_MOTION']) * np.pi / 86400) ** (2/3)) < (earth_radius + 2000000), satellite_data))

# Get each satellite's sma
satellite_sma = list(map(lambda satellite : (earth_gp ** (1/3)) / ( (2 * float(satellite['MEAN_MOTION']) * np.pi / 86400) ** (2/3) ), satellites_data_leo))

# In approx. height over the Earth's surface in Km:
satellite_heights = list(map(lambda sma : (sma - earth_radius) / 1000, satellite_sma))
    
mean = np.mean(satellite_heights)
median = np.median(satellite_heights)
std_dev = np.std(satellite_heights)

print(mean, median, std_dev)

# First histogram - all LEO satellites
plt.figure(figsize=(8, 5))
plt.grid(True, alpha=0.3)
sns.histplot(satellite_heights, kde=True, bins=30, color='steelblue', alpha=0.7)
plt.title('Distribución de frecuencias de la altura aproximada\nde satélites activos en LEO', fontweight='bold')
plt.xlabel('Altura [km]', fontweight='bold')
plt.ylabel('Frecuencia', fontweight='bold')
plt.tight_layout()

# Pie chart for major constellations
major_constellations_names = ["STARLINK", "ONEWEB", "YAOGAN"]
major_constellations_stats = []

for constellation_name in major_constellations_names:
    major_constellations_stats.append(sum(1 for satellite in satellite_data if constellation_name in satellite['OBJECT_NAME']))
    
total_minus_constellations = len(satellites_data_leo) - sum(major_constellations_stats)
major_constellations_stats.append(total_minus_constellations)

# Translate constellation names to Spanish
spanish_constellation_names = ["STARLINK", "OneWeb", "Yaogan", "Otros satélites LEO"]

fig, ax = plt.subplots(figsize=(8, 6), subplot_kw=dict(aspect="equal"))
colors = ['#ff9999', '#66b3ff', '#99ff99', '#ffcc99']
wedges, texts, autotexts = ax.pie(major_constellations_stats, labels=spanish_constellation_names, 
                                  autopct='%1.1f%%', colors=colors,
                                  textprops={'fontsize': 11, 'fontweight': 'bold'}, 
                                  pctdistance=0.8, startangle=140)

for autotext in autotexts:
    autotext.set_color('black')
    autotext.set_fontweight('bold')

plt.title('Distribución de satélites LEO activos por constelación', 
          fontweight='bold', fontsize=14, pad=20)
plt.tight_layout()

# Filter out major constellations
satellites_data_leo_filtered = list(filter(lambda satellite : not any(substring in satellite['OBJECT_NAME'] for substring in major_constellations_names[:-1]), satellites_data_leo))

print(f"Satélites filtrados: {len(satellites_data_leo_filtered)}")

# Extract the semi major axis of the filtered list
satellite_sma_filtered = list(map(lambda satellite : (earth_gp ** (1/3)) / ( (2 * float(satellite['MEAN_MOTION']) * np.pi / 86400) ** (2/3) ), satellites_data_leo_filtered))

# Filter LEO sats
satellite_sma_filtered = list(filter(lambda sma : sma < (earth_radius + 2000000), satellite_sma_filtered))

# In approx. height over the Earth's surface in Km:
satellite_heights_filtered = list(map(lambda sma : (sma - earth_radius) / 1000, satellite_sma_filtered))

# Second histogram - filtered satellites
plt.figure(figsize=(8, 5))
plt.grid(True, alpha=0.3)
sns.histplot(satellite_heights_filtered, kde=True, bins=40, color='darkgreen', alpha=0.7)
plt.title('Distribución de frecuencias de la altura aproximada\nde satélites activos sin Starlink, OneWeb y Yaogan', 
          fontweight='bold')
plt.xlabel('Altura [km]', fontweight='bold')
plt.ylabel('Frecuencia', fontweight='bold')

# Add Van Allen inner Belt threshold
line_x = 1000
plt.axvline(x=line_x, color='red', linestyle='--', linewidth=2)
plt.text(x=line_x+50, y=plt.ylim()[1]*0.7, 
         s='Cinturón interior\nde Van Allen\n(≈1000 km)', 
         color='red', fontsize=10, fontweight='bold',
         verticalalignment='center',
         bbox=dict(boxstyle="round,pad=0.3", facecolor="white", alpha=0.8))
plt.tight_layout()

# CDF plot
plt.figure(figsize=(8, 5))
plt.grid(True, alpha=0.3)
sns.ecdfplot(satellite_heights_filtered, color='purple', linewidth=2)
plt.title('Función de distribución acumulada de la altura aproximada\nde satélites activos sin Starlink, OneWeb y Yaogan', 
          fontweight='bold')
plt.xlabel('Altura [km]', fontweight='bold')
plt.ylabel('Probabilidad acumulada', fontweight='bold')
plt.tight_layout()

plt.show()