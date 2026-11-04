"""Default file locations shared by the analysis and plotting scripts.

All paths are resolved relative to the repository, so the scripts work regardless of the
current working directory. The results root can be moved with the environment variable
``LORA_RESULTS_DIR`` (e.g. ``LORA_RESULTS_DIR=/data/lora-results python scripts/plot_coverage.py``).
"""
import glob
import os
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPTS_DIR.parent
RESOURCES_DIR = SCRIPTS_DIR / "resources"
INPUTS_DIR = REPO_ROOT / "inputs"
RESULTS_DIR = Path(os.environ.get("LORA_RESULTS_DIR", REPO_ROOT / "results"))


def resolve_repo_path(path):
    """Return ``path`` as given if it exists, otherwise interpret it relative to the repository root.

    Paths stored in ``simulation-parameters.json`` are written exactly as they were passed to the
    Java simulator, i.e. relative to the directory it was launched from (normally the repository root).
    """
    p = Path(path)
    if p.exists() or p.is_absolute():
        return p
    return REPO_ROOT / p


def find_simulation_file(simulations_dir, n_nodes, sample_time_ms, time_on_air_ms):
    """Locate ``<N>ED_<sample>ms_<ToA>ms_<a>km.json`` written by NetworkSimulator.

    The last token is the semi-major axis of the first satellite in km (integer part), which is
    matched with a wildcard so the caller does not need to know it.
    """
    pattern = os.path.join(str(simulations_dir), f"{n_nodes}ED_{sample_time_ms}ms_{time_on_air_ms}ms_*km.json")
    matches = sorted(glob.glob(pattern))
    if len(matches) != 1:
        raise FileNotFoundError(
            f"Expected exactly one simulation file matching {pattern}, found {len(matches)}: {matches}")
    return matches[0]
