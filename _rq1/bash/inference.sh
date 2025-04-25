# The script run the inference task with the vanilla llms required to replicate RQ1

# Get current directory
current_dir=$(realpath "$(dirname "${BASH_SOURCE[@]}")")
# Setup global variables
source "${current_dir}/../../scripts/utils/global_variables.sh"
# Get parameters
query_type=$1

while IFS=, read -r ol_model hf_tokenizer num_ctx model_type; do
  for config_num in "${PROMPT_DIR}/queries/inference"/*/; do
    # Clean repo_name string from undesired white-spaces/line-breaks introduced with the CSV parsing
    model_type="${model_type//[$'\t\r\n ']/}"
    echo "Running inference with ${ol_model} on query template ${i}"
    "${PY_ENV}" "${RQ1_DIR}/inference.py" \
        --model_name_or_path "${ol_model}" \
        --model_type "${model_type}" \
        --tokenizer_name "${hf_tokenizer}" \
        --query_path "${ROOT_DIR}/scripts/resources/queries/pretrained/${query_type}/${i}/query-template.txt" \
        --dataset_path "${RQ1_DATASET_DIR}/${i}" \
        --output_path "${ROOT_DIR}/output/inference/${ol_model}-${i}" \
        --src_col "src" \
        --tgt_col "tgt" \
        --num_ctx "${num_ctx}" \
        --ram_saving true
  done
done < "$RQ1_MODELS_LIST"