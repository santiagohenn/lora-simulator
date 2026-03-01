#!/usr/bin/env python3
"""
Spherical-geometry coverage for a satellite over a spherical Earth.

Given:
- Earth radius Re
- satellite altitude h (orbital radius r = Re + h)
- minimum elevation mask e_min (>= 0 deg)

Computes:
1) λ  : "Earth-surface half-cone" (central angle at Earth's center) from SSP to edge of coverage
2) θ  : "sensor half-cone" at the satellite (nadir-pointing cone half-angle) that just contains
        all visible ground points (at elevation = e_min)

Also outputs:
- slant range to edge [km]
- ground arc range Re*λ [km]
- spherical-cap area [km^2]

Notes:
- This is purely geometric, no refraction, Earth treated as a sphere.
- For e_min = 0 deg, λ is the horizon-limited coverage, and θ is the nadir-to-horizon angle as seen from the sat.
"""

import math
import argparse


def lambda_from_elevation_mask(Re_km: float, h_km: float, e_min_deg: float) -> float:
    """
    Returns λ in radians for elevation mask e_min.

    Closed-form:
        cos(λ) = (Re*cos^2(e) + sin(e)*sqrt(r^2 - Re^2*cos^2(e))) / r
    where r = Re + h.
    """
    if Re_km <= 0:
        raise ValueError("Earth radius must be positive.")
    if h_km < 0:
        raise ValueError("Altitude must be >= 0.")
    if not (0.0 <= e_min_deg < 90.0):
        raise ValueError("e_min_deg must be in [0, 90).")

    r_km = Re_km + h_km
    e = math.radians(e_min_deg)

    cos_e = math.cos(e)
    sin_e = math.sin(e)

    inside = r_km * r_km - (Re_km * Re_km) * (cos_e * cos_e)
    inside = max(0.0, inside)  # numerical guard

    cos_lambda = (Re_km * (cos_e * cos_e) + sin_e * math.sqrt(inside)) / r_km
    cos_lambda = max(-1.0, min(1.0, cos_lambda))

    return math.acos(cos_lambda)


def slant_range_to_edge(Re_km: float, h_km: float, lam_rad: float) -> float:
    """
    Slant range d = |SG| from satellite to edge point on Earth.
    Triangle O-S-G with OS=r, OG=Re, angle at O is λ:
        d^2 = r^2 + Re^2 - 2 r Re cos(λ)
    """
    r_km = Re_km + h_km
    return math.sqrt(max(0.0, r_km * r_km + Re_km * Re_km - 2.0 * r_km * Re_km * math.cos(lam_rad)))


def sensor_half_cone_from_lambda(Re_km: float, h_km: float, lam_rad: float) -> float:
    """
    Returns θ in radians: sensor half-angle at the satellite relative to nadir (toward Earth center).

    Using law of sines in triangle O-S-G:
        Re / sin(θ) = d / sin(λ)  =>  sin(θ) = Re * sin(λ) / d
    where d is the slant range satellite->edge.
    """
    d = slant_range_to_edge(Re_km, h_km, lam_rad)
    if d == 0.0:
        return 0.0

    sin_theta = (Re_km * math.sin(lam_rad)) / d
    sin_theta = max(-1.0, min(1.0, sin_theta))
    return math.asin(sin_theta)


def spherical_cap_area(Re_km: float, lam_rad: float) -> float:
    """Area of spherical cap with half-angle λ: A = 2π Re^2 (1 - cos λ)."""
    return 2.0 * math.pi * Re_km * Re_km * (1.0 - math.cos(lam_rad))


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--alt-km", type=float, required=True, help="Satellite altitude above surface [km]")
    p.add_argument("--elev-deg", type=float, default=0.0, help="Minimum elevation mask [deg], default 0")
    p.add_argument("--Re-km", type=float, default=6378.137, help="Earth radius [km], default WGS84 equatorial")
    args = p.parse_args()

    Re = args.Re_km
    h = args.alt_km
    e_min = args.elev_deg
    r = Re + h

    lam = lambda_from_elevation_mask(Re, h, e_min)
    theta = sensor_half_cone_from_lambda(Re, h, lam)

    lam_deg = math.degrees(lam)
    theta_deg = math.degrees(theta)

    d = slant_range_to_edge(Re, h, lam)
    ground_arc_km = Re * lam
    area_km2 = spherical_cap_area(Re, lam)

    print("Inputs:")
    print(f"  Re        = {Re:.6f} km")
    print(f"  altitude  = {h:.6f} km  (r = {r:.6f} km)")
    print(f"  elev mask = {e_min:.6f} deg")
    print()
    print("Coverage geometry (spherical Earth):")
    print(f"  Earth-surface half-angle  λ = {lam:.12f} rad  = {lam_deg:.9f} deg")
    print(f"  Sensor (nadir) half-angle θ = {theta:.12f} rad  = {theta_deg:.9f} deg")
    print()
    print("Ranges / area:")
    print(f"  slant range to edge       d = {d:.6f} km")
    print(f"  ground arc range       Re*λ = {ground_arc_km:.6f} km")
    print(f"  spherical-cap area         A = {area_km2:.3f} km^2")


if __name__ == "__main__":
    main()