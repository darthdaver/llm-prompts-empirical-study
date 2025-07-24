# The script run the inference task with the vanilla llms required to replicate RQ1

# Get current directory
current_dir=$(realpath "$(dirname "${BASH_SOURCE[@]}")")
# Setup global variables and local variables
source "${current_dir}/../../scripts/utils/bash/global_variables.sh"
source "${current_dir}/../../scripts/dataset/bash/utils/local_variables.sh"
source "${current_dir}/utils/local_variables.sh"
source "${current_dir}/../../scripts/utils/bash/init_sdkman.sh"

JAVA_VERSIONS=(
    "$JAVA11"
    "$JAVA17"
    "$JAVA21"
    "$JAVA24"
    "$JAVA8"
)


process_tests() {
  local repo_name="$1"
  local config_num="$2"
  local java_version="$3"
  local input_inference_file_path="$4"
  local test_type="$5"

  # Step 2: Generate classpath for test compilation (dependencies + main classes)
  mvn dependency:build-classpath -Dmdep.outputFile=cp.txt -q
  # Add main classes to classpath
  classpath="${GITHUB_REPOS_DIR}/${repo_name}/target/classes:${GITHUB_REPOS_DIR}/${repo_name}/target/test-classes:$(cat cp.txt)"
  sdk use java "$JAVA21" > /dev/null
  output_inference_file_path="${input_inference_file_path%.csv}.txt"
  ${PY_ENV} "${PY_UTILS_DIR}/mutation_csv_map.py" "${input_inference_file_path}" "${output_inference_file_path}"
  java -jar "${MUTATION_JAR}" "${GITHUB_REPOS_DIR}/${repo_name}" "${PROMPT_DIR}/output/${config_num}/info/${file_name_info}" "${output_inference_file_path}" "${GITHUB_REPOS_DIR}/${repo_name}/star-classes" "${SDKMAN_DIR}/candidates/java/${java_version}" "${SDKMAN_DIR}/candidates/maven/${MAVEN_VERSION}" "${test_type}" "${classpath}"
}


# Add Maven bin directory to PATH temporarily
export PATH="$MAVEN_BIN:$PATH"

source "${current_dir}/../../.venv/bin/activate"

VANILLA_LLMS_LIST_FILE=${1:-$VANILLA_LLMS_LIST_FILE}

if [ ! -e "${MUTATION_JAR}" ]; then
  cd "${MUTATION_DIR}"
  sdk use java "$JAVA21"
  mvn clean package -DskipTests
fi

