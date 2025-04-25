# The script run the moderation task required to replicate RQ1

# Get current directory
current_dir=$(realpath "$(dirname "${BASH_SOURCE[@]}")")
# Setup global variables
source "${current_dir}/../../scripts/utils/global_variables.sh"

"${PY_ENV}" "${RQ1_DIR}/moderator.py" \
    --input_path "${RQ1_DIR}/output/inference" \
    --output_path "${RQ1_DIR}/output/moderator" \
    --query_path "${RQ1_DIR}/queries/moderator/query-template.txt" \
    --model_list "${RQ1_MODELS_LIST}"