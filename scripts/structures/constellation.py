import hashlib
import math
import numpy as np
import csv
from structures.satellite import Satellite

class Constellation:

    prime = 26141
    
    def __init__(self):
        self.satellites = []

    def add_satellite(self, satellite):
        self.satellites.append(satellite)

    def truncate_to_three_decimals(self, value: float) -> float:
        return math.floor(value * 1000) / 1000.0

    def read_from_csv(self, file_path):
        with open(file_path, mode='r') as file:
            csv_reader = csv.reader(file)
            for row in csv_reader:
                if row[0].startswith("#"):
                    continue
                # CSV columns are in the order a,e,i,W,w,v
                timestamp = row[0]
                semi_major_axis = float(row[1])
                eccentricity = float(row[2])
                inclination = float(row[3])
                raan = float(row[4])
                arg_of_perigee = float(row[5])
                anomaly = float(row[6])

                satellite = Satellite(timestamp, semi_major_axis, eccentricity, inclination, raan, arg_of_perigee, anomaly)
                
                # Add the satellite to the constellation
                self.add_satellite(satellite)
    
    def generate_hash_256(self) -> str:
        elements_concat = ["","","","","",""]
        for satellite in self.satellites:
            elements_concat[0] += str(int(satellite.semi_major_axis))
            elements_concat[1] += str(int(satellite.eccentricity * 1000))
            elements_concat[2] += str(int(satellite.inclination * 1000))
            elements_concat[3] += str(int(satellite.raan * 1000))
            elements_concat[4] += str(int(satellite.arg_of_perigee * 1000))
            elements_concat[5] += str(int(satellite.anomaly * 1000))
        list.sort(elements_concat)
        string_to_hash = "".join(map(str, elements_concat)).strip()
        return self.sha256(string_to_hash)

    def sha256(self, input_string: str) -> str:
        return hashlib.sha256(input_string.encode('utf-8')).hexdigest()
    
