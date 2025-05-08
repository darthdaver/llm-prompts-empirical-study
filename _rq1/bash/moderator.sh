# The script run the moderation task required to replicate RQ1

# Get current directory
current_dir=$(realpath "$(dirname "${BASH_SOURCE[@]}")")
# Setup global variables and local variables
source "${current_dir}/../../scripts/utils/bash/global_variables.sh"
source "${current_dir}/utils/local_variables.sh"

"${PY_ENV}" "${RQ1_DIR}/moderator.py" \
    --input_path "${OUTPUT_DIR}/inference" \
    --output_path "${OUTPUT_DIR}/moderator" \
    --query_path "${QUERIES_DIR}/moderator/query-template.txt" \
    --model_list "${VANILLA_LLMS_LIST_FILE}"