import os
import json
import csv
import matplotlib.pyplot as plt
import seaborn as sns
import pandas as pd

assertions_types = [
    "assertAll", "assertArrayEquals", "assertDoesNotThrow", "assertEquals", "assertFalse",
    "assertInstanceOf", "assertIterableEquals", "assertLinesMatch", "assertNotEquals",
    "assertNotNull", "assertNotSame", "assertNull", "assertSame", "assertThat",
    "assertThrows", "assertThrowsExactly", "assertTimeout", "assertTimeoutPreemptively",
    "assertTrue", "fail", "THROW EXCEPTION"
]

if __name__ == "__main__":
    output_dataset_path = os.path.join(os.path.dirname(__file__), "..", "output", "raw-oracles-dataset")
    output_distribution_path = os.path.join(os.path.dirname(__file__), "..", "output", "assertion-distribution")
    github_repos_path = os.path.join(os.path.dirname(__file__), "..", "..", "..", "resources", "highly-rated-github-repos.csv")

    if not os.path.exists(output_distribution_path):
        os.makedirs(output_distribution_path)

    stats_template = {
        **{ a : 0 for a in assertions_types },
        "total": 0,
        "uncommon": [],
        "unknown": []
    }

    overall_stats = { **stats_template }
    project_distributions = []

    with open(github_repos_path, "r") as f:
        reader = csv.reader(f)

        for row in reader:
            repo_id = row[0]
            repo_name = row[1]
            project_stats = {**stats_template}

            for root, _, files in os.walk(os.path.join(output_dataset_path, repo_id)):
                for file in files:
                    if file.endswith(".json"):
                        with open(os.path.join(root, file), "r") as f:
                            data = json.load(f)
                            for obj in data:
                                for dp in obj["datapoints"]:
                                    found = False
                                    for a_type in assertions_types:
                                        if a_type in dp["target"]:
                                            project_stats[a_type] += 1
                                            overall_stats[a_type] += 1
                                            project_stats["total"] += 1
                                            overall_stats["total"] += 1
                                            if not dp["target"].startswith(a_type) and not a_type == "assertThrows":
                                                project_stats["uncommon"].append(dp["target"])
                                                overall_stats["uncommon"].append(dp["target"])
                                            found = True
                                            break
                                    if not found:
                                        project_stats["unknown"].append(dp["target"])
                                        overall_stats["unknown"].append(dp["target"])
            with open(os.path.join(output_distribution_path, f"{repo_id}.json"), "w") as f:
                json.dump(project_stats, f, indent=4)

            dist = {k: project_stats[k] for k in assertions_types}
            dist["repo_id"] = repo_id
            dist["repo_name"] = repo_name
            project_distributions.append(dist)

    with open(os.path.join(output_distribution_path, "overall.json"), "w") as f:
        json.dump(overall_stats, f, indent=4)

    # === DATAFRAME SETUP ===
    sns.set(style="whitegrid")
    df = pd.DataFrame(project_distributions)
    df.set_index("repo_name", inplace=True)

    # === BASIC BAR PLOTS ===
    overall_counts = {k: overall_stats[k] for k in assertions_types}
    sorted_overall = sorted(overall_counts.items(), key=lambda x: x[1], reverse=True)
    keys, values = zip(*sorted_overall)
    plt.figure(figsize=(12, 6))
    sns.barplot(x=list(keys), y=list(values), palette="viridis")
    plt.xticks(rotation=45, ha="right")
    plt.ylabel("Count")
    plt.title("Overall Assertion Type Distribution")
    plt.tight_layout()
    plt.savefig(os.path.join(output_distribution_path, "overall_distribution.pdf"))
    plt.close()

    # === HEATMAP ===
    plt.figure(figsize=(14, max(4, len(df) * 0.3)))
    sns.heatmap(df[assertions_types], cmap="Blues", linewidths=0.5)
    plt.title("Assertion Type Distribution per Project")
    plt.xlabel("Assertion Type")
    plt.ylabel("Project")
    plt.tight_layout()
    plt.savefig(os.path.join(output_distribution_path, "per_project_distribution_heatmap.pdf"))
    plt.close()

    # === VIOLIN PLOT ===
    melted_df = df[assertions_types].reset_index().melt(id_vars=["repo_name"], var_name="assertion_type", value_name="count")
    plt.figure(figsize=(14, 6))
    sns.violinplot(data=melted_df, x="assertion_type", y="count", inner="box", palette="coolwarm")
    plt.xticks(rotation=45, ha="right")
    plt.title("Distribution of Assertion Usage Across Projects (Violin Plot)")
    plt.tight_layout()
    plt.savefig(os.path.join(output_distribution_path, "violin_distribution.pdf"))
    plt.close()




    print("📊 All visualizations saved to:", output_distribution_path)