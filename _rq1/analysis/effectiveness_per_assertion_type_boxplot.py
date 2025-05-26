from __future__ import annotations

import json
import os
import re
from collections import defaultdict
from typing import List

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd

# ───────────────────────────── Config ───────────────────────────────
ROOT = "/Users/luca/Documents/USI/Davide/llm-prompts-empirical-study/_rq1/output/inference"
STATS_FILE = os.path.join(ROOT, "inference_stats.json")
ASSERTION_STATS_FILE = os.path.join(ROOT, "inference_assertion_stats.json")
PLOT_TEMPLATE = os.path.join(ROOT, "inference_assertion_success_N{}.pdf")
BOX_PLOT_FILE = os.path.join(ROOT, "assertion_accuracy_boxplot.pdf")
TEX_FILE = os.path.join(ROOT, "assertion_accuracy_table.tex")

# Very large fonts for print readability
plt.rcParams.update({
    "font.size": 26,
    "axes.titlesize": 38,
    "axes.labelsize": 26,
    "xtick.labelsize": 26,
    "ytick.labelsize": 10,
    "legend.fontsize": 20,
    "figure.titlesize": 38,
    "font.family": "serif",
    "font.serif": ["Times New Roman"],
})

oracle_re = re.compile(r"\[oracle\](.*?)\[\s*[\\/]oracle\]", re.DOTALL | re.I)
assertion_re = re.compile(r"\b(assert[A-Z][A-Za-z]*)\b")

# ────────────────────── Collect statistics ──────────────────────────
stats: dict[str, dict[str, int]] = defaultdict(lambda: {"strict": 0, "flex": 0, "total": 0})
assertion_stats: dict[str, dict[str, dict[str, int]]] = defaultdict(
    lambda: defaultdict(lambda: {"strict": 0, "flex": 0, "total": 0})
)

for dirpath, _, files in os.walk(ROOT):
    csvs = [f for f in files if f.lower().endswith(".csv")]
    if not csvs:
        continue
    model = os.path.relpath(dirpath, ROOT).split(os.sep)[0]

    for csv_file in csvs:
        df = pd.read_csv(os.path.join(dirpath, csv_file), header=None, dtype=str, keep_default_na=False)
        gt = df.iloc[:, 3].str.strip()
        pred = (
            df.iloc[:, 4]
            .str.extract(oracle_re, expand=False)
            .fillna(df.iloc[:, 4])
            .str.strip()
        )

        for g, p in zip(gt, pred):
            strict = (g == p) or (g == p + ";")
            flex = strict or (g in p) or (p in g)

            stats[model]["strict"] += strict
            stats[model]["flex"] += flex
            stats[model]["total"] += 1

            assertion = assertion_re.search(g).group(1) if assertion_re.search(g) else "Other"
            a = assertion_stats[model][assertion]
            a["strict"] += strict
            a["flex"] += flex
            a["total"] += 1


# for each model, for each assertion type, print on screen the percetage of strict only
for a in assertion_stats:
    for m in assertion_stats[a]:
        if assertion_stats[a][m]["total"]:
            print(f"{a} {m}: {100 * assertion_stats[a][m]['strict'] / assertion_stats[a][m]['total']:.1f}%")

# ─────────────────────── Derived helpers ────────────────────────────

def strict_pct(model: str, assertion: str) -> float:
    d = assertion_stats[model].get(assertion, {"strict": 0, "total": 0})
    return 100 * d["strict"] / d["total"] if d["total"] else 0.0

# Global mean strict accuracy across *all* assertions and models
all_pcts: List[float] = []
for m in assertion_stats:
    for a in assertion_stats[m]:
        if assertion_stats[m][a]["total"]:
            all_pcts.append(strict_pct(m, a))
GLOBAL_MEAN = sum(all_pcts) / len(all_pcts) if all_pcts else 0.0

# Mean per assertion (for ordering)
assertion_means = {
    a: (sum(strict_pct(m, a) for m in assertion_stats if assertion_stats[m][a]["total"]) /
        sum(1 for m in assertion_stats if assertion_stats[m][a]["total"]))
    for a in {a for m in assertion_stats for a in assertion_stats[m]}
}

all_assertions: List[str] = sorted(assertion_means, key=assertion_means.get, reverse=True)
all_models: List[str] = sorted(assertion_stats)

# ────────────────────── Plotting utilities ──────────────────────────

def add_global_mean(ax):
    ax.axhline(GLOBAL_MEAN, color="red", linestyle="--", linewidth=2, label=f"Mean {GLOBAL_MEAN:.1f}%")


def grouped_accuracy_bars(ax, models: List[str]):
    if not models:
        ax.set_visible(False)
        return
    bw = 0.8 / len(models)
    x = np.arange(len(all_assertions))
    for i, m in enumerate(models):
        ax.bar(x + i * bw, [strict_pct(m, a) for a in all_assertions], width=bw, label=m)
    add_global_mean(ax)
    ax.set_ylim(0, 100)
    ax.set_ylabel("Exact Match (%)")
    ax.set_xticks(x + bw * len(models) / 2)
    ax.set_xticklabels(all_assertions, rotation=60, ha="right")
    ax.margins(x=0.01)

