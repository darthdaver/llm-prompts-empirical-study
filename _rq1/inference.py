import os
import sys
import csv
import ollama
import pandas as pd
import time
from transformers import HfArgumentParser
sys.path.append(os.path.join(os.path.dirname(__file__), "..", "..", "..", "..", "neural-module"))
from src.parsers.ModelArgs import ModelArgs
from src.parsers.OllamaInferenceArgs import OllamaInferenceArgs
from src.utils.Logger import Logger


# Setup the logger
logger = Logger("main", "RQ1 - inference")


def preprocess_dp(query_template, src_col):
    """
    Preprocess a single datapoint from the dataset.
    Args:
        query_template (`str`):
            The query text to prepend to the source text.
        src_col (`str`):
            The name of the source column in the dataset.
    """
    query = query_template.replace("<QUERY_INPUT>", src_col)
    return query


def save_prediction_stats(out_file_path: str, inputs: list, targets: list, predictions: list):
    """
    Save the prediction stats to a CSV file.
    Args:
        out_file_path (`str`):
            The path to the output file.
        inputs (`list`):
            The list of input sequences.
        targets (`list`):
            The list of target sequences.
        predictions (`list`):
            The list of predicted sequences.
    """
    df = pd.DataFrame({
        'input': inputs,
        'target': targets,
        'prediction': predictions
    })
    df.to_csv(out_file_path, index=False)


if __name__ == "__main__":
    # Read the args passed to the script
    parser = HfArgumentParser((ModelArgs, OllamaInferenceArgs))
    model_args, data_args = parser.parse_args_into_dataclasses()
    # Get query text
    with open(data_args.query_path, 'r') as query_file:
        query_template = query_file.read()
    # Set output path
    output_path = data_args.output_path
    if not os.path.exists(output_path):
        os.makedirs(output_path)
    # Log the arguments
    logger.log("=" * 50)
    logger.log(f"Dataset path: {data_args.dataset_path}")
    logger.log(f"Checkpoints path: {model_args.model_name_or_path}")
    logger.log(f"Predictions path: {output_path}")
    logger.log("=" * 50)
    # Collect files to process
    if os.path.isdir(data_args.dataset_path):
        llm_dataset_files = []
        for root, dirs, files in os.walk(data_args.dataset_path):
            for file in files:
                if file.endswith('.csv'):
                    llm_dataset_files.append(os.path.join(root, file))
    else:
        llm_dataset_files = [data_args.dataset_path]
    # Check and notify if ram saving mode is on
    logger.log(f"RAM saving mode { 'on' if data_args.ram_saving else 'off' }")
    # Process each file in the dataset
    for file_path in llm_dataset_files:
        logger.log(f'Processing file {file_path}')
        filename = os.path.basename(file_path)
        # Read the csv file and load the data
        with open(file_path, mode='r', newline='') as input_file:
            reader = csv.DictReader(input_file)
            rows = list(reader)
            inputs = []
            targets = []
            predictions = []
            times = []
            checkpoint = 100
            # Read each row as a dictionary
            for i, row in enumerate(rows,1):
                src = row[data_args.src_col]
                tgt = row[data_args.tgt_col]
                query = preprocess_dp(query_template, src)
                # Predict the output with ollama
                try:
                    start_time = time.time()
                    response = ollama.generate(
                        model=model_args.model_name_or_path,
                        prompt=query,
                        options={
                            "num_ctx": int(data_args.num_ctx),
                            "seed": 42,
                            "num_predict": 500 if data_args.model_type == "base" else 4096
                        }
                    )
                    end_time = time.time()
                    out = response['response'].strip()
                    inputs.append(src)
                    targets.append(tgt)
                    predictions.append(out)
                    times.append(end_time - start_time)
                except Exception as e:
                    end_time = time.time()
                    logger.log(f"Error while processing the input:\n{query}")
                    logger.log(f"Error: {e}")
                    inputs.append(src)
                    targets.append(tgt)
                    times.append(end_time - start_time)
                    predictions.append("Error")
                if data_args.ram_saving and ((i % checkpoint == 0) or (i == len(rows) - 1)):
                    # Save the predictions one by one
                    logger.log(f"Saving prediction to {output_path}")
                    with open(os.path.join(output_path, filename), mode='a', newline='') as out_file:
                        writer = csv.writer(out_file)
                        for src, tgt, out, request_time in zip(inputs, targets, predictions, times):
                            writer.writerow([src, tgt, out, request_time])
                    inputs = []
                    targets = []
                    predictions = []
                    times = []
        # Save the predictions all at once
        if not data_args.ram_saving:
            # Save stats of predictions
            logger.log(f"Saving predictions to {output_path}")
            save_prediction_stats(os.path.join(output_path, filename), inputs, targets, predictions)