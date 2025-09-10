import csv
import os
import numpy as np
import seaborn as sns
import matplotlib.pyplot as plt

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
# satellite_sma = list(map(lambda satellite : (earth_gp / ((2 * np.pi * float(satellite['MEAN_MOTION'])) ** 2)) ** (1/3) , satellite_data))
satellite_sma = list(map(lambda satellite : (earth_gp ** (1/3)) / ( (2 * float(satellite['MEAN_MOTION']) * np.pi / 86400) ** (2/3) ), satellites_data_leo))

# Filter LEO 
# satellite_sma_filtered = list(filter(lambda sma : sma < (earth_radius + 2000000), satellite_sma))

# In approx. height over the Earth's surface in Km:
satellite_heights = list(map(lambda sma : (sma - earth_radius) / 1000, satellite_sma))
    
mean = np.mean(satellite_heights)
median = np.median(satellite_heights)
std_dev = np.std(satellite_heights)

print(mean, median, std_dev)

plt.figure(figsize=(10, 6))
plt.grid("on")
sns.histplot(satellite_heights, kde=True, bins=30)
plt.title('Frequency distribution of active satellites\' approximate height')
plt.xlabel('Height [Km]')
plt.ylabel('Frequency')

# What if we filter out Starlink?
# First let's see the proportion of satellites in LEO

# major_constellations_names = ["STARLINK", "ONEWEB","YAOGAN", "IRIDIUM", "COSMOS", "FLOCK"]
major_constellations_names = ["STARLINK", "ONEWEB", "YAOGAN"]
major_constellations_stats = []

for constellation_name in major_constellations_names:
    major_constellations_stats.append(sum(1 for satellite in satellite_data if constellation_name in satellite['OBJECT_NAME']))
    
total_minus_constellations = sum(major_constellations_stats)
major_constellations_stats.append(total_minus_constellations)
major_constellations_names.append("Other LEO satellites")

def func(pct, allvals):
    absolute = int(np.round(pct/100.*np.sum(allvals)))
    return f"{pct:.1f}%\n({absolute:d} g)"

fig, ax = plt.subplots(figsize=(10, 6), subplot_kw=dict(aspect="equal"))
wedges = ax.pie(major_constellations_stats, labels=major_constellations_names, autopct='%1.1f%%',  
                textprops={'fontsize': 15, 'color': '#000000', 'backgroundcolor': '#FFFFFFAA'}, pctdistance=0.8, startangle=140)
#plt.tight_layout()
plt.axis('equal')

# Let's take out Starlink, oneweb and Yaogan:
# constellation_filtered = list(filter(lambda satellite: satellite['OBJECT_NAME'] in major_constellations_names, satellite_data))
satellites_data_leo = list(filter(lambda satellite : not any(substring in satellite['OBJECT_NAME'] for substring in major_constellations_names), satellites_data_leo))

print(len(satellites_data_leo))

# Extract the semi major axis of the filtered list
satellite_sma = list(map(lambda satellite : (earth_gp ** (1/3)) / ( (2 * float(satellite['MEAN_MOTION']) * np.pi / 86400) ** (2/3) ), satellites_data_leo))

# Filter LEO sats
satellite_sma_filtered = list(filter(lambda sma : sma < (earth_radius + 2000000), satellite_sma))

# In approx. height over the Earth's surface in Km:
satellite_heights = list(map(lambda sma : (sma - earth_radius) / 1000, satellite_sma_filtered))

plt.figure(figsize=(10, 6))
plt.grid("on")
sns.histplot(satellite_heights, kde=True, bins=40)
plt.title('Frequency distribution of active satellites\' approximate height w/o Starlink, OneWeb & Yaogan')
plt.xlabel('Height [Km]')
plt.ylabel('Frequency')
# Add Van Allen inner Belt threshold
line_x = 1000
plt.axvline(x=line_x, color='r', linestyle='--', linewidth=2, label='Horizontal Line')
plt.text(y=300, x=line_x+100, s='Van Allen\'s inner belt \napprox. height', color='r', fontsize=12, verticalalignment='bottom')

plt.figure(figsize=(10, 6))
plt.grid("on")
sns.ecdfplot(satellite_heights)
plt.title('CDF of active satellites\' approximate height w/o Starlink, OneWeb & Yaogan')
plt.xlabel('Height [Km]')
plt.ylabel('Frequency')

# inner van allen radiation belt from 1.3 to 3 RE https://doi.org/10.1029/2010JA016376

plt.show()