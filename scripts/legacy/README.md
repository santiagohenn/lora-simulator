# Legacy scripts (not maintained)

These scripts are earlier iterations of the analysis and are kept only for traceability.
They are **not** needed to reproduce any result, contain hard-coded paths to the author's
machine and some no longer run against the current code:

| Script | Status |
|---|---|
| `optimization.py`, `optimization_2.py` | Earlier versions of `../plot_avg_sim_vs_model.py` (pure ALOHA only). Not related to the Java optimisers despite the name. |
| `analysis_inc.py` | Throughput vs. inclination sweep from an older coverage tool (`d3co_analysis`). Broken: assigns the tuple returned by `get_tput` into an array column. |
| `aaps_plotter.py`, `worldmap_snapshots_aaps.py` | Early access-area polygon maps, superseded by `../plot_snapshot.py`. |

To run one anyway, execute it from the `scripts/` folder with `PYTHONPATH=.`, and edit its paths first.
