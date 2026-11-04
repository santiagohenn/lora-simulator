# Local inputs (not versioned)

Everything in this folder except this file is ignored by git. The programs expect:

| Path | Used by | Content |
|---|---|---|
| `inputs/satellites.csv` | `Main`, `LoraConstellationSimulator` (via `-s`) | Constellation of the throughput experiment |
| `inputs/satellites/constellation_baseline.csv` | `AreaOptimization`, and `config/optimization_argentina_eez.properties` (`satellites_file`) | Baseline constellation for the constellation-design studies |
| `inputs/rois/argentina_eez.csv` | `AccessVsRAANAndPhase`, `AreaOptimization`, `DEMain`, `SAOptimization`, `scripts/plot_snapshot.py` | ROI polygon (Argentine exclusive economic zone) |
| `inputs/rois/greenland.csv` | `scripts/plot_snapshot_polar.py` | ROI polygon |

**Satellites CSV** (no header, one satellite per line, read by `satellite-tools`):

```
<epoch ISO 8601 UTC>,<semi-major axis [m]>,<eccentricity>,<inclination [deg]>,<RAAN [deg]>,<argument of perigee [deg]>,<true anomaly [deg]>
2025-01-01T16:00:00.000,6921000,0,97.6,284.381,0,282.262
```

`examples/satellites_example.csv` is a valid example (three satellites that pass over the default ROI
between 16:00 and 16:30 UTC on 2025-01-01): `cp examples/satellites_example.csv inputs/satellites.csv`.

**ROI polygon CSV**: one `latitude,longitude` pair per line in degrees (an optional third column is
read as height by `scripts/structures/geotools.py`); non-self-intersecting, clockwise-ordered
(constellation-toolkit requirement).

The files used for the publication are not part of the repository; see REPOSITORY_REVIEW.md.
