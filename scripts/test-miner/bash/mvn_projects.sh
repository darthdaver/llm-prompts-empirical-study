#!/bin/bash
# The script collects the list of maven projects from a list of github projects.

# Get current directory
current_dir=$(realpath "$(dirname "${BASH_SOURCE[@]}")")
# Setup global & local variables
source "${current_dir}/../../utils/bash/global_variables.sh"
source "${current_dir}/utils/local_variables.sh"
GITHUB_REPOS_LIST_FILE=${1:-$GITHUB_REPOS_LIST_FILE}
maven_repos_file="${OUTPUT_DIR}/maven_repos.csv"
unprocessed_repos_file="${OUTPUT_DIR}/unprocessed_repos.csv"
mvn_counter=0
unprocessed_counter=0

if [ ! -d "${GITHUB_REPOS_DIR}" ]; then
  mkdir -p "${GITHUB_REPOS_DIR}"
fi
if [ ! -d "${OUTPUT_DIR}" ]; then
  mkdir -p "${OUTPUT_DIR}"
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
while IFS=, read -r repo_id repo_name; do
    cd "$ROOT_DIR"
    # Clean repo_name string from undesired white-spaces/line-breaks introduced with the CSV parsing
    repo_name="${repo_name//[$'\t\r\n ']/}"
    echo "Processing project: ${repo_name}."
    repo_url="${GITHUB_BASE_URL}/${repo_name}.git"
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
      mvn_counter=$((mvn_counter + 1))
    else
      echo "Unprocessed project"
      echo "${repo_name},${repo_url}" >> "$unprocessed_repos_file"
      unprocessed_counter=$((unprocessed_counter + 1))
    fi
done < "$GITHUB_REPOS_LIST_FILE"

echo "Maven projects detected: $mvn_counter"
echo "Unprocessed projects detected: $unprocessed_counter"