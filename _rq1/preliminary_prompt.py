import os
import sys

import pandas as pd
from src.Logger import Logger

# Setup the logger
logger = Logger("main", "preliminary-prompt-analysis")


def extract_extremes(df):
    """
    Extract the first, middle and last rows of a dataframe.
    Args:
        df: a dataframe containing the data to extract from
    Returns:
        A dataframe containing the first, middle and last rows of the input dataframe.
    """
    if len(df) == 0:
        return pd.DataFrame()  # Return empty if no rows
    mid_idx = len(df) // 2
    indices = {0, mid_idx, len(df) - 1}
    return df.iloc[list(indices)]

if __name__ == "__main__":
    # Read the args passed to the script
    dataset_path = sys.argv[1]
    output_root_path = sys.argv[2]
    # Collect files to process
    if os.path.isdir(dataset_path):
        llm_dataset_files = []
        for root, dirs, files in os.walk(dataset_path):
            for file in files:
                if file.endswith('.csv'):
                    llm_dataset_files.append(os.path.join(root, file))
    else:
        llm_dataset_files = [dataset_path]
    # Process each file in the dataset
    for file_path in llm_dataset_files:
        logger.log(f'Processing file {file_path}')
        filename = os.path.basename(file_path)
        # Read the csv file and load the data
        df = pd.read_csv(file_path)
        # Add a column with the length of 'src'
        df['src_len'] = df['src'].str.len()
        # Separate by target type
        exception_df = df[df['tgt'] == 'THROW EXCEPTION'].sort_values('src_len')
        assertion_df = df[df['tgt'] != 'THROW EXCEPTION'].sort_values('src_len')
        # Extract for both categories
        exception_samples = extract_extremes(exception_df)
        assertion_samples = extract_extremes(assertion_df)
        # Save the samples to a file
        output_path = os.path.join(output_root_path, 'preliminary-prompt')
        if not os.path.exists(output_path):
            os.makedirs(output_path)
        # Save the samples to a CSV file
        exception_samples.to_csv(os.path.join(output_path, f'{filename.replace(".csv", "")}_exception_samples.csv'), index=False)
        assertion_samples.to_csv(os.path.join(output_path, f'{filename.replace(".csv", "")}_assertion_samples.csv'), index=False)