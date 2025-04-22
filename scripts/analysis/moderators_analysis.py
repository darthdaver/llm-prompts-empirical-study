import json
import os
import re
from collections import defaultdict, Counter
import matplotlib.pyplot as plt
from matplotlib.backends.backend_pdf import PdfPages
import numpy as np
import pandas as pd

# === CONFIGURATION ===
root_data_folder = './resources/inference/'
output_pdf_path = './output/moderators_analysis/equivalence_analysis.pdf'

# === LOAD DATA ===
def load_all_datapoint_jsons(root_folder):
    all_entries = []
    datapoint_vote_stats = []
    prompt_type_votes = defaultdict(lambda: Counter({'EQUIVALENT': 0, 'NOT EQUIVALENT': 0, 'UNKNOWN':0, 'NO PREDICTION': 0, 'EXACT MATCH': 0}))

    for root, _, files in os.walk(root_folder):
        for fname in files:
            if 'datapoints' in fname and fname.endswith('.json'):
                full_path = os.path.join(root, fname)

                # Extract prompt type from path: assume folder name is model_name-N
                match = re.search(r'[^/\\]+-(\d)', full_path)
                prompt_type = int(match.group(1)) if match else None

                try:
                    with open(full_path, 'r') as f:
                        data = json.load(f)
                        if isinstance(data, dict):
                            data = [data]

                        for entry in data:
                            entry['__prompt_type'] = prompt_type
                            all_entries.append(entry)

                            # Count votes per datapoint
                            per_datapoint = Counter()
                            for key, value in entry.items():
                                if ':' in key and value in ('EQUIVALENT', 'NOT EQUIVALENT', 'UNKNOWN'):
                                    per_datapoint[value] += 1
                                    if prompt_type:
                                        prompt_type_votes[prompt_type][value] += 1
                                if key == 'prediction' and value == "":
                                    per_datapoint['NO PREDICTION'] += 1
                                elif key == 'match' and value == True:
                                    per_datapoint['EXACT MATCH'] += 1
                                    

                            datapoint_vote_stats.append(per_datapoint)

                except Exception as e:
                    print(f"Failed to load {full_path}: {e}")
    return all_entries, datapoint_vote_stats, prompt_type_votes

print("Loading datapoint files...")
data, datapoint_vote_stats, prompt_type_votes = load_all_datapoint_jsons(root_data_folder)
print(f"Loaded {len(data)} datapoints.")

# === AGGREGATE MODEL STATS ===
model_equiv_counts = defaultdict(lambda: Counter({'EQUIVALENT': 0, 'NOT EQUIVALENT': 0, 'UNKNOWN': 0, 'NO PREDICTION': 0, 'EXACT MATCH': 0}))
model_names = set()

for entry in data:
    for key, value in entry.items():
        if ':' in key and value in ('EQUIVALENT', 'NOT EQUIVALENT'):
            model_equiv_counts[key][value] += 1
            model_names.add(key)
        if key == 'prediction' and value == "":
            model_equiv_counts['prediction']['NO PREDICTION'] += 1
        elif key == 'match' and value == True:
            model_equiv_counts['match']['EXACT MATCH'] += 1

model_names = sorted(model_names)

