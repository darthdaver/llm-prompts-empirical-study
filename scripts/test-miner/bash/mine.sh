#!/bin/bash
# The script mines test cases from a list of repositories provided in a csv file.
# By default, the script uses the list of repositories saved in the resources folder of the root dir (`resources/github-repos.csv`).
# However, the user can provide a different list of repositories by passing the absolute file path as the first argument
# to the script.
# The script operates in 4 steps, iteratively:
#   1. The script reads the current line of the CSV file and split it into repo_id and repo_name fields (the repo_url is generated from the repo_name)
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
GITHUB_REPOS_LIST_FILE=${1:-$GITHUB_REPOS_LIST_FILE}

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
    # Check how many pom.xml files are in the project
    cd "${GITHUB_REPOS_DIR}/${repo_name}"
    pom_count=$(find . -name "pom.xml" | wc -l)
    junit_plugin=false
    if [ "$pom_count" -eq 1 ]; then
      # Check if the project uses JUnit as plugin to run the tests
      if grep -qE '<artifactId>(junit|junit-jupiter)' pom.xml || grep -qE '<groupId>(junit|org\.junit\.jupiter)' pom.xml; then
        junit_plugin=true
      fi
    fi
    # Check if the project is a Maven project and if it has only one pom.xml file and uses JUnit as plugin
    if [[ "$project_type" == "maven" && "$pom_count" -eq 1 && "$junit_plugin" == true ]]; then
      echo "Maven project detected"
      # Mine the Maven project
      "${PY_ENV}" "${TEST_MINER_DIR}/mine.py" \
        --input_path "${GITHUB_REPOS_DIR}" \
        --output_path "${OUTPUT_DIR}" \
        --repo_name "${repo_name}" \
        --repo_url "${repo_url}" \
        --since "01/09/2024 00:00:00" \
        --operation "added"
    else
      echo "Unprocessed project"
    fi
done < "$GITHUB_REPOS_LIST_FILE"