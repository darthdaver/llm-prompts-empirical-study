#!/bin/bash
# The script mines test cases from a list of repositories provided in a csv file.
# By default, the script uses the list of repositories saved in the resources folder (`test-miner/resources/github-repos.csv`).
# However, the user can provide a different list of repositories by passing the absolute file path as the first argument
# to the script.
# The script operates in 4 steps, iteratively:
#   1. The script reads the current line of the CSV file and split it into repo_name and repo_url fields
#   2. The script clones the repository
#   3. The script detects the type of project (Maven, Gradle, Ant) and processes it accordingly. Only Maven projects are
#      processed, as the other two types are not supported yet. The script also sets up the dependencies for the project.
#   4. The script traverses the commit history of the project from a given date and collects the modified test cases from
#      the first commit after the given date, up to the last commit of the project.
# The mined test cases and the statistics are stored in the root folder of the project (by default `llm-prompts-empirical-study`)
# in the `output` directory.

# Get current directory
current_dir=$(realpath "$(dirname "${BASH_SOURCE[@]}")")
# Setup global & local variables
source "${current_dir}/../../utils/bash/global_variables.sh"
source "${current_dir}/utils/local_variables.sh"
maven_repos_file="${OUTPUT_STATS_DIR}/maven_repos.csv"
unprocessed_repos_file="${OUTPUT_STATS_DIR}/unprocessed_repos.csv"

if [ ! -d "${GITHUB_REPOS_DIR}" ]; then
  mkdir -p "${GITHUB_REPOS_DIR}"
fi
if [ ! -d "${OUTPUT_STATS_DIR}" ]; then
  mkdir -p "${OUTPUT_STATS_DIR}"
fi
if [ ! -d "${OUTPUT_MINE_DIR}" ]; then
  mkdir -p "${OUTPUT_MINE_DIR}"
fi

if [ -f "$maven_repos_file" ]; then
  echo "Removing old maven_repos.csv file"
  # Remove the file
  rm "$maven_repos_file"
  echo "Creating new empty maven_repos.csv file"
  touch "$maven_repos_file"
  chmod 666 "$maven_repos_file"
fi
if [ -f "$unprocessed_repos_file" ]; then
  echo "Removing old unprocessed_repos.csv file"
  # Remove the file
  rm "$unprocessed_repos_file"
  echo "Creating new empty unprocessed_repos.csv file"
  touch "$unprocessed_repos_file"
  chmod 666 "$unprocessed_repos_file"
fi

# Function to detect and classify projects
scan_projects() {
    local dir="$1"
    if [ -f "$dir/pom.xml" ]; then
        echo "maven"
    elif [ -f "$dir/build.gradle" ] || [ -f "$dir/build.gradle.kts" ] || [ -f "settings.gradle" ]; then
        echo "gradle"
    elif [ -f "$dir/build.xml" ] || [ -f "$dir/build/build.xml" ]; then
        echo "ant"
    else
        echo "unprocessed"
    fi
}

export -f scan_projects

# Read the CSV file line by line and split into project_id, bug_id, and modified_classes fields
while IFS=, read -r repo_name repo_url; do
    cd "$ROOT_DIR"
    echo "Processing project: ${repo_name}."
    if [ ! -d "${GITHUB_REPOS_DIR}/${repo_name}" ]; then
      # Clean modified_classes string from undesired white-spaces/line-breaks introduced with the CSV parsing
      commit_sha="${commit_sha//[$'\t\r\n ']/}"
      # Clone project
      echo "Cloning project in folder: ${GITHUB_REPOS_DIR}/${repo_name}"
      git clone "${repo_url}" "${GITHUB_REPOS_DIR}/${repo_name}"|| echo "Error cloning repository: ${repo_url}" >&2
    fi
    project_type=$(scan_projects "${GITHUB_REPOS_DIR}/${repo_name}")
    if [ "$project_type" == "maven" ]; then
      echo "Maven project detected"
      echo "${repo_name},${repo_url}" >> "$maven_repos_file"
      # Mine the Maven project
      python "${TEST_MINER_DIR}/mine.py" \
        --input_path "${GITHUB_REPOS_DIR}" \
        --output_path "${OUTPUT_MINE_DIR}" \
        --output_stats_path "${OUTPUT_STATS_DIR}" \
        --repo_name "${repo_name}" \
        --repo_url "${repo_url}" \
        --since "01/08/2024 00:00:00"
    else
      echo "Unprocessed project"
      echo "${repo_name},${repo_url}" >> "$unprocessed_repos_file"
    fi
done < "$GITHUB_REPO_DIR"