# === PLOTTING ===
with PdfPages(output_pdf_path) as pdf:

    # 0. Table of model equivalence counts
    # Collect percentages per model
    percent_data = {}

    # Add global NO PREDICTION and EXACT MATCH
    global_no_pred = model_equiv_counts['prediction']['NO PREDICTION']
    global_exact_match = model_equiv_counts['match']['EXACT MATCH']
    global_total = sum(model_equiv_counts[model]['EQUIVALENT'] + model_equiv_counts[model]['NOT EQUIVALENT'] for model in model_names)

    if global_total > 0:
        no_predictions = round((global_no_pred / global_total) * 100, 1)
        exact_matchs = round((global_exact_match / global_total) * 100, 1)
    else:
        no_predictions = 0
        exact_matchs = 0

    
    for model in model_names:
        equiv = model_equiv_counts[model]['EQUIVALENT']
        not_equiv = model_equiv_counts[model]['NOT EQUIVALENT']
        total = equiv + not_equiv
        if total == 0:
            continue  # skip if no data

        percent_data[model] = {
            'EQUIVALENT': round((equiv / total) * 100, 1),
            'NOT EQUIVALENT': round((not_equiv / total) * 100, 1),
            'NO PREDICTION': no_predictions,  # leave blank for now
            'EXACT MATCH': exact_matchs     # leave blank for now
        }

    

    # Plot table
    df = pd.DataFrame.from_dict(percent_data, orient='index')
    df = df[['EQUIVALENT', 'NOT EQUIVALENT', 'NO PREDICTION', 'EXACT MATCH']]

    plt.figure(figsize=(10, len(df) * 0.6 + 1))
    plt.axis('off')
    table = plt.table(cellText=df.values,
                    rowLabels=df.index,
                    colLabels=df.columns,
                    cellLoc='center',
                    loc='center')
    table.auto_set_font_size(False)
    table.set_fontsize(12)
    table.scale(1, 1.5)

    plt.title("Moderator decision in tabular form", pad=20, fontsize=14)
    plt.tight_layout()
    pdf.savefig()
    plt.close()

    # 1. Bar chart per model: EQUIVALENT vs NOT
    equivalents = [model_equiv_counts[model]['EQUIVALENT'] for model in model_names]
    not_equivalents = [model_equiv_counts[model]['NOT EQUIVALENT'] for model in model_names]
    no_predictions = [model_equiv_counts['prediction']['NO PREDICTION'] for model in model_names]
    exact_matchs = [model_equiv_counts['match']['EXACT MATCH'] for model in model_names]
    x = np.arange(len(model_names))  # use numpy for easy offset math
    bar_width = 0.2

    plt.figure(figsize=(12, 6))
    plt.bar(x - 1.5*bar_width, equivalents, width=bar_width, label='EQUIVALENT', color='blue')
    plt.bar(x - 0.5*bar_width, not_equivalents, width=bar_width, label='NOT EQUIVALENT', color='orange')
    plt.bar(x + 0.5*bar_width, no_predictions, width=bar_width, label='NO PREDICTION', color='red')
    plt.bar(x + 1.5*bar_width, exact_matchs, width=bar_width, label='EXACT MATCH', color='green')

    # add one more bar for NO PREDICTION and EXACT MATCH
    #no_prediction = [model_equiv_counts[model]['NO PREDICTION'] for model in model_names]
    #exact_match = [model_equiv_counts[model]['EXACT MATCH'] for model in model_names]
    #plt.bar(x, no_prediction, width=0.4, label='NO PREDICTION', color='blue', align='edge')
    #plt.bar(x, exact_match, width=0.4, label='EXACT MATCH', color='purple', align='edge')

    plt.xticks(x, model_names, rotation=45, ha='right')
    plt.ylabel('Count')
    plt.title('Equivalence Judgments per Model')
    plt.legend()
    plt.tight_layout()
    pdf.savefig()
    plt.close()

    # 2. Bar plot: Prompt types comparison
    prompt_types = sorted(prompt_type_votes.keys())
    equivs = [prompt_type_votes[t]['EQUIVALENT'] for t in prompt_types]
    not_equivs = [prompt_type_votes[t]['NOT EQUIVALENT'] for t in prompt_types]

    x = range(len(prompt_types))
    plt.figure(figsize=(8, 5))
    plt.bar(x, equivs, width=0.4, label='EQUIVALENT', color='green', align='center')
    plt.bar(x, not_equivs, width=0.4, label='NOT EQUIVALENT', color='red', align='edge')
    plt.xticks(x, [f'Prompt {t}' for t in prompt_types])
    plt.ylabel('Vote Count')
    plt.title('Equivalence Votes by Prompt Type')
    plt.legend()
    plt.tight_layout()
    pdf.savefig()
    plt.close()

    # 3. Agreement when match == true
    match_equiv_counts = []
    for entry in data:
        if entry.get("match") == True:
            count = sum(1 for key, val in entry.items() if ':' in key and val == 'EQUIVALENT')
            match_equiv_counts.append(count)

    plt.figure(figsize=(10, 5))
    plt.hist(match_equiv_counts, bins=range(0, max(match_equiv_counts)+2), color='blue', alpha=0.7)
    plt.xlabel('Number of Models Saying EQUIVALENT')
    plt.ylabel('Count of Datapoints')
    plt.title('Model Agreement (EQUIVALENT) When is EXACT MATCH')
    plt.grid(True)
    pdf.savefig()
    plt.close()

    # 4. Statistical metrics & threshold analysis
    equiv_per_datapoint = [sum(1 for k, v in entry.items() if ':' in k and v == 'EQUIVALENT') for entry in data]
    mean_val = np.mean(equiv_per_datapoint)
    median_val = np.median(equiv_per_datapoint)
    std_dev = np.std(equiv_per_datapoint)

    plt.figure(figsize=(10, 6))
    plt.hist(equiv_per_datapoint, bins=range(0, max(equiv_per_datapoint)+2), alpha=0.75, color='purple')
    plt.axvline(mean_val, color='green', linestyle='dashed', linewidth=2, label=f'Mean = {mean_val:.2f}')
    plt.axvline(median_val, color='orange', linestyle='dashed', linewidth=2, label=f'Median = {median_val:.2f}')
    plt.axvline(mean_val + std_dev, color='red', linestyle='dotted', linewidth=2, label=f'Mean + 1σ = {mean_val + std_dev:.2f}')
    plt.xlabel('Number of Models Voting EQUIVALENT')
    plt.ylabel('Datapoint Count')
    plt.title('Distribution of EQUIVALENT Votes per Datapoint\n(Use This for Threshold Decisions)')
    plt.legend()
    plt.grid(True)
    pdf.savefig()
    plt.close()

    # 5. Full agreement vs disagreement
    full_agree_equiv = 0
    full_agree_nonequiv = 0
    partial_agree = 0

    for entry in data:
        votes = [v for k, v in entry.items() if ':' in k and v in ('EQUIVALENT', 'NOT EQUIVALENT')]
        if len(set(votes)) == 1:
            if votes[0] == 'EQUIVALENT':
                full_agree_equiv += 1
            else:
                full_agree_nonequiv += 1
        else:
            partial_agree += 1

    labels = ['All EQUIVALENT', 'All NOT EQUIVALENT', 'Disagreement']
    values = [full_agree_equiv, full_agree_nonequiv, partial_agree]
    colors = ['green', 'red', 'gray']

    plt.figure(figsize=(8, 5))
    plt.bar(labels, values, color=colors)
    plt.ylabel('Number of Datapoints')
    plt.title('Full Agreement vs Disagreement Among Models')
    for i, v in enumerate(values):
        plt.text(i, v + 1, str(v), ha='center')
    plt.tight_layout()
    pdf.savefig()
    plt.close()

print(f"\n✅ Analysis complete. Results saved to: {output_pdf_path}")
