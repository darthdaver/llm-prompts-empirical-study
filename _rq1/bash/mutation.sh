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
  local csv_file="$1"
  local success_csv="$2"
  local fail_csv="$3"
  local dir_name="$4"
  local repo_name="$5"
  local repo_id="$6"
  local config_num="$7"
  local suffix="$8"

  local successfully_compiled_tests=()
  local successfully_compiled_tests_csv=()
  local failing_tests=()
  local failing_tests_csv=()

  # Step 2: Generate classpath for test compilation (dependencies + main classes)
  mvn dependency:build-classpath -Dmdep.outputFile=cp.txt -q
  # Add main classes to classpath
  classpath="${GITHUB_REPOS_DIR}/${repo_name}/target/classes:${GITHUB_REPOS_DIR}/${repo_name}/target/test-classes:$(cat cp.txt)"

  while IFS=, read -r temp_path dest_path test_class_fqn inference_id ; do
    inference_id="${inference_id//[$'\t\r\n ']/}"
    echo "Checking test: ${test_class_fqn}_${suffix}" >&2
    echo "Copying test ${test_class_fqn}_${suffix} into project" >&2
    cp "${temp_path}" "${dest_path}"

    if [ ! -f "$dest_path" ]; then
      echo "⚠️  Test file not found: $dest_path" >&2
      failing_tests+=("${test_class_fqn}_${suffix}")
      continue
    fi

    echo "Compilation of test class: ${test_class_fqn}_${suffix}" >&2
    javac -cp "${classpath}" -d target/test-classes "$dest_path"
    if [ $? -ne 0 ]; then
      echo "❌ Compilation failed for: ${test_class_fqn}_${suffix}" >&2
      failing_tests+=("${test_class_fqn}_${suffix}")
      failing_tests_csv+=("${test_class_fqn}_${suffix},${inference_id}")
      rm "${dest_path}"
    else
      echo "✅ Compilation succeeded for: ${test_class_fqn}_${suffix}" >&2
      test_class_fqn_already_present=false
      inference_id_already_present=false

      for item in "${successfully_compiled_tests[@]}"; do
        if [[ "$item" == "${test_class_fqn}_${suffix}" ]]; then
          test_class_fqn_already_present=true
          break
        fi
      done

      for item in "${successfully_compiled_tests_csv[@]}"; do
        if [[ "$item" == "${test_class_fqn}_${suffix},${inference_id}" ]]; then
          inference_id_already_present=true
          break
        fi
      done

      if ! $test_class_fqn_already_present; then
        successfully_compiled_tests+=("${test_class_fqn}_${suffix}")
      fi
      if ! $inference_id_already_present; then
        successfully_compiled_tests_csv+=("${test_class_fqn}_${suffix},${inference_id}")
      fi
    fi
  done < "${csv_file}"


  if [ ! -d "${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/${config_num}/original/compilation" ]; then
    mkdir -p "${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/${config_num}/original/compilation"
  fi
  printf "%s\n" "${successfully_compiled_tests_csv[@]}" > "${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/${config_num}/original/compilation/${success_csv}"
  printf "%s\n" "${failing_tests_csv[@]}" > "${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/${config_num}/original/compilation/${fail_csv}"
  echo "${successfully_compiled_tests[@]}"
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

