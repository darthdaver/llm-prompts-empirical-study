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
SUCCESSFUL_COMPILED_FILE=${2:-"successful_compiled_projects.csv"}
FAILING_COMPILED_FILE=${3:-"failing_compiled_projects.csv"}

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
    # Clean repo_name string from undesired white-spaces/line-breaks introduced with the CSV parsing
    repo_name="${repo_name//[$'\t\r\n ']/}"
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
      compiled=false
      for version in "${JAVA_VERSIONS[@]}"; do
          echo "Compiling project: ${repo_name} with Java version: ${version}."
          sdk use java "$version"
          cd "${GITHUB_REPOS_DIR}/${repo_name}"
          if [ ! -d "${OUTPUT_DIR}/compiled/successful" ]; then
              mkdir -p "${OUTPUT_DIR}/compiled/successful"
          fi
          if [ ! -d "${OUTPUT_DIR}/compiled/failed" ]; then
              mkdir -p "${OUTPUT_DIR}/compiled/failed"
          fi
          echo "Starting compiling..."
          max_attempts=3
          attempt=1
          timeout_duration=600
          while [ $attempt -le $max_attempts ]; do
            timeout "$timeout_duration" mvn clean install -DskipTests -Dgpg.skip=true
            exit_code=$?
            if [ $exit_code -eq 0 ]; then
                echo "Compilation successful for ${repo_name} with Java version ${version}"
                successfully_compiled+=("${repo_name},${repo_id},${version}")
                compiled=true
                break
            elif [ $exit_code -eq 124 ]; then
                echo "Timeout on attempt $attempt"
            else
                echo "Compilation failed for ${repo_name} with Java version ${version}"
                break
            fi
            attempt=$((attempt + 1))
            sleep 2
          done
          if [ "$compiled" = true ]; then
            break
          fi
      done
      if [ "$compiled" = false ]; then
          echo "Compilation failed for all Java versions for ${repo_name}"
          failing_compiled+=("${repo_name},${repo_id}")
      fi
    fi
    if [ -f "${OUTPUT_DIR}/compiled/successful/${SUCCESSFUL_COMPILED_FILE}" ]; then
        rm "${OUTPUT_DIR}/compiled/successful/${SUCCESSFUL_COMPILED_FILE}"
    fi
    if [ -f "${OUTPUT_DIR}/compiled/failed/${FAILING_COMPILED_FILE}" ]; then
        rm "${OUTPUT_DIR}/compiled/failed/${FAILING_COMPILED_FILE}"
    fi
    # Save the results to a CSV file
    printf "%s\n" "${successfully_compiled[@]}" > "${OUTPUT_DIR}/compiled/successful/${SUCCESSFUL_COMPILED_FILE}"
    printf "%s\n" "${failing_compiled[@]}" > "${OUTPUT_DIR}/compiled/failed/${FAILING_COMPILED_FILE}"
done < "$GITHUB_REPOS_LIST_FILE"