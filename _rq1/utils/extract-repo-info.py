import re
import sys
import csv

if __name__ == "__main__":
    file_name = sys.argv[1]
    github_repos_file_path = sys.argv[2]
    match = re.search(r'oracles-datapoints-(\d+)', file_name)
    if match:
        datapoint_number = match.group(1)
        with open(github_repos_file_path, 'r') as github_repos_file:
            reader = csv.reader(github_repos_file)
            for row in reader:
                if row[0] == datapoint_number:
                    print(f"{datapoint_number} {row[1]}")
                    sys.exit(0)
    # If not found, return an error
    sys.exit(1)