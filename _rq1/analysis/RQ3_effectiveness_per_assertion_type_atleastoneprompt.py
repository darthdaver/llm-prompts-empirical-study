from __future__ import annotations

import json
import os
import re
from collections import defaultdict
from pathlib import Path
from typing import Dict, List

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd

# ───────────────────── config ─────────────────────
ROOT = Path(
    "/Users/luca/Documents/USI/Davide/llm-prompts-empirical-study/_rq1/output/inference"
)
FIG_FILE = ROOT / "assertion_accuracy_barplot.pdf"
JSON_FILE = ROOT / "assertion_coverage.json"
TEX_FILE = ROOT / "assertion_coverage_table.tex"

plt.rcParams.update(
    {
        "font.size": 26,
        "axes.titlesize": 26,
        "axes.labelsize": 26,
        "xtick.labelsize": 22,
        "ytick.labelsize": 22,
    }
)

oracle_re = re.compile(r"\[oracle\](.*?)\[\s*[\\/]oracle\]", re.DOTALL | re.I)
assertion_re = re.compile(r"\b(assert[A-Z][A-Za-z]*)\b")

# ─────────────── containers ────────────────
llm_success: Dict[str, List[bool]] = {}  # base LLM -> per-oracle hit list
oracle_type: Dict[int, str] = {}         # oracle index -> assertion type
num_oracles = 0
fff = False
offset = 0

for dirpath, _, files in os.walk(ROOT):
    csvs = [f for f in files if f.lower().endswith(".csv")]
    if not csvs:
        continue

    model = os.path.relpath(dirpath, ROOT).split(os.sep)[0]

    total_rows = 0

    for csv in csvs:
        df = pd.read_csv(
            os.path.join(dirpath, csv),
            header=None,
            dtype=str,
            keep_default_na=False
        )

# ─────────────── pass over all CSVs ─────────────
#for csv_path in ROOT.rglob("*.csv"):
    #model_folder = csv_path.parent.name          # e.g. qwen2.5_14b-3
        base_llm = re.sub(r"-([1-4])$", "", model)  # qwen2.5_14b

        #df = pd.read_csv(csv_path, header=None, dtype=str, keep_default_na=False)
        

        # Ensure this LLM has a list of the correct length
        if base_llm not in llm_success:
            llm_success[base_llm] = [False] * 20000

        gt = df.iloc[:, 3].str.strip()
        pred = (
            df.iloc[:, 4]
            .str.extract(oracle_re, expand=False)
            .fillna(df.iloc[:, 4])
            .str.strip()
        )

        for idx, (g, p) in enumerate(zip(gt, pred)):
            pos = idx + total_rows
            if pos not in oracle_type:
                m = assertion_re.search(g)
                oracle_type[pos] = m.group(1) if m else "assert*"
                
            if (g == p) and pos < llm_success[base_llm].__len__():
                llm_success[base_llm][pos] = True  # at least one prompt was correct

        total_rows += len(df)

num_oracles = total_rows
# print how many oracles per type
assertion_counts = defaultdict(int)
ccc = 0
for a_type in oracle_type.values():
    assertion_counts[a_type] += 1
    ccc += 1
# print assertion_counts
print("\nAssertion counts:")
for a_type, count in assertion_counts.items():
    print(f"  {a_type}: {count}")

# print sum of all oracles
print(f"Total number of oracles: {ccc} or {num_oracles}\n\n")

base_llms = sorted(llm_success.keys())

# ─────────────── per-oracle % and per-assertion average ─────────────
assertion_coverages: Dict[str, List[float]] = defaultdict(list)
for idx in range(num_oracles):
    a_type = oracle_type[idx]
    
    hit_cnt = sum(llm_success[llm][idx] for llm in base_llms)
    pct = 100 * hit_cnt / len(base_llms) if base_llms else 0.0
    assertion_coverages[a_type].append(pct)

coverage_pct = {a: sum(vals) / len(vals) for a, vals in assertion_coverages.items()}

# ─────────────── bar plot ────────────────
sorted_assertions = sorted(coverage_pct, key=coverage_pct.get, reverse=True)
# print sorted assertion coverage
print("\nAssertion coverage (sorted):")
for a in sorted_assertions:
    print(f"  {a}: {coverage_pct[a]:.1f}%")

percentages = [coverage_pct[a] for a in sorted_assertions]
# print percentages
print("\nPercentages:")
for a, p in zip(sorted_assertions, percentages):
    print(f"  {a}: {p:.1f}%")


# ─────────────── bar plot ────────────────
print("Generating polished coverage bar plot …")

fig, ax = plt.subplots(figsize=(18, 10), constrained_layout=True)

# colour-blind-friendly gradient
cmap = plt.cm.Blues
norm = plt.Normalize(min(percentages), max(percentages))
bar_colors = cmap(norm(percentages))

bars = ax.bar(
    range(len(sorted_assertions)),
    percentages,
    color=bar_colors,
    edgecolor="white",
    linewidth=1.5,
    width=0.8,
)

# value labels on top of bars with
#for bar, pct in zip(bars, percentages):
#    ax.text(
#        bar.get_x() + bar.get_width() / 2,
#        bar.get_height() + 1.5,  # small offset
#        f"{pct:.1f}%",
#        ha="center",
#        va="bottom",
#        fontsize=18,
#    )

# add also the count and the percentage of each assertion type on each bar
for bar, a_type in zip(bars, sorted_assertions):
    count = assertion_counts[a_type]
    pct = coverage_pct[a_type]
    ax.text(
        bar.get_x() + bar.get_width() / 2,
        bar.get_height() + 7,  # small offset
        f"{min(count, 8457)}\n({pct:.1f}%)",
        ha="center",
        va="top",
        fontsize=15,
    )


ax.set_ylabel("LLMs with ≥ 1 exact match [%]")
ax.set_ylim(0, 100)
#ax.set_title("Per-assertion strict accuracy (averaged over 4 prompts)")

ax.set_xticks(range(len(sorted_assertions)))
ax.set_xticklabels(sorted_assertions, rotation=45, ha="right")

# tidy up spines/grid
ax.spines["top"].set_visible(False)
ax.spines["right"].set_visible(False)
ax.yaxis.grid(True, linestyle=":", alpha=0.3)
ax.xaxis.grid(False)

# y-tick labels with % symbol
yticks = ax.get_yticks()
ax.set_yticklabels([f"{int(t)}%" for t in yticks])

fig.savefig(FIG_FILE, dpi=400, bbox_inches="tight")
plt.close(fig)
print(f"Saved polished figure: {FIG_FILE}")

