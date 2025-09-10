import matplotlib.pyplot as plt
import os
import pandas as pd

def load_coverage_stats(coverage_dir, HASH):
    file_name = os.path.join(coverage_dir, f'coverage_{HASH}.csv')
    coverage_stats = pd.read_csv(file_name, header=None).values
    return coverage_stats

COVERAGE_DIR = "C:/Projects/results/test1/"
HASH = "73d575dcc86cf4911d290f7444b308be57c90c8534b86d8eeb86239284f6f043"
coverage_stats = load_coverage_stats(COVERAGE_DIR, HASH)

plt.figure(figsize=(6, 4))
plt.plot(
    coverage_stats[:, 0] / 1000.0,
    coverage_stats[:, 1],
    label="Coverage of Deployment Region",
    color="C0",
    linewidth=2,
)
plt.margins(0)
plt.grid(True, which='both', linestyle='--', linewidth=0.5, alpha=0.7)
plt.ylabel('Coverage (%)', fontsize=12)
plt.ylim([0, 105])
plt.xlim([0, 1500])
plt.xlabel('Time (s)', fontsize=12)
plt.legend(fontsize=11, loc='lower right', frameon=False)
plt.tight_layout()
plt.savefig(os.path.join(COVERAGE_DIR,f'coverage_{HASH}.pdf'), format='pdf', bbox_inches='tight', dpi=300)
plt.show()