import os
import json

if __name__ == '__main__':
    mined_dataset_dir = os.path.join(os.path.dirname(__file__), "output", "miner")

    total = 0

    for root, _, files in os.walk(mined_dataset_dir):
        for file in files:
            if file.endswith(".json"):
                with open(os.path.join(root, file), "r") as f:
                    data = json.load(f)
                    for v in data["track"].values():
                        total += len(v)

    print(f"Total mined test cases: {total}")