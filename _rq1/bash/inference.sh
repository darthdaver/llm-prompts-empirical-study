# The script run the inference task with the vanilla llms required to replicate RQ1

# Get current directory
current_dir=$(realpath "$(dirname "${BASH_SOURCE[@]}")")
# Setup global variables and local variables
source "${current_dir}/../../scripts/utils/bash/global_variables.sh"
source "${current_dir}/utils/local_variables.sh"

VANILLA_LLMS_LIST_FILE=${1:-$VANILLA_LLMS_LIST_FILE}

while IFS=, read -r ol_model hf_tokenizer num_ctx model_type; do
  for config_num in "${RQ1_DIR}/queries/inference"/*/; do
    i=$(basename "$config_num")
    # Clean repo_name string from undesired white-spaces/line-breaks introduced with the CSV parsing
    model_type="${model_type//[$'\t\r\n ']/}"
    echo "Running inference with ${ol_model} on query template ${i}"
    "${PY_ENV}" "${RQ1_DIR}/inference.py" \
        --model_name_or_path "${ol_model}" \
        --model_type "${model_type}" \
        --tokenizer_name "${hf_tokenizer}" \
        --query_path "${QUERIES_DIR}/inference/${i}/query-template.txt" \
        --dataset_path "${OUTPUT_PROMPT_DIR}/${i}/prompt" \
        --output_path "${OUTPUT_DIR}/inference/${ol_model}-${i}" \
        --src_col "src" \
        --tgt_col "tgt" \
        --num_ctx "${num_ctx}" \
        --ram_saving true
  done
done < "$VANILLA_LLMS_LIST_FILE"