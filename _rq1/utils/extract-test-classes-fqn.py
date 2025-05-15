import sys
import json
import pandas as pd

if __name__ == "__main__":
    # Get the test file path from command line arguments
    prompt_info_file_path = sys.argv[1]
    inference_file_path = sys.argv[2]
    df = pd.read_csv(inference_file_path, header=None)

    with open(prompt_info_file_path, 'r') as f:
        prompt_info = json.load(f)

    classes_fqn_list = []
    test_classes_fqn_list = []

    for i, row in df.iterrows():
        test_file_path = None
        for entry in prompt_info:
            if entry['id'] == row[0]:
                test_file_path = entry['test_class_path'][1:] if entry['test_class_path'].startswith('/') else entry['test_class_path']
                break
        if test_file_path:
            if "src/test/java" in test_file_path:
                test_class_path = test_file_path.replace("src/test/java/", "")
            elif "src/tests/java" in test_file_path:
                test_class_path = test_file_path.replace("src/tests/java/", "")
            elif "src/java/test" in test_file_path:
                test_class_path = test_file_path.replace("src/java/test/", "")
            elif "src/test" in test_file_path:
                test_class_path = test_file_path.replace("src/test/", "")
            elif "test/java" in test_file_path:
                test_class_path = test_file_path.replace("test/java/", "")
            elif "test" in test_file_path:
                test_class_path = test_file_path.replace("test/", "")
            elif "unittest" in test_file_path:
                test_class_path = test_file_path.replace("unittest/", "")
            else:
                print(f"Unknown test file path format: {test_file_path}")
                continue
            test_class_fqn = test_class_path.replace('/', '.').replace('.java', '')
            test_classes_fqn_list.append(test_class_fqn)
            classes_fqn_list.append(test_class_fqn.replace("Test_STAR_Split", ""))
    print(json.dumps({"classes": ','.join(list(set(classes_fqn_list))), "test_classes": ','.join(list(set(test_classes_fqn_list)))}))
