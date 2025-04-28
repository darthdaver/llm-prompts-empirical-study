import os
import json
import csv

assertions_types = [
    "assertAll",
    "assertArrayEquals",
    "assertDoesNotThrow",
    "assertEquals",
    "assertFalse",
    "assertInstanceOf",
    "assertIterableEquals",
    "assertLinesMatch",
    "assertNotEquals",
    "assertNotNull",
    "assertNotSame",
    "assertNull",
    "assertSame",
    "assertThat",
    "assertThrows",
    "assertThrowsExactly",
    "assertTimeout",
    "assertTimeoutPreemptively",
    "assertTrue",
    "fail",
    "THROW EXCEPTION"
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

    print(github_repos_path)

    with open(github_repos_path, "r") as f:
        reader = csv.reader(f)

        for row in reader:
            repo_id = row[0]
            repo_name = row[1]

            for root, _, files in os.walk(os.path.join(output_dataset_path, repo_id)):
                for file in files:
                    if file.endswith(".json"):
                        print(f"Processing {file} in {repo_name} ({repo_id})")
                        project_stats = {**stats_template}
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
                                                print(f"Uncommon assertion type: {dp['target']}")
                                                project_stats["uncommon"].append(dp["target"])
                                                overall_stats["uncommon"].append(dp["target"])
                                            found = True
                                            break
                                    if not found:
                                        print(f"Unknown assertion type: {dp['target']}")
                                        project_stats["unknown"].append(dp["target"])
                                        overall_stats["unknown"].append(dp["target"])
            print(f"Processing completed")
            with open(os.path.join(output_distribution_path, f"{repo_id}.json"), "w") as f:
                json.dump(project_stats, f, indent=4)
    with open(os.path.join(output_distribution_path, "overall.json"), "w") as f:
        json.dump(overall_stats, f, indent=4)
