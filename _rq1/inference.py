import os
import time
import pandas as pd
import ollama
from dataclasses import dataclass, field
from typing import Optional
from transformers import HfArgumentParser, AutoTokenizer
from huggingface_hub import login
from dotenv import load_dotenv
import csv

# Load environment variables from .env file
load_dotenv()

# Access the token
hf_token = os.getenv("HUGGINGFACE_TOKEN")
# Login to Hugging Face Hub
login(token=hf_token)

# ====================
# Inline Argument Classes
# ====================

@dataclass
class ModelArgs:
    model_name_or_path: str = field(
        metadata={"help": "Path to pretrained model, checkpoint, or model identifier."}
    )
    tokenizer_name: str = field(
        metadata={"help": "Pretrained tokenizer name or path."}
    )
    model_type: Optional[str] = field(
        default=None,
        metadata={"help": "Model type (not used, for compatibility with shell script)."}
    )


@dataclass
class OllamaInferenceArgs:
    dataset_path: str = field(
        metadata={"help": "Path to the dataset file or folder."}
    )
    output_path: str = field(
        metadata={"help": "Output path where to save the results."}
    )
    src_col: str = field(
        metadata={"help": "Column name in the dataset containing the input."}
    )
    tgt_col: str = field(
        metadata={"help": "Column name in the dataset containing the target."}
    )
    num_ctx: int = field(
        metadata={"help": "The context length. Sequences exceeding the length will be truncated or padded."}
    )
    query_path: Optional[str] = field(
        default=None,
        metadata={"help": "Path to the query request template file."}
    )
    ram_saving: Optional[bool] = field(
        default=False,
        metadata={"help": "Dummy flag to accept --ram_saving from shell script."}
    )


# ====================
# Logger
# ====================

class logger:
    @staticmethod
    def log(msg):
        print(f"[LOG] {msg}")

def count_tokens(tokenizer, input_text):
    """
    Count the number of tokens in the input text encoding it with the model's tokenizer (considering the special tokens).
    :param tokenizer: the tokenizer to encode the input text
    :param input_text: the input text to encode
    :return: the number of tokens in the input text
    """
    # Tokenize the input text encoding it with the model's tokenizer (not considering the special tokens)
    tokens = tokenizer.encode(input_text, add_special_tokens=False)
    # Return the number of tokens in the input text
    return len(tokens)

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
    try:
        tokenizer = AutoTokenizer.from_pretrained(model_args.tokenizer_name)
    except Exception as e:
        tokenizer = None
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
    logger.log(f"RAM saving mode {'on' if data_args.ram_saving else 'off'}")
    # Process each file in the dataset
    for file_path in llm_dataset_files:
        logger.log(f'Processing file {file_path}')
        filename = os.path.basename(file_path)
        # Read the csv file and load the data
        with open(file_path, mode='r', newline='') as input_file:
            reader = csv.DictReader(input_file)
            rows = list(reader)
            logger.log(f'Loading file {file_path}.')
            dp_ids = []
            queries = []
            inputs = []
            targets = []
            predictions = []
            times = []
            nums_tokens = []
            exceeds = []
            checkpoint = 100
            # Read each row as a dictionary
            for i, row in enumerate(rows, 1):
                dp_id = row['id']
                src = row[data_args.src_col]
                tgt = row[data_args.tgt_col]
                query = preprocess_dp(query_template, src)
                num_tokens = count_tokens(tokenizer, query) if tokenizer else None
                exceed = num_tokens >= data_args.num_ctx if tokenizer else None
                # Predict the output with ollama
                try:
                    start_time = time.time()
                    response = ollama.generate(
                        model=model_args.model_name_or_path,
                        prompt=query,
                        options={
                            "num_ctx": int(data_args.num_ctx),
                            "seed": 42,
                            "num_predict": 500 if model_args.model_type == "base" else 4096
                        }
                    )
                    end_time = time.time()
                    out = response['response'].strip()
                    dp_ids.append(dp_id)
                    queries.append(query)
                    inputs.append(src)
                    targets.append(tgt)
                    predictions.append(out)
                    times.append(end_time - start_time)
                    nums_tokens.append(num_tokens)
                    exceeds.append(exceed)
                except Exception as e:
                    end_time = time.time()
                    logger.log(f"Error while processing the input:\n{query}")
                    logger.log(f"Error: {e}")
                    dp_ids.append(dp_id)
                    queries.append(query)
                    inputs.append(src)
                    targets.append(tgt)
                    times.append(end_time - start_time)
                    nums_tokens.append(num_tokens)
                    exceeds.append(exceed)
                    predictions.append("Error")
                if data_args.ram_saving and ((i % checkpoint == 0) or (i == len(rows) - 1)):
                    # Save the predictions one by one
                    logger.log(f"Saving prediction to {output_path}")
                    with open(os.path.join(output_path, filename), mode='a', newline='') as out_file:
                        writer = csv.writer(out_file)
                        for dp_id, query, src, tgt, out, request_time, num_tokens, exceed in zip(dp_ids, queries, inputs, targets, predictions, times, nums_tokens, exceeds):
                            writer.writerow([dp_id, query, src, tgt, out, request_time, num_tokens, exceed ])
                    dp_ids = []
                    queries = []
                    inputs = []
                    targets = []
                    predictions = []
                    times = []
                    nums_tokens = []
                    exceeds = []

        # Save the predictions all at once
        if not data_args.ram_saving:
            # Save stats of predictions
            logger.log(f"Saving predictions to {output_path}")
            save_prediction_stats(os.path.join(output_path, filename), inputs, targets, predictions)