import matplotlib.pyplot as plt
import os
import pandas as pd

def load_coverage_stats(coverage_dir, hash):
    file_name = os.path.join(coverage_dir, f'coverage_{hash}.csv')
    coverage_stats = pd.read_csv(file_name, header=None).values
    return coverage_stats

coverage_dir = "case3/pure-aloha"
# 8c7cb66b6566312c17e1319c4a71a62285013e87800588d9aa2ed17cf5990595
# "73d575dcc86cf4911d290f7444b308be57c90c8534b86d8eeb86239284f6f043"
hash = "8c7cb66b6566312c17e1319c4a71a62285013e87800588d9aa2ed17cf5990595"
coverage_stats = load_coverage_stats(coverage_dir, hash)


plt.figure()
plt.plot(coverage_stats[:,0]/1000.0,coverage_stats[:,1],label="% of Depl. Region covered")
plt.margins(0)
plt.grid(False)
plt.ylabel('Coverage [%]')
plt.ylim([0, 110])
plt.xlim([200, 1200])
plt.xlabel('Time [s]')
plt.legend()
plt.tight_layout()
plt.savefig(f'coverage_{hash},.pdf', format='pdf', bbox_inches='tight')
plt.show()