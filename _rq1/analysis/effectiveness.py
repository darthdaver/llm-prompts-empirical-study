#!/usr/bin/env python3
"""
Count exact matches between the ground-truth (4th column) and the text found
between [oracle] … [\\oracle] in the prediction (5th column) for every CSV file
under the *inference* directory.  
A separate score is kept for each first-level sub-folder (e.g. qwen2.5_1.5b-1).

Required package: pandas (`pip install pandas`).
"""

import os
import re
import json
import pandas as pd
from collections import defaultdict

ROOT = "/Users/luca/Documents/USI/Davide/llm-prompts-empirical-study/_rq1/output/inference"
STATS_FILE = os.path.join(ROOT, "inference_stats.json")

oracle_re = re.compile(r"\[oracle\](.*?)\[\s*[\\/]oracle\]", re.DOTALL | re.I)

# container: {model: {"strict": x, "flex": y, "total": n}}
stats = defaultdict(lambda: {"strict": 0, "flex": 0, "total": 0})

for dirpath, _, files in os.walk(ROOT):
    csvs = [f for f in files if f.lower().endswith(".csv")]
    if not csvs:
        continue

    model = os.path.relpath(dirpath, ROOT).split(os.sep)[0]

    for csv in csvs:
        df = pd.read_csv(
            os.path.join(dirpath, csv),
            header=None,
            dtype=str,
            keep_default_na=False
        )

        gt = df.iloc[:, 3].astype(str).str.strip()
        pred_raw = df.iloc[:, 4].astype(str)
        pred_clean = (
            pred_raw
            .str.extract(oracle_re, expand=False)
            .fillna(pred_raw)
            .str.strip()
        )

        for g, p in zip(gt, pred_clean):
            strict = (g == p) or (g == (p + ";"))
            flex   = strict or (g in p) or (p in g)

            #if  (not (g == (p + ";"))) and flex:
            #    print(f"WARNING: {g} == {p}")
            #    break

            stats[model]["strict"] += strict
            stats[model]["flex"]   += flex
            stats[model]["total"]  += 1

# ------------------------- console output -------------------------

print("METRICS:\n\
       strict = (groundtruth == prediction)\n\
       flex = strict or (groundtruth in prediction) or (prediction in groundtruth)\n\n")

print("PROMPTS:\n \
     Prompt 1: Test prefix + invoked methods\n \
     Prompt 2: Test prefix  + invoked methods + focal class \n \
     Prompt 3: Test prefix + invoked methods + test class \n \
     Prompt 4: Test prefix + invoked methods + test class + focal class ")

print("\n-------------------------\n")

different_model = 0 # better output printing

for m, s in sorted(stats.items()):
    different_model += 1
    t = s["total"]
    if t == 0:
        continue
    strict_pct = 100 * s["strict"] / t
    flex_pct   = 100 * s["flex"]   / t
    print(
        f"{m} -> strict: {s['strict']} / {t} ({strict_pct:.1f}%), "
        f"flexible: {s['flex']} / {t} ({flex_pct:.1f}%)"
    )
    #if different_model == 4:
    #    print("\n")
    #    different_model = 0

# ------------------------ JSON serialisation ----------------------
json_data = {
    model: {
        "total":   s["total"],
        "strict":  s["strict"],
        "strict_percent":  round(100 * s["strict"] / s["total"], 2) if s["total"] else 0,
        "flexible":        s["flex"],
        "flexible_percent":round(100 * s["flex"]   / s["total"], 2) if s["total"] else 0,
    }
    for model, s in stats.items()
}

with open(STATS_FILE, "w", encoding="utf-8") as f:
    json.dump(json_data, f, indent=2, ensure_ascii=False)

print(f"\nStats written to: {STATS_FILE}")