for model_dir_path in "${OUTPUT_DIR}/inference"/*; do
  for inference_file_path in "${model_dir_path}"/*; do
    file_name=$(basename "$inference_file_path")
    dir_name=$(basename $(dirname "$inference_file_path"))
    config_num=${dir_name##*-}
    repo_info=$("${PY_ENV}" "${PY_UTILS_DIR}/extract-repo-info.py" "${file_name}" "${GITHUB_REPOS_LIST_FILE}")
    if [ $? -eq 0 ]; then
      repo_id=$(echo "$repo_info" | cut -d' ' -f1)
      repo_name=$(echo "$repo_info" | cut -d' ' -f2)
      repo_url="${GITHUB_BASE_URL}/${repo_name}.git"
      if [ ! -d "${GITHUB_REPOS_DIR}/${repo_name}" ]; then
        # Clean modified_classes string from undesired white-spaces/line-breaks introduced with the CSV parsing
        commit_sha="${commit_sha//[$'\t\r\n ']/}"
        # Clone project
        echo "Cloning project in folder: ${GITHUB_REPOS_DIR}/${repo_name}"
        git clone "${repo_url}" "${GITHUB_REPOS_DIR}/${repo_name}"|| echo "Error cloning repository: ${repo_url}" >&2
      fi
      for version in "${JAVA_VERSIONS[@]}"; do
          sdk use java "$version"
          file_name_info=${file_name/.csv/_info.json}
          fqn_classes=$("${PY_ENV}" "${PY_UTILS_DIR}/extract-test-classes-fqn.py" "${PROMPT_DIR}/output/${config_num}/info/${file_name_info}" "${inference_file_path}")
          cd "${GITHUB_REPOS_DIR}/${repo_name}"
          src_classes=$(echo "$fqn_classes" | jq -r '.classes | @sh' | tr -d \')
          test_classes=$(echo "$fqn_classes" | jq -r '.test_classes | @sh' | tr -d \')
          echo "Source classes: [ ${src_classes} ]"
          echo "Test classes: [ ${test_classes} ]"
          if [ -d "${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/${config_num}" ]; then
              rm -rf "${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/${config_num}"
          fi
          mkdir -p "${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/${config_num}/original/pit-reports"
          echo "Compiling original project"
          find "${GITHUB_REPOS_DIR}/${repo_name}" -type f -name '*_STAR_Split_inference.java' -delete
          find "${GITHUB_REPOS_DIR}/${repo_name}" -type f -name '*_STAR_Split_no_oracle.java' -delete
          echo "Starting compiling..."
          mvn clean install -DskipTests -Dgpg.skip=true -Dspotless.check.skip=true -Dmaven.compiler.failOnWarning=false -Dspotless.check.skip=true #> /dev/null 2>&1
          if [ $? -eq 0 ]; then
            echo "Compilation successful for ${repo_name} with Java version ${version}"
          else
            echo "Compilation failed for ${repo_name} with Java version ${version}"
            continue
          fi
          echo "Performing mutation testing on original test classes of project (removing target oracles)"
          if [ ${version} == "$JAVA8" ]; then
              # Pitest works with Java 11-17
              version="$JAVA11"
          fi
          if [ ${version} == "$JAVA21" ]; then
              # Pitest works with Java 11-17
              version="$JAVA11"
          fi
          if [ ${version} == "$JAVA24" ]; then
              # Pitest works with Java 11-17
              version="$JAVA11"
          fi
          sdk use java "$version"
          POM_FILE="${GITHUB_REPOS_DIR}/${repo_name}/pom.xml"
          TEST_PLUGIN="junit" # default to junit4
          if grep -q "org.junit.jupiter" "$POM_FILE"; then
            TEST_PLUGIN="junit5"
            if [ ! -d "${RQ1_DIR}/mvn-pitest" ]; then
              mkdir -p "${RQ1_DIR}/mvn-pitest"
              mvn dependency:get -Dartifact=org.pitest:pitest-junit5-plugin:1.1.2
              mvn dependency:copy -Dartifact=org.pitest:pitest-junit5-plugin:1.1.2 -DoutputDirectory="${RQ1_DIR}/mvn-pitest"
            fi
          elif grep -q "junit:junit" "$POM_FILE"; then
            TEST_PLUGIN="junit"
          fi
          echo "Detected PIT test plugin: $TEST_PLUGIN"
          process_tests \
            "$repo_name" \
            "$config_num" \
            "$version" \
            "$inference_file_path" \
            "no_oracle"
          no_oracle_tests=$(head -n 1 "${GITHUB_REPOS_DIR}/${repo_name}/NO_ORACLE_classes_processed.csv" | tr -d '\r\n')
          echo "Source classes: [ ${src_classes} ]"
          echo "Test classes: [ ${no_oracle_tests} ]"
          mvn org.pitest:pitest-maven:mutationCoverage \
                  -DjvmArgs="--add-opens=java.base/java.lang=ALL-UNNAMED,\
                              --add-opens=java.base/java.lang.reflect=ALL-UNNAMED" \
                  -DtargetClasses="${src_classes}" \
                  -DtargetTests="${no_oracle_tests}" \
                  -DoutputFormats=HTML,XML,CSV \
                  -DfullMutationMatrix \
                  -Dmutators=ALL \
                  -DreportDir="${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/original/pit-reports"

          if [ ! -d "${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/${config_num}/inference/pit-reports" ]; then
              mkdir -p "${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/${config_num}/original/pit-reports"
          fi

          mv "${GITHUB_REPOS_DIR}/${repo_name}/target/pit-reports"/* "${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/${config_num}/original/pit-reports"
          rm -r "${GITHUB_REPOS_DIR}/${repo_name}/star-classes"

          process_tests \
            "$repo_name" \
            "$config_num" \
            "$version" \
            "$inference_file_path" \
            "inference"
          inference_tests=$(head -n 1 "${GITHUB_REPOS_DIR}/${repo_name}/INFERENCE_classes_processed.csv" | tr -d '\r\n')
          echo "Source classes: [ ${src_classes} ]"
          echo "Test classes: [ ${inference_tests} ]"
          mvn org.pitest:pitest-maven:mutationCoverage \
                  -DjvmArgs="--add-opens=java.base/java.lang=ALL-UNNAMED,\
                              --add-opens=java.base/java.lang.reflect=ALL-UNNAMED" \
                  -DtargetClasses="${src_classes}" \
                  -DtargetTests="${inference_tests}" \
                  -DoutputFormats=HTML,XML,CSV \
                  -DfullMutationMatrix \
                  -Dmutators=ALL \
                  -DreportDir="${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/inference/pit-reports"

          if [ ! -d "${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/${config_num}/inference/pit-reports" ]; then
              mkdir -p "${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/${config_num}/inference/pit-reports"
          fi

          mv "${GITHUB_REPOS_DIR}/${repo_name}/target/pit-reports"/* "${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/${config_num}/inference/pit-reports"
          rm -r "${GITHUB_REPOS_DIR}/${repo_name}/star-classes"
          break
      done
    else
      echo "Repo not found from file_name: ${file_name}"
    fi
  done
done
