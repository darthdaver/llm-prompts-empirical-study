#!/bin/bash
# This script install the requirements to run the experiments
# using an isolated version of sdkman. Sdkman let to easily
# manage different versions of Java JDK required by the tools
# used for the experiments. The script assumes zip and unzip
# packages are installed in the machine.

# Get current directory
current_dir=$(realpath "$(dirname "$BASH_SOURCE")")
# Setup global and local variables
source "${current_dir}/global_variables.sh"

# Download sdkman
bash "${current_dir}/install_sdkman.sh"
source "${current_dir}/init_sdkman.sh"

# Install Java 8
yes N | sdk install java "$JAVA8"
# Install Java 11
yes N | sdk install java "$JAVA11"
# Install Java 17
yes N | sdk install java "$JAVA17"
# Install Java 21
yes N | sdk install java "$JAVA21"
# Install maven
sdk install maven "$MAVEN_VERSION"