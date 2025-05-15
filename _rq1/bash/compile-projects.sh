# The script run the inference task with the vanilla llms required to replicate RQ1

# Get current directory
current_dir=$(realpath "$(dirname "${BASH_SOURCE[@]}")")
# Setup global variables and local variables
source "${current_dir}/../../scripts/utils/bash/global_variables.sh"
source "${current_dir}/../../scripts/dataset/bash/utils/local_variables.sh"
source "${current_dir}/utils/local_variables.sh"
source "${current_dir}/../../scripts/utils/bash/init_sdkman.sh"

JAVA_VERSIONS=(
    "$JAVA8"
    "$JAVA11"
    "$JAVA17"
    "$JAVA21"
)

successfully_compiled=()
failing_compiled=()

# Add Maven bin directory to PATH temporarily
export PATH="$MAVEN_BIN:$PATH"

source "${current_dir}/../../.venv/bin/activate"

GITHUB_REPOS_LIST_FILE=${1:-$GITHUB_REPOS_LIST_FILE}

# Read the CSV file line by line and split into project_id, bug_id, and modified_classes fields
while IFS=, read -r repo_id repo_name; do
    # Clean repo_name string from undesired white-spaces/line-breaks introduced with the CSV parsing
    repo_name="${repo_name//[$'\t\r\n ']/}"
    compiled=false
    for version in "${JAVA_VERSIONS[@]}"; do
        echo "Compiling project: ${repo_name} with Java version: ${version}."
        sdk use java "$version"
        cd "${GITHUB_REPOS_DIR}/${repo_name}"
        if [ ! -d "${OUTPUT_DIR}/compiled" ]; then
            mkdir -p "${OUTPUT_DIR}/compiled"
        fi
        mvn clean install -DskipTests > /dev/null 2>&1

        if [ $? -eq 0 ]; then
          echo "Compilation successful for ${repo_name} with Java version ${version}"
          successfully_compiled+=("${repo_name},${repo_id},${version}")
          compiled=true
          break
        else
          echo "Compilation failed for ${repo_name} with Java version ${version}"
        fi
    done
    if [ "$compiled" = false ]; then
        echo "Compilation failed for all Java versions for ${repo_name}"
        failing_compiled+=("${repo_name},${repo_id}")
    fi
done < "$GITHUB_REPOS_LIST_FILE"

# Save the results to a CSV file
printf "%s\n" "${successfully_compiled[@]}" > "${OUTPUT_DIR}/compiled/successfully_compiled_projects.csv"
printf "%s\n" "${failing_compiled[@]}" > "${OUTPUT_DIR}/compiled/failing_compiled_projects.csv"