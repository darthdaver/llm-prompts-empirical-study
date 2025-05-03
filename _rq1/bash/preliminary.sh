# The script run the inference task with the vanilla llms required to replicate RQ1

# Get current directory
current_dir=$(realpath "$(dirname "${BASH_SOURCE[@]}")")
# Setup global variables and local variables
source "${current_dir}/../../scripts/utils/bash/global_variables.sh"
source "${current_dir}/utils/local_variables.sh"

while IFS=, read -r ol_model hf_tokenizer num_ctx model_type; do
  for config_num in "${PROMPT_DIR}/queries/preliminary"/*/; do
    i=$(basename "$config_num")
    # Clean repo_name string from undesired white-spaces/line-breaks introduced with the CSV parsing
    model_type="${model_type//[$'\t\r\n ']/}"
    echo "Running inference with ${ol_model} on query template ${i}"
    "${PY_ENV}" "${RQ1_DIR}/preliminary.py" \
        --model_name_or_path "${ol_model}" \
        --model_type "${model_type}" \
        --tokenizer_name "${hf_tokenizer}" \
        --query_path "${QUERIES_DIR}/preliminary/${i}/query-template.txt" \
        --dataset_path "${OUTPUT_PROMPT_DIR}/preliminary-prompt" \
        --output_path "${OUTPUT_DIR}/preliminary/${ol_model}-${i}" \
        --src_col "src" \
        --tgt_col "tgt" \
        --num_ctx "${num_ctx}" \
        --ram_saving true
  done
done < "$VANILLA_LLMS_LIST"