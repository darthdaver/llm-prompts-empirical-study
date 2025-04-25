# The script transforms the raw oracles dataset into the final prompts to the model.
# The output to the script is a list of files containing the inputs to the model, for each datapoint.
# Each file is a csv file containing the two columns: src and tgt. The src column contains the input to the model,
# while the tgt column contains the target that the model should predict during the training phase.
# The input is generated using the E-wash approach, according to the configuration file saved in the resources folder
# (ml-model/src/resources/ewash_config.json).

# Get current directory
current_dir=$(realpath "$(dirname "${BASH_SOURCE[@]}")")
# Setup global & local variables
source "${current_dir}/../../utils/bash/global_variables.sh"
source "${current_dir}/utils/local_variables.sh"

for config_path in "${PROMPT_DIR}/resources/config"/*/; do
  config_num=$(basename "${config_path}")
  echo "Generating prompt with configuration ${config_num}"
  # Generate LLM oracles input
  "${PY_ENV}" "${PROMPT_DIR}/prompt.py" \
      --input "${OUTPUT_DATASET_DIR}" \
      --output "${OUTPUT_DIR}/${config_num}" \
      --config "${PROMPT_DIR}/resources/config/${config_num}/ewash_config.json"
done