# ─────────────────────────── Grouped-bar plots ──────────────────────
for N in range(1, 5):
    subset = [m for m in all_models if m.endswith(f"-{N}")]
    if not subset:
        continue
    fig_w = max(22, len(all_assertions) * 1.6)
    # remove "assertThrows" from the list of assertions
    subset = [m for m in subset if m not in {"assertThrows", "assertThrowsExactly", "assertThrowsTimeout"}]
    fig_w = max(22, len(subset) * 1.6)
    fig, ax = plt.subplots(figsize=(fig_w, 10))
    grouped_accuracy_bars(ax, subset)
    ax.set_title(f"Assertion Exact Match – models ending ‘-{N}’", loc="left")
    ax.legend(ncol=2, loc="upper right")
    fig.tight_layout()
    fig.savefig(PLOT_TEMPLATE.format(N), dpi=300, bbox_inches="tight")
    plt.close(fig)

# ───────────────────────────── Box plot ─────────────────────────────
box_data = [[strict_pct(m, a) for m in all_models if assertion_stats[m][a]["total"]] for a in all_assertions]
# remove "assertThrows" from the list of assertions
box_data = [bd for bd, a in zip(box_data, all_assertions) if a not in {"assertThrows", "assertThrowsExactly", "assertThrowsTimeout"}]
all_assertions = [a for a in all_assertions if a not in {"assertThrows", "assertThrowsExactly", "assertThrowsTimeout"}]

fig_w = max(18, len(all_assertions) * 1.1)
fig, ax = plt.subplots(figsize=(fig_w, 10))
ax.boxplot(
    box_data,
    labels=all_assertions,
    widths=0.35,
    showmeans=True,
    meanprops={"marker": "^", "markerfacecolor": "green"},
)
add_global_mean(ax)
ax.set_ylabel("Exact Match (%)")
#ax.set_title("Per-model Exact Match by assertion type", loc="left")
ax.set_ylim(0, 100)
ax.set_xticklabels(all_assertions, rotation=60, ha="right")
ax.legend(loc="upper right")
fig.tight_layout()
fig.savefig(BOX_PLOT_FILE, dpi=300, bbox_inches="tight")
plt.close(fig)

# print for each assertion type and each model the mean and std
with open(TEX_FILE, "w") as f:
    f.write("\\begin{table}[H]\n")
    f.write("\\centering\n")
    f.write("\\begin{tabular}{|l|l|l|l|}\n")
    f.write("\\hline\n")
    f.write("Assertion & Mean & Std & Count \\\\\n")
    f.write("\\hline\n")

    for a in all_assertions:
        means = [strict_pct(m, a) for m in all_models if assertion_stats[m][a]["total"]]
        stds = [np.std([strict_pct(m, a) for m in all_models if assertion_stats[m][a]["total"]])]
        counts = [assertion_stats[m][a]["total"] for m in all_models if assertion_stats[m][a]["total"]]
        mean = np.mean(means)
        std = np.mean(stds)
        count = np.mean(counts)
        f.write(f"{a} & {mean:.2f} & {std:.2f} & {count:.2f} \\\\\n")
        f.write("\\hline\n")

    f.write("\\end{tabular}\n")
    f.write("\\caption{Per-assertion type exact match statistics}\n")
    f.write("\\label{tab:assertion_statistics}\n")
    f.write("\\end{table}\n")


# ────────────────── Scatter plot: accuracy vs. frequency ──────────────────
from math import isnan
from scipy.stats import pearsonr           # add this import at the top

SCATTER_PLOT_FILE = os.path.join(
    ROOT, "assertion_accuracy_vs_frequency.pdf"
)

# aggregate counts and strict hits across *all* models
total_counts  = {a: sum(assertion_stats[m][a]["total"]  for m in assertion_stats)
                 for a in all_assertions}
total_correct = {a: sum(assertion_stats[m][a]["strict"] for m in assertion_stats)
                 for a in all_assertions}

# strict-accuracy (%)
accuracy_pct  = {a: (100 * total_correct[a] / total_counts[a]) if total_counts[a] else 0.0
                 for a in all_assertions}

# scatter data
x = np.array([total_counts[a] for a in all_assertions])
y = np.array([accuracy_pct[a]  for a in all_assertions])

fig, ax = plt.subplots(figsize=(12, 8))
ax.scatter(x, y, s=200, alpha=0.7)            # NEW – uniform size  (200≈√200 pt)
ax.set_xscale("log")                          # ← NEW: log-scale on x-axis ✅

# annotate each point
for a, xi, yi in zip(all_assertions, x, y):
    ax.annotate(a, (xi, yi),
                textcoords="offset points", xytext=(5, 5),
                ha="left", fontsize=14)

# Pearson correlation
r, p = pearsonr(x, y)
ax.set_ylabel("Exact Match (%)")
ax.set_ylim(0, 100)
fig.tight_layout()
fig.savefig(SCATTER_PLOT_FILE, dpi=300, bbox_inches="tight")
plt.close(fig)

print(f"Pearson correlation: r = {r:.3f}, p = {p:.3e}")