model_dir_path="${OUTPUT_DIR}/inference/phi4_14b-1"
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
        out_class_paths=$("${PY_ENV}" "${PY_UTILS_DIR}/extract-test-classes-fqn.py" "${PROMPT_DIR}/output/${config_num}/info/${file_name_info}" "${inference_file_path}")
        cd "${GITHUB_REPOS_DIR}/${repo_name}"
        class_paths=$(echo "$out_class_paths" | jq -r '.classes | @sh' | tr -d \')
        test_class_paths=$(echo "$out_class_paths" | jq -r '.test_classes | @sh' | tr -d \')
        echo "Source classes: [ ${class_paths} ]"
        echo "Test classes: [ ${test_class_paths} ]"
        if [ -d "${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/${config_num}" ]; then
            rm -rf "${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/${config_num}"
        fi
        mkdir -p "${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/${config_num}/original/pit-reports"
        echo "Compiling original project"
        find "${GITHUB_REPOS_DIR}/${repo_name}" -type f -name '*_STAR_Split_inference.java' -delete
        echo "Starting compiling..."
        #mvn clean install -DskipTests > /dev/null 2>&1
        #mvn clean install -DskipTests -Dgpg.skip=true -Dspotless.check.skip=true #> /dev/null 2>&1
        if [ $? -eq 0 ]; then
          echo "Compilation successful for ${repo_name} with Java version ${version}"
        else
          echo "Compilation failed for ${repo_name} with Java version ${version}"
          continue
        fi
        echo "Performing mutation testing on original project"
        if [ ${version} == "$JAVA8" ]; then
            # Pitest works with Java 11 or higher
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

        # sdk use java "$JAVA21"
        # java -jar "${MUTATION_JAR}" "${GITHUB_REPOS_DIR}/${repo_name}" "${PROMPT_DIR}/output/${config_num}/info/${file_name_info}" "${inference_file_path}" "${GITHUB_REPOS_DIR}/${repo_name}/star-classes" "no_oracle"

        # result=$(process_tests \
        #   "${GITHUB_REPOS_DIR}/${repo_name}/star_classes_mapping.csv" \
        #   "no_oracle_successfully_compiled_tests.csv" \
        #   "no_oracle_failing_tests.csv" \
        #   "$dir_name" \
        #   "$repo_name" \
        #   "$repo_id" \
        #   "$config_num" \
        #   "no_oracle"
        # )
        # # Reconstruct the array
        # IFS=' ' read -r -a no_oracle_tests_array <<< "$result"
        # IFS=',' no_oracle_tests_str="${no_oracle_tests_array[*]}"
        
        # rm "${GITHUB_REPOS_DIR}/${repo_name}/star_classes_mapping.csv"
        # rm "${GITHUB_REPOS_DIR}/${repo_name}/cp.txt"

        # echo "Source classes: [ ${class_paths} ]"
        # echo "Test classes: [ ${no_oracle_tests_str} ]"

        # mvn org.pitest:pitest-maven:mutationCoverage \
        #         -DjvmArgs="--add-opens=java.base/java.lang=ALL-UNNAMED,\
        #                     --add-opens=java.base/java.lang.reflect=ALL-UNNAMED" \
        #         -DtargetClasses="${class_paths}" \
        #         -DtargetTests="${no_oracle_tests_str}" \
        #         -DoutputFormats=HTML,XML,CSV \
        #         -DfullMutationMatrix \
        #         -Dmutators=ALL \
        #         -DreportDir="${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/original/pit-reports"

        # if [ ! -d "${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/${config_num}/inference/pit-reports" ]; then
        #     mkdir -p "${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/${config_num}/original/pit-reports"
        # fi

        # mv "${GITHUB_REPOS_DIR}/${repo_name}/target/pit-reports"/* "${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/${config_num}/original/pit-reports"

        # rm -r "${GITHUB_REPOS_DIR}/${repo_name}/star-classes" 
        
        sdk use java "$JAVA21"
        java -jar "${MUTATION_JAR}" "${GITHUB_REPOS_DIR}/${repo_name}" "${PROMPT_DIR}/output/${config_num}/info/${file_name_info}" "${inference_file_path}" "${GITHUB_REPOS_DIR}/${repo_name}/star-classes" "inference"

        result=$(process_tests \
          "${GITHUB_REPOS_DIR}/${repo_name}/star_classes_mapping.csv" \
          "inference_successfully_compiled_tests.csv" \
          "inference_failing_tests.csv" \
          "$dir_name" \
          "$repo_name" \
          "$repo_id" \
          "$config_num" \
          "inference"
        )
        # Reconstruct the array
        IFS=' ' read -r -a inference_tests_array <<< "$result"
        IFS=',' inference_tests_str="${inference_tests_array[*]}"
        
        rm "${GITHUB_REPOS_DIR}/${repo_name}/star_classes_mapping.csv"
        rm "${GITHUB_REPOS_DIR}/${repo_name}/cp.txt"

        echo "Source classes: [ ${class_paths} ]"
        echo "Test classes: [ ${inference_tests_str} ]"

        mvn org.pitest:pitest-maven:mutationCoverage \
                -DjvmArgs="--add-opens=java.base/java.lang=ALL-UNNAMED,\
                            --add-opens=java.base/java.lang.reflect=ALL-UNNAMED" \
                -DtargetClasses="${class_paths}" \
                -DtargetTests="${inference_tests_str}" \
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
    break
  else
    echo "Repo not found from file_name: ${file_name}"
  fi
done
