
from __future__ import annotations

import os
import re
import json
from collections import defaultdict
from typing import List

import numpy as np
import pandas as pd
import matplotlib.pyplot as plt

# ───────────────────────── configuration ────────────────────────────
ROOT = "/Users/luca/Documents/USI/Davide/llm-prompts-empirical-study/_rq1/output/inference"
STATS_FILE = os.path.join(ROOT, "inference_stats.json")
ASSERTION_STATS_FILE = os.path.join(ROOT, "inference_assertion_stats.json")
PLOT_TEMPLATE = os.path.join(ROOT, "inference_assertion_success_N{}.pdf")

oracle_re = re.compile(r"\[oracle\](.*?)\[\s*[\\/]oracle\]", re.DOTALL | re.I)
assertion_re = re.compile(r"\b(assert[A-Z][A-Za-z]*)\b")

# ────────────────────── containers for statistics ───────────────────
stats: defaultdict[str, dict[str, int]] = defaultdict(lambda: {"strict": 0, "flex": 0, "total": 0})
assertion_stats: defaultdict[str, defaultdict[str, dict[str, int]]] = defaultdict(
    lambda: defaultdict(lambda: {"strict": 0, "flex": 0, "total": 0})
)

# ─────────────────────────── traversal ──────────────────────────────
for dirpath, _, files in os.walk(ROOT):
    csvs = [f for f in files if f.lower().endswith(".csv")]
    if not csvs:
        continue

    model = os.path.relpath(dirpath, ROOT).split(os.sep)[0]

    for csv_name in csvs:
        df = pd.read_csv(
            os.path.join(dirpath, csv_name),
            header=None,
            dtype=str,
            keep_default_na=False,
        )

        gt = df.iloc[:, 3].astype(str).str.strip()
        pred_raw = df.iloc[:, 4].astype(str)
        pred_clean = (
            pred_raw.str.extract(oracle_re, expand=False).fillna(pred_raw).str.strip()
        )

        for g, p in zip(gt, pred_clean):
            strict = (g == p) or (g == p + ";")
            flex = strict or (g in p) or (p in g)

            # overall counters
            stats[model]["strict"] += strict
            stats[model]["flex"] += flex
            stats[model]["total"] += 1

            # per‑assertion
            m_ = assertion_re.search(g)
            assertion = m_.group(1) if m_ else "UNKNOWN"
            a = assertion_stats[model][assertion]
            a["strict"] += strict
            a["flex"] += flex
            a["total"] += 1

# ─────────────────────────── helper plot ────────────────────────────
all_assertions: List[str] = sorted({a for m in assertion_stats for a in assertion_stats[m]})
all_models: List[str] = sorted(assertion_stats.keys())


def grouped_accuracy_bars(ax, models: List[str]):
    """Draw grouped bars with strict‑accuracy percentage for *models*."""
    if not models:
        ax.set_visible(False)
        return

    bar_w = 0.8 / len(models)
    x = np.arange(len(all_assertions))

    for idx, m in enumerate(models):
        percentages = []
        for a in all_assertions:
            data = assertion_stats[m].get(a, {"strict": 0, "total": 0})
            pct = 100 * data["strict"] / data["total"] if data["total"] else 0
            percentages.append(pct)
        ax.bar(x + idx * bar_w, percentages, width=bar_w, label=m)

    ax.set_ylim(0, 100)
    ax.set_ylabel("Strict accuracy (%)")
    ax.set_xticks(x + bar_w * len(models) / 2)
    ax.set_xticklabels(all_assertions, rotation=90)
    ax.margins(x=0.01)

# ────────────────────────── create plots ────────────────────────────
print("Generating per‑N accuracy plots …")
for N in range(1, 5):
    models_N = [m for m in all_models if m.endswith(f"-{N}")]
    if not models_N:
        print(f"  [skip] No models using Prompt {N}")
        continue

    fig_w = max(15, len(all_assertions) * 1.2)
    fig, ax = plt.subplots(figsize=(fig_w, 6))
    grouped_accuracy_bars(ax, models_N)
    ax.set_title(f"Assertion strict‑accuracy – models using Prompt '{N}'", loc="left", fontsize="medium")
    # legend inside plot area
    ax.legend(fontsize="x-small", ncol=2, loc="upper center")
    fig.tight_layout()
    pdf_path = PLOT_TEMPLATE.format(N)
    fig.savefig(pdf_path, dpi=300, bbox_inches="tight")
    plt.close(fig)
    print(f"  → {os.path.basename(pdf_path)}")

# ───────────────────────────── JSONs ────────────────────────────────
print("Writing JSON summaries …")
with open(STATS_FILE, "w", encoding="utf-8") as f:
    json.dump(
        {
            m: {
                "total": s["total"],
                "strict": s["strict"],
                "strict_percent": round(100 * s["strict"] / s["total"], 2) if s["total"] else 0,
                "flexible": s["flex"],
                "flexible_percent": round(100 * s["flex"] / s["total"], 2) if s["total"] else 0,
            }
            for m, s in stats.items()
        },
        f,
        indent=2,
        ensure_ascii=False,
    )
with open(ASSERTION_STATS_FILE, "w", encoding="utf-8") as f:
    json.dump(
        {
            m: {
                a: {
                    "total": d["total"],
                    "strict": d["strict"],
                    "strict_percent": round(100 * d["strict"] / d["total"], 2) if d["total"] else 0,
                    "flexible": d["flex"],
                    "flexible_percent": round(100 * d["flex"] / d["total"], 2) if d["total"] else 0,
                }
                for a, d in assertion_stats[m].items()
            }
            for m in assertion_stats
        },
        f,
        indent=2,
        ensure_ascii=False,
    )
print(f"Overall stats written to: {STATS_FILE}")
print(f"Assertion stats written to: {ASSERTION_STATS_FILE}")
print("Finished. Four accuracy‑percentage plots saved.")
