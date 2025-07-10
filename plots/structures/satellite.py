class Satellite:
    def __init__(self, timestamp, semi_major_axis, eccentricity, inclination, raan, arg_of_perigee, anomaly):
        self.semi_major_axis = semi_major_axis          # Semi-major axis in meters
        self.eccentricity = eccentricity                # Eccentricity
        self.inclination = inclination                  # Inclination
        self.raan = raan                                # Right Ascension of the Ascending Node (RAAN)
        self.arg_of_perigee = arg_of_perigee            # Argument of perigee
        self.anomaly = anomaly                          # True anomaly
        self.timestamp = timestamp                      # Date associated to the state

    def __repr__(self):
        return (f"Satellite(date={self.timestamp}, semi_major_axis={self.semi_major_axis}, "
                f"eccentricity={self.eccentricity}, inclination={self.inclination}, "
                f"raan={self.raan}, argument_of_perigee={self.arg_of_perigee}, "
                f"anomaly={self.anomaly})")