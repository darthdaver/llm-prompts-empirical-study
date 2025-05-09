#!/bin/bash
# The script generates the oracles dataset starting from a list of repositories provided in a csv file.
# By default, the script uses the list of repositories saved in the resources folder of the root dir (`resources/github-repos.csv`).
# However, the user can provide a different list of repositories by passing the absolute file path as the first argument
# to the script.
# A second argument can be passed to the script to specify whether to resolve dependencies or not (default is false).
# The script operates in 4 steps, iteratively:
#   1. The script reads the current line of the CSV file and split it into repo_id and repo_name fields
#      (the repo_url is generated from the repo_name)
#   2. The script detects the type of project (Maven, Gradle, Ant) and processes it accordingly (e.g. download maven
#      dependencies, if available). The script suppose that the github repository has already been downloaded during
#      the mining process
#   4. The script generates the oracles dataset for the current project
# The final dataset is stored in the `output/raw-oracles-dataset` directory (statistics are saved in `output/statistics`).

# Get current directory
current_dir=$(realpath "$(dirname "${BASH_SOURCE[@]}")")
# Setup global & local variables
source "${current_dir}/../../utils/bash/global_variables.sh"
source "${current_dir}/utils/local_variables.sh"
source "${current_dir}/utils/init_sdkman.sh"
# Add Maven bin directory to PATH temporarily
export PATH="$MAVEN_BIN:$PATH"

GITHUB_REPOS_LIST_FILE=${1:-$GITHUB_REPOS_LIST_FILE}
resolve_deps=${2:-"false"}

if [ ! -e "${DATASET_JAR}" ]; then
  cd "${DATASET_DIR}"
  sdk use java "$JAVA21"
  mvn clean package -DskipTests
fi

if [ ! -d "${LIB_DIR}/java8" ] || [ ! -d "${LIB_DIR}/java11" ] || [ ! -d "${LIB_DIR}/java17" ] || [ ! -d "${LIB_DIR}/java21" ]; then
    if [ -d "${LIB_DIR}" ]; then
        mv "${LIB_DIR}/java_classpaths.json" "${LIB_DIR}/../java_classpaths.json"
        rm -rf "${LIB_DIR}/"*
        mv "${LIB_DIR}/../java_classpaths.json" "${LIB_DIR}/java_classpaths.json"
    fi
    curl -o "${LIB_DIR}/jdks.zip" "${LIB_JDKS_LINK}"
    unzip "${LIB_DIR}/jdks.zip" -d "${LIB_DIR}"
    rm "${LIB_DIR}/jdks.zip"
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

# Read the CSV file line by line and split into project_id, bug_id, and modified_classes field
while IFS=, read -r repo_id repo_name; do
    cd "$DATASET_DIR"
    # Clean repo_name string from undesired white-spaces/line-breaks introduced with the CSV parsing
    repo_name="${repo_name//[$'\t\r\n ']/}"
    echo "Processing project: ${repo_name}."
    repo_url="${GITHUB_BASE_URL}/${repo_name}.git"
    project_type=$(scan_projects "${GITHUB_REPOS_DIR}/${repo_name}")
    # Check if the project is a Maven project and if it has only one pom.xml file and uses JUnit as plugin
    if [ "$project_type" == "maven" ]; then
      echo "Maven project detected"
      # Check how many pom.xml files are in the project
      cd "${GITHUB_REPOS_DIR}/${repo_name}"
      pom_count=$(find . -name "pom.xml" | wc -l)
      # Check if the project uses JUnit as plugin to run the tests
      if grep -qE '<artifactId>junit|junit-jupiter' pom.xml || grep -qE '<groupId>junit|org.junit.jupiter' pom.xml; then
          junit_plugin=true
      else
          junit_plugin=false
      fi

      if [[ "$pom_count" -eq 1 && "$junit_plugin" == true ]]; then
        if [ ! -d "${GITHUB_REPOS_DIR}/${repo_name}/processed_libs" ]; then
          # Setup dependencies
          if [ "$resolve_deps" == "true" ]; then
            bash "${BASH_UTILS_DIR}/resolve_dependencies.sh" "${GITHUB_REPOS_DIR}/${repo_name}"
          fi
        fi
        cd "$DATASET_DIR"
        # Setup java libraries paths
        classpath="${LIB_DIR}/java8:${LIB_DIR}/java11:${LIB_DIR}/java17"
        # Generate Oracle dataset
        sdk use java "$JAVA21"
        java -jar "${DATASET_JAR}" "${repo_id}" "${GITHUB_REPOS_DIR}/${repo_name}" "${OUTPUT_MINER_DIR}/${repo_name}.json" "${OUTPUT_DIR}" "${DATASET_DIR}" "${SRC_RESOURCES_DIR}/oracles-dataset_config.json"
      fi
    else
      echo "Unprocessed project"
    fi
done < "$GITHUB_REPOS_LIST_FILE"