#!/bin/bash

# Get current directory
current_dir=$(realpath "$(dirname "${BASH_SOURCE[@]}")")
# Setup global and local variables
source "${current_dir}/../../../../scripts/utils/bash/global_variables.sh"
source "${current_dir}/local_variables.sh"
source "${BASH_UTILS_DIR}/init_sdkman.sh"

# Add Maven bin directory to PATH temporarily
export PATH="$MAVEN_BIN:$PATH"

main_folder="$1"
maven_default_repo="https://repo1.maven.org/maven2"

# Function to check if a folder is a Maven project
check_maven() {
    [[ -f "pom.xml" ]] && return 0 || return 1
}

echo "Resolving dependencies for: $main_folder"
# Main script execution
if [[ -d "$main_folder" ]]; then
    cd "$main_folder" || exit
    global_failed_file="${main_folder}/global_failed_downloads.txt"
    global_success_file="${main_folder}/global_successful_downloads.txt"
    if [ -f "$global_failed_file" ]; then
        # Remove the file
        rm "$global_failed_file"
    fi
    if [ -f "$global_success_file" ]; then
        # Remove the file
        rm "$global_success_file"
    fi
    if check_maven; then
      project_poms_analysis=$("${PY_ENV}" "${PY_UTILS_DIR}/resolve_dependency.py" "$main_folder" 2>/dev/null)
      # Initialize an empty array to store the pom paths
      pom_paths=()
      # Read keys into the array
      while IFS= read -r key; do
          pom_paths+=("$key")
      done < <(echo "$project_poms_analysis" | jq -r 'keys[]')
      for pom_path in "${pom_paths[@]}"; do
          echo "Processing: $pom_path"
          pom_dir=$(dirname $pom_path)
          output_dir="$(realpath "$pom_dir")/processed_libs"
          decompiled_dir="$output_dir/decompiled"
          # Convert dependencies to an array
          mapfile -t dependencies < <(echo "$project_poms_analysis" | jq -r --arg key "${pom_path}" '.[$key][0][]')
          mapfile -t repositories < <(echo "$project_poms_analysis" | jq -r --arg key "${pom_path}" '.[$key][1][]')
          # Debug: Print parsed arrays
          echo "Dependencies:"
          echo "${dependencies[@]}"
          echo "Repositories:"
          echo "${repositories[@]}"

          repositories=("$maven_default_repo" "${repositories[@]}")
          local_failed_file="${pom_dir}/local_failed_downloads.txt"
          local_success_file="${pom_dir}/local_successful_downloads.txt"
          if [ -f "$local_failed_file" ]; then
              # Remove the file
              rm "$local_failed_file"
          fi
          if [ -f "$local_success_file" ]; then
              # Remove the file
              rm "$local_success_file"
          fi
          if [ -d "$output_dir" ]; then
              rm -r "$output_dir"
          fi
          mkdir -p "$decompiled_dir"
          if [ ${#dependencies[@]} -gt 0 ]; then
            for dep in "${dependencies[@]}"; do
                echo "Processing dependency: $dep"
                IFS=':' read -r group_id artifact_id version <<< "$dep"
                for repo in "${repositories[@]}"; do
                  echo "Trying to download source JAR of '$group_id:$artifact_id:$version' from '$repo'"
                  if mvn dependency:get \
                    -Dartifact="$group_id:$artifact_id:$version:jar:sources" \
                    -DoutputDirectory="$output_dir" \
                    -DremoteRepositories="$repo" \
                    -Dpackaging=jar \
                    -Dmaven.artifact.threads=1 \
                    -Doverwrite=true;
                  then
                    echo "Downloaded: $group_id:$artifact_id:$version from $repo"
                    if [ ! -d "$output_dir/sources" ]; then
                        mkdir "$output_dir/sources"
                    fi
                    mvn dependency:copy -Dartifact="$group_id:$artifact_id:$version:jar:sources" -DoutputDirectory="$output_dir/sources" -Dclassifier=sources -Doverwrite=true
                    echo "$group_id:$artifact_id:$version,sources" >> "$local_success_file"
                    echo "$pom_dir,$group_id:$artifact_id:$version,sources" >> "$global_success_file"
                    break
                  else
                      echo "Trying to download classes JAR of '$group_id:$artifact_id:$version' from '$repo'"
                      if mvn dependency:get \
                        -Dartifact="$group_id:$artifact_id:$version" \
                        -DoutputDirectory="$output_dir/classes" \
                        -DremoteRepositories="$repo" \
                        -Dmaven.artifact.threads=1;
                      then
                        echo "Downloaded: $group_id:$artifact_id:$version from $repo"
                        if [ ! -d "$output_dir/classes" ]; then
                            mkdir "$output_dir/classes"
                        fi
                        mvn dependency:copy -Dartifact="$group_id:$artifact_id:$version" -DoutputDirectory="$output_dir/classes" -Doverwrite=true
                        echo "$group_id:$artifact_id:$version,classes" >> "$local_success_file"
                        echo "$pom_dir,$group_id:$artifact_id:$version,classes" >> "$global_success_file"
                        break
                      else
                          echo "$group_id:$artifact_id:$version,download-failed" >> "$local_failed_file"
                          echo "$pom_dir,$group_id:$artifact_id:$version,download-failed" >> "$global_failed_file"
                          echo "Error: Failed to download both source and classes JAR for $group_id:$artifact_id:$version"
                      fi
                  fi
                done
            done
            # Iterate over each ZIP file in the source directory
            for jar_file in "$output_dir/sources"/*.jar; do
                if [[ -f "$jar_file" ]]; then
                  # Extract the base name of the ZIP file (without extension)
                  base_name=$(basename "$jar_file" .jar)
                  # Create a destination folder for the extracted files
                  destination_folder="$decompiled_dir/$base_name"
                  mkdir -p "$destination_folder"
                  # Unzip the file into the destination folder
                  unzip -oq "$jar_file" -d "$destination_folder"
                  echo "Unzipped $jar_file to $destination_folder"
                fi
            done
            # Iterate over each ZIP file in the classes directory
            for jar_file in "$output_dir/classes"/*.jar; do
                if [[ -f "$jar_file" ]]; then
                  # Extract the base name of the ZIP file (without extension)
                  base_name=$(basename "$jar_file" .jar)
                  # Create a destination folder for the extracted files
                  destination_folder="$decompiled_dir/$base_name"
                  mkdir -p "$destination_folder"
                  sdk use java "$JAVA21"
                  java -jar $FERNFLOWER_JAR $jar_file $destination_folder
                  # Unzip the file into the destination folder
                  unzip -oq "$destination_folder/$base_name.jar" -d "$destination_folder"
                  rm "$destination_folder/$base_name.jar"
                  echo "Unzipped $jar_file to $destination_folder"
                fi
            done
          fi
      done
      exit 0
    elif check_gradle; then
        echo "Gradle project skipped"
        exit 1
    elif check_ant; then
        echo "Ant project skipped."
        exit 1
    else
        echo "This folder is not a recognized Maven, Gradle, or Ant project."
        exit 1
    fi
else
    echo "Usage: $0 <directory>"
    exit 1
fi
