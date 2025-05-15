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

# Add Maven bin directory to PATH temporarily
export PATH="$MAVEN_BIN:$PATH"

source "${current_dir}/../../.venv/bin/activate"

VANILLA_LLMS_LIST_FILE=${1:-$VANILLA_LLMS_LIST_FILE}

if [ ! -e "${MUTATION_JAR}" ]; then
  cd "${MUTATION_DIR}"
  sdk use java "$JAVA21"
  mvn clean package -DskipTests
fi

for model_dir_path in "${OUTPUT_DIR}/inference"/*/; do
    model_dir_path="${model_dir_path%/}"
    for inference_file_path in "${model_dir_path}"/*; do
      file_name=$(basename "$inference_file_path")
      dir_name=$(basename $(dirname "$inference_file_path"))
      config_num=${dir_name##*-}
      repo_info=$("${PY_ENV}" "${PY_UTILS_DIR}/extract-repo-info.py" "${file_name}" "${GITHUB_REPOS_LIST_FILE}")
      if [ $? -eq 0 ]; then
        repo_id=$(echo "$repo_info" | cut -d' ' -f1)
        repo_name=$(echo "$repo_info" | cut -d' ' -f2)

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
            mvn clean install -DskipTests > /dev/null 2>&1
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
            mvn org.pitest:pitest-maven:mutationCoverage \
                -DtargetClasses="${class_paths}" \
                -DtargetTests="${test_class_paths}" \
                -DoutputFormats=HTML,XML,CSV \
                -DjunitIgnoreFailures=true \
                -Djunit5IgnoreFailures=true \
                -Dmutators=ALL \
                -DreportDir="${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/original/pit-reports"
            mv "${GITHUB_REPOS_DIR}/${repo_name}/target/pit-reports"/* "${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/${config_num}/original/pit-reports"
            if [ $? -eq 0 ]; then
                cd "$MUTATION_DIR"
                # Inset Oracles
                echo "Inserting inferred oracles in test cases"
                sdk use java "$JAVA21"
                java -jar "${MUTATION_JAR}" "${GITHUB_REPOS_DIR}/${repo_name}" "${PROMPT_DIR}/output/${config_num}/info/${file_name_info}" "${inference_file_path}" "${GITHUB_REPOS_DIR}/${repo_name}/star-classes"
                cd "${GITHUB_REPOS_DIR}/${repo_name}"
                sdk use java "$version"
                # Initial list of test classes (fully qualified names)
                successfully_compiled_tests=()
                successfully_compiled_tests_csv=()
                failing_tests=()
                failing_tests_csv=()
                # Step 2: Generate classpath for test compilation (dependencies + main classes)
                mvn dependency:build-classpath -Dmdep.outputFile=cp.txt -q
                # Add main classes to classpath
                classpath="${GITHUB_REPOS_DIR}/${repo_name}/target/classes:${GITHUB_REPOS_DIR}/${repo_name}/target/test-classes:$(cat cp.txt)"
                # Step 3: Iterate over each test class and try to compile it using javac
                while IFS=, read -r temp_path dest_path test_class_fqn inference_id ; do
                    inference_id="${inference_id//[$'\t\r\n ']/}"
                    echo "Checking test: $test_class_fqn"
                    echo "Copying test ${test_class_fqn} into project"
                    cp "${temp_path}" "${dest_path}"
                    if [ ! -f "$dest_path" ]; then
                        echo "⚠️  Test file not found: $dest_path"
                        failing_tests+=("$test_class_fqn")
                        continue
                    fi
                    # Compile the test class
                    echo "Compilation of test class: $test_class_fqn"
                    javac -cp "${classpath}" -d target/test-classes "$dest_path"
                    if [ $? -ne 0 ]; then
                        echo "❌ Compilation failed for: $test_class_fqn"
                        failing_tests+=("${test_class_fqn}")
                        failing_tests_csv+=("${test_class_fqn},${inference_id}")
                        rm "${dest_path}"
                    else
                        echo "✅ Compilation succeeded for: $test_class_fqn"
                        test_class_fqn_already_present=false
                        inference_id_already_present=false
                        for item in "${successfully_compiled_tests[@]}"; do
                          if [[ "$item" == "${test_class_fqn}" ]]; then
                            test_class_fqn_already_present=true
                            break
                          fi
                        done
                        for item in "${successfully_compiled_tests_csv[@]}"; do
                          if [[ "$item" == "${test_class_fqn},${inference_id}" ]]; then
                            inference_id_already_present=true
                            break
                          fi
                        done
                        # Add only if not already in list
                        if ! $test_class_fqn_already_present; then
                          successfully_compiled_tests+=("${test_class_fqn}")
                        fi
                        if ! $inference_id_already_present; then
                          successfully_compiled_tests_csv+=("${test_class_fqn},${inference_id}")
                        fi
                    fi
                done < "${GITHUB_REPOS_DIR}/${repo_name}/star_classes_mapping.csv"

                if [ ! -d "${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/${config_num}/inference/compilation" ]; then
                    mkdir -p "${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/${config_num}/inference/compilation"
                fi

                printf "%s\n" "${successfully_compiled_tests_csv[@]}" > "${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/${config_num}/inference/compilation/successfully_compiled_tests.csv"
                printf "%s\n" "${failing_tests_csv[@]}" > "${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/${config_num}/inference/compilation/failing_tests.csv"

                # Step 4: Report failing test classes
                if [ ${#failing_tests[@]} -ne 0 ]; then
                    echo "🚫 The following test classes failed to compile:"
                    for test in "${failing_tests[@]}"; do
                        echo " - $test"
                    done
                else
                    echo "🎉 All test classes compiled successfully."
                fi
                if [ ! -d "${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/${config_num}/inference/pit-reports" ]; then
                    mkdir -p "${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/${config_num}/inference/pit-reports"
                fi

                # Step 5: Run mutation testing on the modified project
                IFS=,; successfully_compiled_tests_joined="${successfully_compiled_tests[*]}"

                echo "Performing mutation testing on inferred project"
                echo "Source classes: [ ${class_paths} ]"
                echo "Test classes: [ ${successfully_compiled_tests_joined} ]"
                mvn org.pitest:pitest-maven:mutationCoverage \
                  -DtargetClasses="${class_paths}" \
                  -DtargetTests="${successfully_compiled_tests}" \
                  -DoutputFormats=HTML,XML,CSV \
                  -DjunitIgnoreFailures=true \
                  -Djunit5IgnoreFailures=true \
                  -Dmutators=ALL \
                  -DreportDir="${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/inference/pit-reports" \
                  -Dverbose=true
                mv "${GITHUB_REPOS_DIR}/${repo_name}/target/pit-reports"/* "${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/${config_num}/inference/pit-reports"
                break
            fi
        done
      else
        echo "Repo not found from file_name: ${file_name}"
      fi
    done
done