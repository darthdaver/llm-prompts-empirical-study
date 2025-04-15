# Directories
ROOT_DIR=$(dirname "$(dirname "$(dirname "$(realpath "$(dirname "$BASH_SOURCE")")")")")
TEST_MINER_DIR="${ROOT_DIR}/scripts/test-miner"
PROMPT_DIR="${ROOT_DIR}/scripts/prompt"
UTILS_DIR="${ROOT_DIR}/scripts/utils"
INPUT_DIR="${ROOT_DIR}/input"
GITHUB_REPOS_DIR="${INPUT_DIR}/github-repos"

# Links
GITHUB_BASE_URL="https://github.com"