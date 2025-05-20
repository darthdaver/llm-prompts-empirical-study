#!/usr/bin/env python3
"""
Generate assertion-level strict/flex accuracy statistics and publish-quality
figures from every CSV under the *inference* directory.

Outputs
=======
* `inference_stats.json`               – overall strict/flex per model
* `inference_assertion_stats.json`     – same metrics per assertion type
* `inference_assertion_success_N#.pdf` – four grouped-bar plots (prompt index N)
  with a red dashed line showing the **global mean strict-accuracy** across all
  models & assertions.
* `assertion_accuracy_boxplot.pdf`     – distribution of per-model accuracies
  (assertions sorted by descending mean) + the same global mean line.
* `assertion_accuracy_table.tex`       – LaTeX booktabs table (per-N averages)

All plots use very large fonts (base 30 pt) and 45° x-tick labels.
"""
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
    "font.size": 30,
    "axes.titlesize": 38,
    "axes.labelsize": 36,
    "xtick.labelsize": 28,
    "ytick.labelsize": 28,
    "legend.fontsize": 28,
    "figure.titlesize": 38,
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

            assertion = assertion_re.search(g).group(1) if assertion_re.search(g) else "Undefined"
            a = assertion_stats[model][assertion]
            a["strict"] += strict
            a["flex"] += flex
            a["total"] += 1

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
    ax.axhline(GLOBAL_MEAN, color="red", linestyle="--", linewidth=2, label=f"Global mean {GLOBAL_MEAN:.1f}%")


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
    ax.set_ylabel("Strict accuracy (%)")
    ax.set_xticks(x + bw * len(models) / 2)
    ax.set_xticklabels(all_assertions, rotation=45, ha="right")
    ax.margins(x=0.01)

# ─────────────────────────── Grouped-bar plots ──────────────────────
for N in range(1, 5):
    subset = [m for m in all_models if m.endswith(f"-{N}")]
    if not subset:
        continue
    fig_w = max(22, len(all_assertions) * 1.6)
    fig, ax = plt.subplots(figsize=(fig_w, 10))
    grouped_accuracy_bars(ax, subset)
    ax.set_title(f"Assertion strict accuracy – models ending ‘-{N}’", loc="left")
    ax.legend(ncol=2, loc="upper right")
    fig.tight_layout()
    fig.savefig(PLOT_TEMPLATE.format(N), dpi=300, bbox_inches="tight")
    plt.close(fig)

# ───────────────────────────── Box plot ─────────────────────────────
box_data = [[strict_pct(m, a) for m in all_models if assertion_stats[m][a]["total"]] for a in all_assertions]
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
ax.set_ylabel("Strict accuracy (%)")
#ax.set_title("Per-model strict accuracy by assertion type", loc="left")
ax.set_ylim(0, 100)
ax.set_xticklabels(all_assertions, rotation=45, ha="right")
ax.legend(loc="upper right")
fig.tight_layout()
fig.savefig(BOX_PLOT_FILE, dpi=300, bbox_inches="tight")
plt.close(fig)

# ─────────────────────────── LaTeX table ────────────────────────────
agg: dict[str, dict[int, float]] = {a: {N: 0.0 for N in range(1, 5)} for a in all_assertions}
counts = {a: {N: 0 for N in range(1, 5)} for a in all_assertions}
for m in all_models:
    suffix = m.rsplit("-", 1)[-1]
    if suffix.isdigit():
        N = int(suffix)
        if 1 <= N <= 4:
            for a in all_assertions:
                if assertion_stats[m][a]["total"]:
                    pct = strict_pct(m, a)
                    agg[a][N] += pct
                    counts[a][N] += 1
for a in all_assertions:
    for N in range(1, 5):
        c = counts[a][N]
        agg[a][N] = agg[a][N] / c if c else 0.0

tex_lines = [
    "% Auto-generated – assertion accuracy table",
    "\\begin{table}[ht]",
    "\\centering",
    "\\begin{tabular}{lcccc}",
    "\\toprule",
    "Assertion & N1 (\%) & N2 (\%) & N3 (\%) & N4 (\%) \\ ",
    "\\midrule",
]
for a in all_assertions:
    tex_lines.append(f"{a} & " + " & ".join(f"{agg[a][N]:.1f}" for N in range(1, 5)) + " \\" )
tex_lines.extend([
    "\\bottomrule",
    "\\end{tabular}",
    f"\\caption{{Average strict-accuracy (\%) per JUnit assertion type. Global mean = {GLOBAL_MEAN:.1f}\% (red dashed line in figures).}}",
    "\\label{tab:assertion-accuracy}",
    "\\end{table}",
])
with open(TEX_FILE, "w", encoding="utf-8") as f:
    f.write("\n".join(tex_lines))

