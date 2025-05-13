# Directories
ROOT_DIR=$(dirname "$(dirname "$(dirname "$(realpath "$(dirname "$BASH_SOURCE")")")")")
DATASET_DIR="${ROOT_DIR}/scripts/dataset"
PROMPT_DIR="${ROOT_DIR}/scripts/prompt"
TEST_MINER_DIR="${ROOT_DIR}/scripts/test-miner"
UTILS_DIR="${ROOT_DIR}/scripts/utils"
RQ1_DIR="${ROOT_DIR}/_rq1"
INPUT_DIR="${ROOT_DIR}/input"
GITHUB_REPOS_DIR="${INPUT_DIR}/github-repos"
RESOURCES_DIR="${ROOT_DIR}/resources"
BASH_RESOURCES_DIR="${UTILS_DIR}/bash/resources"
SDKMAN_DIR="${UTILS_DIR}/resources/sdkman"

# Sdkman Java versions
JAVA8="8.0.392-amzn"
JAVA11="11.0.21-amzn"
JAVA17="17.0.8-oracle"
JAVA21="21.0.6-amzn"

# Sdkman Maven version
MAVEN_VERSION="3.9.4"

# Bin
MAVEN_BIN="${SDKMAN_DIR}/candidates/maven/${MAVEN_VERSION}/bin"



# Python environment
if [ -e "/path/to/something" ]; then
    PY_ENV="${ROOT_DIR}/.venv/bin/python"
else
    PY_ENV="python"
fi

# Files
GITHUB_REPOS_LIST_FILE="${ROOT_DIR}/resources/seart-github-repos.csv"
VANILLA_LLMS_LIST_FILE="${ROOT_DIR}/resources/vanilla_llms.csv"

# Links
GITHUB_BASE_URL="https://github.com"