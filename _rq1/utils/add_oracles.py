import os
import javalang
import sys
import re
import pandas as pd
import json

def read_java_file(repo_root_path, file_path):
    with open(os.path.join(repo_root_path, file_path), 'r', encoding='utf-8') as f:
        return f.read()

def find_methods(source_code, name):
    tree = javalang.parse.parse(source_code)
    matches = []
    for _, node in tree:
        if isinstance(node, javalang.tree.MethodDeclaration):
            if node.name == name:
                matches.append(node)
    return matches

def replace_method_body(source_code, signature, new_body):
    # crude regex-based replace (not perfect for nested methods or annotations)
    pattern = re.compile(rf'({signature}\s*{re.escape("{")})(.*?)(\s*{re.escape("}")})', re.DOTALL)
    pass

if __name__ == "__main__":
    repo_root_path = sys.argv[1]
    prompt_info_file_path = sys.argv[2]
    inference_file_path = sys.argv[3]
    df = pd.read_csv(inference_file_path, header=None)
    with open(prompt_info_file_path, 'r') as f:
        prompt_info = json.load(f)

    for i, row in df.iterrows():
        info = None
        for entry in prompt_info:
            if entry['id'] == row[0]:
                info = entry
                break
        if info:
            test_file_path = info['test_class_path'][1:] if info['test_class_path'].startswith('/') else info['test_class_path']
            java_code = read_java_file(repo_root_path, test_file_path)
            # Search for method by signature
            methods = find_methods(java_code, info['signature'])
            if not methods:
                print(f"No matching method found for signature {info['signature']}.")
                continue
            if len(methods) > 1:
                print(f"Multiple matching methods found for signature {info['signature']}.")
                continue

            # Replace the body
            new_body = info['tp_body'].replace("/*<MASK_PLACEHOLDER>*/", info['tgt'])
            updated_code = replace_method_body(java_code, info['signature'], new_body)

            # Save or print updated code
            #with open(file_path, "w", encoding="utf-8") as f:
            #    f.write(updated_code)