#!/bin/bash
# This script split the original CSV file of github repositories into smaller files to process them in parallel.
# It takes a single argument, which is the number of lines per file.
# The script creates a new folder called `split` within the `resources` directory in the current directory
# and saves the split files there.

# Get current directory
current_dir=$(realpath "$(dirname "${BASH_SOURCE[@]}")")
# Setup global & local variables
source "${current_dir}/global_variables.sh"

lines_per_file=${1:-1}

"${PY_ENV}" "${UTILS_DIR}/split-csv-list.py" \
    "${VANILLA_LLMS_LIST_FILE}" \
    "${RESOURCES_DIR}/vanilla-llms-split" \
    "$lines_per_file"