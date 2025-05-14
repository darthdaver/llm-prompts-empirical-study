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

JAVA_VERSIONS=(
    "$JAVA11"
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

        #"${PY_ENV}" "${PY_UTILS_DIR}/add-pit-plugin.py" "${GITHUB_REPOS_DIR}/${repo_name}"

        for version in "${JAVA_VERSIONS[@]}"; do
            sdk use java "$version"
            file_name_info=${file_name/.csv/_info.json}
            out_class_paths=$("${PY_ENV}" "${PY_UTILS_DIR}/extract-test-classes-fqn.py" "${PROMPT_DIR}/output/${config_num}/info/${file_name_info}" "${inference_file_path}")
            #"${PY_ENV}" "${PY_UTILS_DIR}/add-pit-plugin.py" "${GITHUB_REPOS_DIR}/${repo_name}"
            cd "${GITHUB_REPOS_DIR}/${repo_name}"
            class_paths=$(echo "$out_class_paths" | jq -r '.classes | @sh' | tr -d \')
            test_class_paths=$(echo "$out_class_paths" | jq -r '.test_classes | @sh' | tr -d \')
            echo "${class_paths}"
            echo "${test_class_paths}"
            #mvn clean install
            echo "${OUTPUT_DIR}/mutation/pit-reports"
            if [ ! -d "${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/original/pit-reports" ]; then
                mkdir -p "${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/original/pit-reports"
            fi
            #mvn org.pitest:pitest-maven:mutationCoverage \
            #    -DtargetClasses="${class_paths}" \
            #    -DtargetTests="${test_class_paths}" \
            #    -DoutputFormats=HTML,XML,CSV \
            #    -DjunitIgnoreFailures=true \
            #    -Djunit5IgnoreFailures=true \
            #    -Dmutators=ALL \
            #    -DreportDir="${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/original/pit-reports"

            #mv "${GITHUB_REPOS_DIR}/${repo_name}/target/pit-reports"/* "${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/original/pit-reports"

            #if [ $? -eq 0 ]; then
                cd "$MUTATION_DIR"
                # Inset Oracles
                sdk use java "$JAVA21"
                java -jar "${MUTATION_JAR}" "${GITHUB_REPOS_DIR}/${repo_name}" "${PROMPT_DIR}/output/${config_num}/info/${file_name_info}" "${inference_file_path}" "${GITHUB_REPOS_DIR}/${repo_name}/star-classes"

                #mutation_test_class_paths=()
                #for i in "${class_paths[@]}"; do
                #    mutation_test_class_paths+=("${i}_inference")
                #done

                #mutation_test_class_paths=("com.twilio.http.RequestTest_STAR_Split_inference" "com.twilio.http.NetworkHttpClientTest_STAR_Split_inference")
                #class_paths=("com.twilio.http.Request" "com.twilio.http.NetworkHttpClient")
                #echo "${mutation_test_class_paths[@]}"
                cd "${GITHUB_REPOS_DIR}/${repo_name}"
                sdk use java "$version"
                mvn clean install

                # Initial list of test classes (fully qualified names)
                failing_tests=()

                # Step 1: Compile only the main sources (skip tests completely)
                # mvn clean compile -Dmaven.test.skip=true

                # Step 2: Generate classpath for test compilation (dependencies + main classes)
                mvn dependency:build-classpath -Dmdep.outputFile=cp.txt -q

                # Add main classes to classpath
                classpath="${GITHUB_REPOS_DIR}/${repo_name}/target/classes:${GITHUB_REPOS_DIR}/${repo_name}/target/test-classes:$(cat cp.txt)"

                # Step 3: Iterate over each test class and try to compile it using javac
                while IFS=, read -r temp_path dest_path test_class_fqn ; do
                    test_class_fqn="${test_class_fqn//[$'\t\r\n ']/}"
                    echo "Checking test: $test_class_fqn"
                    # Convert fully qualified class name to file path
                    class_path=$(echo "$test_class_fqn" | tr '.' '/')".java"

                    cp "${temp_path}" "${dest_path}"

                    if [ ! -f "$dest_path" ]; then
                        echo "⚠️  Test file not found: $test_file"
                        failing_tests+=("$test_class_fqn")
                        continue
                    fi
                    # Compile the test class
                    javac -cp "${classpath}" -d target/test-classes "$test_file"
                    if [ $? -ne 0 ]; then
                        echo "❌ Compilation failed for: $test_class_fqn"
                        failing_tests+=("$test_class_fqn")
                        rm "${dest_path}"
                    else
                        echo "✅ Compilation succeeded for: $test_class_fqn"
                    fi
                done
                # Step 4: Report failing test classes
                echo ""
                if [ ${#failing_tests[@]} -ne 0 ]; then
                    echo "🚫 The following test classes failed to compile:"
                    for test in "${failing_tests[@]}"; do
                        echo " - $test"
                    done
                else
                    echo "🎉 All test classes compiled successfully."
                fi
#                if [ ! -d "${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/inference/pit-reports" ]; then
#                    mkdir -p "${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/inference/pit-reports"
#                fi
#                mvn org.pitest:pitest-maven:mutationCoverage \
#                  -DtargetClasses="${class_paths}" \
#                  -DtargetTests="${mutation_test_class_paths}" \
#                  -DoutputFormats=HTML,XML,CSV \
#                  -DjunitIgnoreFailures=true \
#                  -Djunit5IgnoreFailures=true \
#                  -Dmutators=ALL \
#                  -DreportDir="${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/inference/pit-reports" \
#                  -Dverbose=true
#                mv "${GITHUB_REPOS_DIR}/${repo_name}/target/pit-reports"/* "${OUTPUT_DIR}/mutation/${dir_name}/${repo_id}/inference/pit-reports"
#                break
            #fi
            
        done
      else
        echo "Repo not found from file_name: ${file_name}"
      fi
    done
done