# Directories
ROOT_DIR=$(dirname "$(dirname "$(dirname "$(realpath "$(dirname "$BASH_SOURCE")")")")")
DATASET_DIR="${ROOT_DIR}/scripts/dataset"
PROMPT_DIR="${ROOT_DIR}/scripts/prompt"
TEST_MINER_DIR="${ROOT_DIR}/scripts/test-miner"
UTILS_DIR="${ROOT_DIR}/scripts/utils"
RQ1_DIR="${ROOT_DIR}/_rq1"
INPUT_DIR="${ROOT_DIR}/input"
GITHUB_REPOS_DIR="${INPUT_DIR}/github-repos"

# Python environment
PY_ENV="${ROOT_DIR}/.venv/bin/python"

# Files
GITHUB_REPOS_LIST_FILE="${ROOT_DIR}/resources/github-repos.csv"
VANILLA_LLMS_LIST="${ROOT_DIR}/resources/vanilla_llms.csv"

# Links
GITHUB_BASE_URL="https://github.com"