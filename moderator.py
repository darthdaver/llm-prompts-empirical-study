import csv
import json
import re
import os
import sys
import ollama
from transformers import HfArgumentParser

sys.path.append(os.path.join(os.path.dirname(__file__), "..", "..", "..", "..", "neural-module"))
from src.parsers.OllamaModeratorArgs import OllamaModeratorArgs
from src.utils.Logger import Logger

# Setup the logger
logger = Logger("main", "RQ1 - moderator")


def preprocess_query(query_template, target, prediction):
    """
    Preprocess a couple of target-prediction assertions that the moderator models have to evaluate.
    Args:
        query_template (`str`):
            The query template to use as prompt to the moderator models.
        target (`str`):
            The target assertion.
        prediction (`str`):
            The predicted assertion.
    """
    query = query_template.replace("<TARGET>", target)
    query = query.replace("<PREDICTION>", prediction)
    return query


def llm_assertion_moderator(llm_id, num_ctx, llm_type, query):
    """
    Use the LLM as moderator to determine if the target and the prediction assertions are equivalent.
    Args:
        llm_id: the identifier of the LLM model to use as moderator.
        num_ctx: the context length of the model
        llm_type: the type of the LLM model to use as moderator (`base` or `reasoning`).
        query: the query to use as prompt to the moderator model.

    Returns:
        The result of the equivalence evaluation (<EQUIVALENT>, <NOT_EQUIVALENT>, <UNKNOWN>, <ERROR>).
    """
    # Predict the output with ollama
    try:
        response = ollama.generate(
            model=llm_id,
            prompt=query,
            options={
                "num_ctx": int(num_ctx),
                "seed": 42,
                "num_predict": 500 if llm_type == "base" else 4096,
            }
        )
        # Use regex to extract whatever is between <keep> and </keep>
        match = re.search(r'<keep>(.*?)</keep>', response['response'].strip(), re.DOTALL)

        if llm_type == "reasoning":
            response['response'] = response['response'].split("</think>")[-1]

        if match:
            out = match.group(1).strip()
            if out in ["EQUIVALENT", "NOT EQUIVALENT"]:
                logger.log(f"Response from {llm_id}: {out}")
                return out
            else:
                logger.log(
                    f"Response from {llm_id}: Regex extracted, but response different from ['EQUIVALENT', 'NOT EQUIVALENT']")
                return "UNKNOWN"
        else:
            match = re.search(r'\bassert\w+\((.*?)\);', response['response'].strip())
            if match:
                out = match.group(1).strip()
                if out in ["EQUIVALENT", "NOT EQUIVALENT"]:
                    logger.log(f"Response from {llm_id}: {out}")
                    return out
                else:
                    logger.log(
                        f"Response from {llm_id}: Regex extracted, but response different from ['EQUIVALENT', 'NOT EQUIVALENT']")
                    return "UNKNOWN"
            else:
                if response['response'].strip() in ["EQUIVALENT", "NOT EQUIVALENT"]:
                    logger.log(f"Response from {llm_id}: {response['response'].strip()}")
                    return response['response'].strip()
                logger.log(f"Response from {llm_id}: Unable to extract content")
                return "UNKNOWN"
    except Exception as e:
        logger.log(f"Error while processing the input:\n{query}")
        logger.log(f"Error: {e}")
        return "ERROR"


def compute_equivalence_threshold(pred_stats, llms):
    """
    Count the results of the equivalence evaluation of the assertions at different thresholds.
    Args:
        pred_stats: the dictionary containing the results of the moderation task for a given equivalence.
        llms: the list of the LLM models used as moderators.
    """
    equivalence_counter = 0
    for llm_id in llms:
        if pred_stats[llm_id] == "EQUIVALENT":
            equivalence_counter += 1
    return equivalence_counter


