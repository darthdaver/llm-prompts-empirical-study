#!/bin/bash
# This script runs multiple instances of the generate dataset script in parallel.
# It takes two optional arguments: the first is the maximum number of jobs to run in parallel.
# The second is a boolean flag to resolve dependencies (default is false).

source ../../.venv/bin/activate

# Get current directory
current_dir=$(realpath "$(dirname "${BASH_SOURCE[@]}")")
# Setup global & local variables
source "${current_dir}/../../utils/bash/global_variables.sh"
source "${current_dir}/utils/local_variables.sh"

max_jobs=${1:-100}
resolve_deps=${2:-"false"}
num_files=$(find "${RESOURCES_DIR}/github-repos-split" -type f | wc -l)
running_pids=()
current_index=0

mkdir -p logs
mkdir -p pids

check_running_processes() {
    for i in "${!running_pids[@]}"; do
        if ! kill -0 "${running_pids[i]}" 2>/dev/null; then
            unset 'running_pids[i]'
            running_pids=("${running_pids[@]}")
            return
        fi
    done
}

while (( current_index < num_files )); do
    if (( ${#running_pids[@]} < max_jobs )); then
        nohup bash "${DATASET_DIR}/bash/generate-dataset.sh" "${RESOURCES_DIR}/github-repos-split/split_${current_index}.csv" "${resolve_deps}" > "logs/log-${current_index}.out" 2>&1 &
        last_pid=$!
        echo "$last_pid" > "pids/pid-${current_index}.txt"
        running_pids+=("$last_pid")
        ((current_index++))
    fi

    sleep 2
    check_running_processes
done

wait "${running_pids[@]}"
echo "All processes have completed!"