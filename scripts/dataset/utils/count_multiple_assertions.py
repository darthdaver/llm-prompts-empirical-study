import os
import json

def analyze_json_files(directory):
    # Initialize counters for each category
    count_0 = 0
    count_1 = 0
    count_2 = 0
    count_gt_3 = 0
    total_identifiers = 0

    # Traverse through each subfolder in the directory
    for root, dirs, files in os.walk(directory):
        print(f"Processing directory: {root}")
        print(f"Subdirectories: {dirs}")
        print(f"Files: {files}")
        
        for file in files:
            if file.endswith('.json'):
                file_path = os.path.join(root, file)
                print(f"Processing file: {file_path}")

                try:
                    # Open and read the JSON file
                    with open(file_path, 'r') as f:
                        data_all = json.load(f)
                        for data in data_all:
                            # Iterate through each datapoint in the list
                            for datapoint in data['datapoints']:
                                # Check if the testPrefix key exists in the datapoint
                                if 'testPrefix' in datapoint:
                                    # Extract the identifier part after the underscore
                                    identifier = datapoint['testPrefix']['identifier'].split('_')[-1]

                                    # Convert the identifier to an integer
                                    try:
                                        identifier_value = int(identifier)

                                        # Increment the appropriate counter
                                        if identifier_value == 0:
                                            count_0 += 1
                                        elif identifier_value == 1:
                                            count_1 += 1
                                        elif identifier_value == 2:
                                            count_2 += 1
                                        else:
                                            count_gt_3 += 1

                                        # Increment the total identifiers counter
                                        total_identifiers += 1
                                    except ValueError:
                                        print(f"Invalid identifier in {file_path}: {identifier}")
                except Exception as e:
                    print(f"Error reading {file_path}: {e}")

    # Calculate percentages
    if total_identifiers > 0:
        percent_0 = (count_0 / total_identifiers) * 100
        percent_1 = (count_1 / total_identifiers) * 100
        percent_2 = (count_2 / total_identifiers) * 100
        percent_gt_3 = (count_gt_3 / total_identifiers) * 100

        print("\nTotal datapoints:", total_identifiers)

        # Print the results
        print(f"Percentage of identifiers ending with 0: {percent_0:.1f}%")
        print(f"Percentage of identifiers ending with 1: {percent_1:.1f}%")
        print(f"Percentage of identifiers ending with 2: {percent_2:.1f}%")
        print(f"Percentage of identifiers ending with >= 3: {percent_gt_3:.1f}%")
    else:
        print("No valid identifiers found.")

if __name__ == "__main__":
    # Specify the directory containing the JSON files
    directory = input("Enter the directory path containing the JSON files: ")

    # Run the analysis
    analyze_json_files(directory)