if __name__ == "__main__":
    """
    The script analyzes the output of the LLMs and uses all the models as moderators to determine if the ground truth and 
    the predicted assertions are equivalent.
    To individuate the best prompt to the model, we designed 4 different preliminary templates, integrating the test prefix
    and different combination of contextual information (signature, javadoc, body) about the methods invoked within the
    test prefix. We asked a list of vanilla LLMs (pretrained with text and/or code) to predict the assertions that should
    follow the test prefixes in the ground truth dataset.
    To measure the capability of the LLMs to predict equivalent assertions automatizing the manual evaluation, we ask 
    all the models to evaluate the equivalence of the predicted assertions with the ground truth ones. The models have to
    answer if the two assertions are equivalent or not, considering the semantics of the assertions.
    For each assertion predicted by the LLMs, we count the votes of the models that predict the equivalence of the assertions.
    The script saves the results in json format, for future analysis. The idea is that if the majority of the models predict 
    the equivalence of the assertions, we consider the assertions equivalent.
    """
    # Read the args passed to the script
    parser = HfArgumentParser(OllamaModeratorArgs)
    moderator_args = parser.parse_args_into_dataclasses()[0]
    input_path = moderator_args.input_path
    output_path = moderator_args.output_path
    checkpoint = 100
    # Read the model list from the file where they are listed
    llms = []
    with open(moderator_args.moderator_list_path, 'r') as moderator_list_file:
        csv_reader = csv.reader(moderator_list_file)
        for row in csv_reader:
            ollama_model_name = row[0]
            num_ctx = int(row[2])
            model_type = row[3]
            llms.append((ollama_model_name, num_ctx, model_type))
    # Get query text template
    with open(moderator_args.query_path, 'r') as query_file:
        query_template = query_file.read()
    # Process each model folder within the input directory
    for model_preds_dir in os.listdir(input_path):
        # Extract the name of the model and the query template id
        model_id = model_preds_dir.split(':')[0] + ':' + model_preds_dir.split(':')[1].replace(
            "-" + model_preds_dir.split('-')[-1], "")
        query_id = int(model_preds_dir.split('-')[-1])
        # assert model_id in [ l[0] for l in llms ]
        if not model_id in [l[0] for l in llms]:
            logger.log(f"Model {model_id} not in the list of moderators")
            continue
        logger.log(f"Processing model {model_id} with query template {query_id}")
        # Initialize the list of processed moderators
        moderators_processed = []
        for i_llm, moderator in enumerate(llms):
            moderator_id, num_ctx, moderator_type = moderator
            logger.log(f"Moderator {moderator_id} with context length {num_ctx} and type {moderator_type}")
            moderators_processed.append(moderator)
            # Process each file of predictions within the model folder
            for preds_file_name in os.listdir(os.path.join(input_path, model_preds_dir)):
                if preds_file_name.endswith(".csv"):
                    output_file_path = os.path.join(output_path, model_preds_dir)
                    if not os.path.exists(output_file_path):
                        os.makedirs(output_file_path)
                    output_file_name = os.path.join(output_file_path,
                                                    f"{preds_file_name.replace('.csv', '')}_mod_analysis.json")
                    # Initialize the list of the processed assertions
                    moderators_stats = [] if i_llm == 0 else json.load(open(output_file_name, 'r'))
                    logger.log(f"Processing file {preds_file_name}")
                    preds_file_path = os.path.join(input_path, model_preds_dir, preds_file_name)
                    with open(preds_file_path, 'r', newline='', encoding='utf-8') as preds_file:
                        reader = csv.reader(preds_file)
                        rows = list(reader)
                        # Process each prediction from the file
                        for i_row, row in enumerate(rows):
                            target = row[1].rstrip(';')
                            raw_prediction = row[2]
                            parsing = False
                            # Use regex to extract the assertion between <keep> and </keep>
                            match = re.search(r'<keep>(.*?)</keep>', raw_prediction, re.DOTALL)
                            if match:
                                parsing = True
                                prediction = match.group(1).strip(';')
                            else:
                                match = re.search(r'\bassert\w+\((.*?)\);', raw_prediction)
                                if match:
                                    parsing = True
                                    prediction = match.group(0).strip(';')
                                else:
                                    prediction = raw_prediction.strip(';')
                            logger.log(f"Analyzing equivalence between {target} and {prediction}")
                            # Generate the query for the moderators, from the query template
                            query = preprocess_query(query_template, target, prediction)
                            pred_stats = {
                                "llm": model_id,
                                "file_path": preds_file_path.replace(input_path, ''),
                                "prompt-prediction": row[0],
                                "prompt-moderator": query,
                                "target": target,
                                "raw_prediction": raw_prediction,
                                "prediction": prediction,
                                "parsing": parsing,
                                "match": True if target == prediction else False
                            } if i_llm == 0 else moderators_stats[i_row]
                            # Run the moderators
                            result = llm_assertion_moderator(moderator_id, num_ctx, moderator_type, query)
                            pred_stats[moderator_id] = result
                            # Compute the equivalence threshold
                            pred_stats['equivalence_counter'] = compute_equivalence_threshold(pred_stats, [l[0] for l in
                                                                                                           moderators_processed])
                            logger.log(
                                f"Equivalence threshold: {pred_stats['equivalence_counter']}/{len(moderators_processed)}")
                            # Save the result of the moderation task
                            if i_llm == 0:
                                moderators_stats.append(pred_stats)
                            if (((i_row + 1) % checkpoint) == 0) or (i_row == len(rows) - 1):
                                # Save the intermediate result of the moderation task
                                logger.log(f"Saving intermediate results to {output_path}")
                                with open(output_file_name, 'w', newline='', encoding='utf-8') as output:
                                    json.dump(moderators_stats, output, indent=4)