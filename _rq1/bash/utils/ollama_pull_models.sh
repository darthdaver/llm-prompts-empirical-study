# The script is used to setup the OLLAMA environment.
# The script pulls all the models required to replicate RQ1

# Get current directory
current_dir=$(realpath "$(dirname "${BASH_SOURCE[@]}")")
# Setup global variables
source "${current_dir}/../../../scripts/utils/bash/global_variables.sh"

# Download the models
while IFS=, read -r ol_model _ _; do
  echo "Pulling model ${ol_model}"
  ollama pull "${ol_model}"
done < "$VANILLA_LLMS_LIST"