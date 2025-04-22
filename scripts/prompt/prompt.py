import os
import sys
import json
import csv
import logging
import copy
from transformers import HfArgumentParser
sys.path.append(os.path.join(os.path.dirname(__file__), ".."))
from EwashArgsParser import EwashArgsParser
from EWash import EWash
from utils.Logger import Logger


# Setup the logger
logger = Logger("main", "ewash")


def update_stats(stats, tc_id, tp_id, tgt, exceeded, num_tokens):
    """
    Update the statistics dictionary with the information about the target oracle, the flag indicating if the input length
    limit was exceeded, and the number of tokens in the input string.
    :param stats: the dictionary containing the statistics about the datapoints processed
    :param tp_id: the identifier of the test prefix
    :param tgt: the target oracle to generate
    :param exceeded: the flag indicating if the input length limit was exceeded
    :param num_tokens: the number of tokens in the input string
    """
    if not tc_id in stats:
        stats[tc_id] = {}
    if not tp_id in stats:
        stats[tc_id][tp_id] = []
    stats[tc_id][tp_id].append({"tgt": tgt, "exceeded": exceeded, "num_tokens": num_tokens})


if __name__ == '__main__':
    """
    The program process the raw JSON dataset and generates the input for the model using the E-WASH approach.
    The prompt is customizable and can be configured to include information about the invoked methods, the test class,
    and the focal class. The E-WASH approach integrates the information progressively, only if the whole information fits
    in the input length limit of the model. The program generates the input for the model in the CSV format, where each
    row contains the model input of a datapoint and the corresponding target oracle.
    """
    # Input and output paths
    # Parse arguments
    parser = HfArgumentParser(EwashArgsParser)
    args = parser.parse_args_into_dataclasses()[0]

    input_path = args.input
    output_path = args.output
    config_path = args.config
    intros_path = args.intros
    # Create the output directory if it does not exist
    if not os.path.exists(output_path):
        os.makedirs(output_path)
    # Load the configuration file for the E-WASH approach
    with open(config_path, 'r') as config_file:
        ewash_config = json.load(config_file)
    # Load the dictionary of intro comments for each section of the input for the E-WASH approach
    with open(intros_path, 'r') as intros_file:
        intros_template = json.load(intros_file)
    # Initialize statistics
    stats = {}
    # Collect files to process
    if os.path.isdir(input_path):
        raw_dataset_files = []
        for root, dirs, files in os.walk(input_path):
            for file in files:
                if file.endswith('.json'):
                    raw_dataset_files.append(os.path.join(root, file))
    else:
        raw_dataset_files = [input_path]
    # Process each JSON file in the original json dataset
    for filename in raw_dataset_files:
        logger.log(f'Processing file {filename}', logging.INFO)
        file_path = os.path.join(input_path, filename)
        # Read the JSON file and load the data
        with open(file_path, 'r') as json_file:
            test_classes_datapoints = json.load(json_file)
        output_file_path = os.path.join(output_path, os.path.basename(filename).replace(".json", ".csv"))
        # Open the CSV file for writing the dataset
        with open(output_file_path, 'w', newline='') as csv_file:
            writer = csv.writer(csv_file)
            # Write the header
            writer.writerow(["src", "tgt"])
            # Process each datapoint in the JSON list
            for tcs_d in test_classes_datapoints:
                # Initialize the counter of the tokens in the input to the model
                tokens_counter = 0
                # Get the information about the test class and the focal class
                tc = tcs_d['testClass']
                fc = tcs_d['focalClass']
                # Get the JUnit version used in the test class to generate the target oracle
                ju_v = tcs_d['junitVersion']
                # Normalize sections to conform to the expected format
                tc = EWash.normalize_section(tc)
                fc = EWash.normalize_section(fc)
                # Iterate over the datapoints in the test class and create the corresponding input for the model
                for i_d,d in enumerate(tcs_d['datapoints']):
                    # Get the test prefix of the datapoint and the target oracle to generate
                    tp = EWash.normalize_section(d['testPrefix'])
                    oracle = d['target']
                    intros = copy.deepcopy(intros_template)
                    logger.log(f"Processing datapoint {tp['signature']}, oracle: {oracle}", logging.INFO)
                    # Get target (the whole oracle)
                    tgt = oracle
                    try:
                        if i_d == 0:
                            # Initialize the E-WASH dictionary
                            ewash = EWash(tp, tc, fc, ju_v, intros, ewash_config)
                            # Generate the input string for the model using the E-WASH approach
                            src, exceeded, num_tokens = ewash.run(['test_prefix', 'test_class', 'focal_class'])
                        else:
                            ewash.update(intros, tp, ju_v, update_section=True)
                            src, exceeded, num_tokens = ewash.run(['test_prefix'])
                        # Write the input and the target oracle to the CSV file
                        writer.writerow([src, oracle])
                        # Update stats
                        update_stats(stats, tc['identifier'], tp['signature'], tgt, exceeded, num_tokens)
                    except ValueError as e:
                        # Update stats
                        update_stats(stats, tc['identifier'], tp['signature'], tgt, True, -1)
        # Save statistics
        with open(os.path.join(output_path, os.path.basename(filename).replace(".json", "_stats.json")), 'w') as stats_file:
            json.dump(stats, stats_file)
    logger.log(f"Processing completed.", logging.INFO)