# The script run the inference task with the vanilla llms required to replicate RQ1

# Get current directory
current_dir=$(realpath "$(dirname "${BASH_SOURCE[@]}")")
# Setup global variables and local variables
source "${current_dir}/../../scripts/utils/bash/global_variables.sh"
source "${current_dir}/utils/local_variables.sh"

"${PY_ENV}" "${RQ1_DIR}/preliminary_prompt.py" \
    ${OUTPUT_PROMPT_DIR}/1/prompt \
    ${OUTPUT_DIR}