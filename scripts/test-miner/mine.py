import logging
import os, csv, json, sys
import time
from tree_sitter import Language, Parser
import tree_sitter_java as tsjava
from datetime import datetime
from pydriller import Repository
from transformers import HfArgumentParser
sys.path.append(os.path.join(os.path.dirname(__file__), ".."))
from TestMinerArgs import TestMinerArgs
from utils.Logger import Logger

# Setup the logger
logger = Logger("main", "test_miner")
# Load the Java grammar
JAVA = Language(tsjava.language())
ts_parser = Parser(JAVA)


def generate_modified_file_entry_track(mf, c, idx, b_c_m, a_c_m, d_m, a_m, c_m):
    """
    Generate a dictionary entry for the modified file in a given commit.
    :param mf: the modified file to track
    :param c: the commit containing the modified file
    :param idx: the index the commit processed in the main branch of the repository
    :param b_c_m: the methods in the class before the commit
    :param a_c_m: the methods in the class after the commit
    :param d_m: the deleted methods in the modified file
    :param a_m: the added methods in the modified file
    :param c_m: the changed methods in the modified file
    :return: a dictionary containing the modified file information
    """
    return {
        "idx": idx,
        "old_path": mf.old_path,
        "new_path": mf.new_path,
        "added_lines": mf.added_lines,
        "deleted_lines": mf.deleted_lines,
        "diff_parsed": mf.diff_parsed,
        "src_code_after": mf.source_code,
        "src_code_before": mf.source_code_before,
        "commit_sha": c.hash,
        "commit_date": c.committer_date.strftime("%m/%d/%Y %H:%M:%S"),
        "methods_before_commit": b_c_m,
        "methods_after_commit": a_c_m,
        "deleted_methods": d_m,
        "added_methods": a_m,
        "changed_methods": c_m
    }


def is_test_method(code_bytes, method_node):
    # Check if method_node has any child of type 'modifiers' -> annotations
    for child in method_node.children:
        if child.type == 'modifiers':
            for mod in child.children:
                if mod.type == 'marker_annotation':
                    annotation_name = extract_text(code_bytes, mod.child_by_field_name('name'))
                    if annotation_name in [ 'Test', 'org.junit.Test', 'org.junit.jupiter.api.Test', 'ParameterizedTest', 'org.junit.jupiter.params.ParameterizedTest' ]:
                        return True
    return False


def get_method_signature(code_bytes, node):
    """
    Build the complete method signature, including:
    - Modifiers
    - Type parameters (generics)
    - Return type
    - Method name
    - Parameters
    - Throws clause
    """
    signature_parts = []

    # Modifiers (e.g., public, static)
    modifiers_node = next((c for c in node.children if c.type == 'modifiers'), None)
    if modifiers_node:
        non_annotation_modifiers = extract_non_annotation_modifiers(code_bytes, modifiers_node)
        if non_annotation_modifiers:
            signature_parts.append(non_annotation_modifiers)

    # Type parameters (e.g., <T>)
    type_params_node = node.child_by_field_name('type_parameters')
    if type_params_node:
        signature_parts.append(extract_text(code_bytes, type_params_node))

    # Return type (not present in constructors)
    return_type_node = node.child_by_field_name('type')
    if return_type_node:
        signature_parts.append(extract_text(code_bytes, return_type_node))

    # Method name
    name_node = node.child_by_field_name('name')
    if name_node:
        signature_parts.append(extract_text(code_bytes, name_node))

    # Parameters
    parameters_node = node.child_by_field_name('parameters')
    if parameters_node:
        signature_parts.append(extract_text(code_bytes, parameters_node))

    # Throws clause
    throws_node = node.child_by_field_name('throws')
    if throws_node:
        signature_parts.append(extract_text(code_bytes, throws_node))

    return ' '.join(signature_parts)

def extract_non_annotation_modifiers(code_bytes, modifiers_node):
    """
    Extract only keyword modifiers (e.g., public, static), excluding annotations.
    """
    modifiers = []
    for child in modifiers_node.children:
        if child.type not in {"marker_annotation", "annotation"}:
            modifiers.append(extract_text(code_bytes, child))
    return ' '.join(modifiers)

def extract_text(code_bytes, node):
    if node is None:
        return None
    return code_bytes[node.start_byte:node.end_byte].decode()

def extract_test_methods(root, code_bytes):
    test_methods = {}

    # Traverse all method_declaration nodes within class_body
    def visit(node):
        if node.type == 'method_declaration':
            if is_test_method(code_bytes, node):
                name = extract_text(code_bytes, node.child_by_field_name('name'))
                signature = get_method_signature(code_bytes, node)
                body = extract_text(code_bytes, node.child_by_field_name('body'))
                assert signature not in test_methods, f"Duplicate method signature found: {signature}"
                test_methods[signature] = {
                    'name': name,
                    'signature': signature,
                    'body': body,
                    'full_method': extract_text(code_bytes, node)
                }
        for child in node.children:
            visit(child)

    visit(root)
    return test_methods

if __name__ == '__main__':

    # Read the args passed to the script
    hf_parser = HfArgumentParser(TestMinerArgs)
    miner_args = hf_parser.parse_args_into_dataclasses()[0]
    # Check the validity of the arguments
    if miner_args.repos_file_path is None and (miner_args.repo_name is None or miner_args.repo_url is None):
        logger.log("Please provide the path to the file containing the repositories to mine or the name and URL of the repository to mine.", logging.ERROR)
        sys.exit(1)
    since = datetime.strptime(miner_args.since, '%m/%d/%Y %H:%M:%S')
    until = datetime.now()
    input_path = miner_args.input_path
    output_path = miner_args.output_path
    logger.log(f"Mining the repositories since {miner_args.since} ...")
    # Load retrieved repositories
    repos = []
    if not miner_args.repos_file_path is None:
        with open(miner_args.repos_file_path, "r") as repo_file:
            csv_repos = csv.DictReader(repo_file)
            for repo in csv_repos:
                repos.append({ "name": repo["name"], "url": repo["url"] })
    else:
        repos.append({ "name": miner_args.repo_name, "url": miner_args.repo_url })

    miner_stats = {}
    for repo in repos:
        logger.log(f'Mining the repo name: {repo["name"]} available on {repo["url"]} ...')
        repo_track = { "url": repo["url"] , "since": miner_args.since, "until": until.strftime("%m/%d/%Y %H:%M:%S"), "commits": {}, "track": {}, "file_path_track": {} }
        file_path_track = repo_track['file_path_track']
        try:
            start_repo = time.time()
            miner_stats[repo["url"]] = {
                "time": 0,
                "time_commits": [],
                "processed_commits": 0
            }
            commits = list(Repository(
                    f"{input_path}/{repo['name']}",
                    only_modifications_with_file_types=['.java'],
                    since=since,
                    order="date-order"
            ).traverse_commits())
        except Exception as ex:
            logger.log(f'The repository cannot be parsed {repo}: Error: {ex}', logging.ERROR)
            continue
        commit_idx = 0
        for idx, commit in enumerate(commits, 1):
            try :
                logger.log(f'Processing commit {commit.hash} {idx}/{len(commits)})')
                if commit.in_main_branch:
                    start_commit = time.time()
                    for modified_file in commit.modified_files:
                        new_file_path = modified_file.new_path
                        old_file_path = modified_file.old_path
                        if ((not new_file_path is None) and (new_file_path.endswith(".java"))) or ((not old_file_path is None) and (old_file_path.endswith(".java"))):
                            src_code_after = modified_file.source_code
                            src_code_before = modified_file.source_code_before
                            # Check that the modified file is a test class containing test cases
                            if (not src_code_after is None) and ("@Test" in src_code_after or "@org.junit.Test" in src_code_after or "@org.junit.jupiter.api.Test" in src_code_after or "@ParameterizedTest" in src_code_after or "@org.junit.jupiter.params.ParameterizedTest" in src_code_after):
                                # Parse the source code before and after the commit
                                tree_code_before = ts_parser.parse(src_code_before.encode()) if not src_code_before is None else None
                                tree_code_after = ts_parser.parse(src_code_after.encode()) if not src_code_after is None else None
                                # Get the methods before and after the commit
                                methods_before = extract_test_methods(tree_code_before.root_node, src_code_before.encode()) if not tree_code_before is None else {}
                                # Get the methods after the commit
                                methods_after = extract_test_methods(tree_code_after.root_node, src_code_after.encode()) if not tree_code_after is None else {}
                                # Manage deleted methods
                                deleted_methods = [
                                    b_method for b_method in methods_before.values()
                                    if not b_method['signature'] in methods_after.keys()
                                ]
                                # Manage added methods
                                added_methods = [
                                    a_method for a_method in methods_after.values()
                                    if not a_method['signature'] in methods_before.keys()
                                ]
                                # Manage changed methods
                                changed_methods = [
                                    a_method for a_method in methods_after.values()
                                    if a_method['signature'] in methods_before.keys() and a_method['body'] != methods_before[a_method['signature']]['body']
                                ]

                                if not new_file_path in file_path_track.keys():
                                    if (not old_file_path is None) and (not old_file_path == new_file_path):
                                        if not old_file_path is None:
                                            if not old_file_path in file_path_track.keys():
                                                file_path_track[old_file_path] = old_file_path
                                            file_path_track[new_file_path] = old_file_path
                                        else:
                                            file_path_track[new_file_path] = new_file_path
                                    else:
                                        if not new_file_path in file_path_track.keys():
                                            file_path_track[new_file_path] = new_file_path

                                original_file_path = new_file_path
                                while original_file_path != file_path_track[original_file_path]:
                                    original_file_path = file_path_track[original_file_path]

                                if not original_file_path in repo_track['commits'].keys():
                                    repo_track['commits'][original_file_path] = []
                                repo_track['commits'][original_file_path].append(generate_modified_file_entry_track(
                                    modified_file,
                                    commit,
                                    commit_idx,
                                    methods_before,
                                    methods_after,
                                    deleted_methods,
                                    added_methods,
                                    changed_methods
                                ))

                                if not original_file_path in repo_track['track']:
                                    repo_track['track'][original_file_path] = []
                                for d_m in deleted_methods:
                                    if d_m['signature'] in [ m['signature'] for m in repo_track['track'][original_file_path] ]:
                                        new_track_set  = [ m for m in repo_track['track'][original_file_path] if m['signature'] != d_m['signature'] ]
                                        assert len(new_track_set) == max(len(repo_track['track'][original_file_path]) - 1, 0), f"Error: {len(new_track_set)} != {len(repo_track['track'][original_file_path]) - 1}"
                                        repo_track['track'][original_file_path] = new_track_set
                                for a_m in added_methods:
                                    if not a_m['signature'] in [ m['signature'] for m in repo_track['track'][original_file_path] ]:
                                            repo_track['track'][original_file_path].append(a_m)
                                if miner_args.operation in ["all", "changed"]:
                                    for c_m in changed_methods:
                                        if c_m['signature'] in [ m['signature'] for m in repo_track['track'][original_file_path] ]:
                                            new_track_set  = [ m for m in repo_track['track'][original_file_path] if m['signature'] != c_m['signature'] ]
                                            repo_track['track'][original_file_path] = new_track_set
                                        # Add the new method to the track
                                        repo_track['track'][original_file_path].append(c_m)
                            elif (not src_code_before is None) and ("@Test" in src_code_before or "@org.junit.Test" in src_code_before or "@org.junit.jupiter.api.Test" in src_code_before or "@ParameterizedTest" in src_code_before or "@org.junit.jupiter.params.ParameterizedTest" in src_code_before):
                                original_file_path = old_file_path if new_file_path is None else new_file_path

                                if not new_file_path is None:
                                    if not new_file_path in file_path_track.keys():
                                        if (not old_file_path is None) and (not old_file_path == new_file_path):
                                            if not old_file_path is None:
                                                if not old_file_path in file_path_track.keys():
                                                    file_path_track[old_file_path] = old_file_path
                                                file_path_track[new_file_path] = old_file_path
                                            else:
                                                file_path_track[new_file_path] = new_file_path
                                        else:
                                            if not new_file_path in file_path_track.keys():
                                                file_path_track[new_file_path] = new_file_path
                                else:
                                    if not old_file_path in file_path_track.keys():
                                        file_path_track[old_file_path] = old_file_path
                                while original_file_path != file_path_track[original_file_path]:
                                    original_file_path = file_path_track[original_file_path]
                                tree_code_before = ts_parser.parse(src_code_before.encode())
                                # Get the methods before the commit
                                methods_before = extract_test_methods(tree_code_before.root_node, src_code_before.encode())

                                if not original_file_path in repo_track['commits'].keys():
                                    repo_track['commits'][original_file_path] = []
                                repo_track['commits'][original_file_path].append(generate_modified_file_entry_track(
                                    modified_file,
                                    commit,
                                    commit_idx,
                                    methods_before,
                                    {},
                                    methods_before,
                                    [],
                                    []
                                ))

                                if not original_file_path in repo_track['track']:
                                    repo_track['track'][original_file_path] = []
                                else:
                                    new_track_set = [m for m in repo_track['track'][original_file_path] if m['signature'] not in methods_before.keys()]
                                    assert len(new_track_set) == 0
                                    repo_track['track'][original_file_path] = new_track_set
                    commit_idx += 1
                    end_commit = time.time()
                    miner_stats[repo["url"]]["time_commits"].append({
                        "commit": commit.hash,
                        "time": end_commit - start_commit,
                        "message": "Commit analyzed successfully.",
                        "result": "success"
                    })
                else:
                    logger.log(f'Skipping commit {commit.hash} not in main branch')
            except Exception as ex:
                logger.log(f'Error: {repr(ex)}', logging.ERROR)
                miner_stats[repo["url"]]["time_commits"].append({
                    "commit": commit.hash,
                    "time": time.time() - start_commit,
                    "message": repr(ex),
                    "result": "error"
                })
                continue
        end_repo = time.time()
        miner_stats[repo["url"]]["time"] = end_repo - start_repo
        miner_stats[repo["url"]]["processed_commits"] = commit_idx + 1
        # Save the mined data
        if not os.path.exists(os.path.join(output_path, "miner", f"{'/'.join(repo['name'].split('/')[:-1])}")):
            os.makedirs(os.path.join(output_path, "miner", f"{'/'.join(repo['name'].split('/')[:-1])}"))
        if not os.path.exists(os.path.join(output_path, "stats", f"{'/'.join(repo['name'].split('/')[:-1])}")):
            os.makedirs(os.path.join(output_path, "stats", f"{'/'.join(repo['name'].split('/')[:-1])}"))
        with open(os.path.join(output_path, "miner", f"{repo['name']}.json"), "w") as json_file:
            json.dump(repo_track, json_file, indent=4)
        # Save the miner stats
        with open(os.path.join(output_path, "stats", f"{repo['name']}_stats.json"), "w") as json_file:
            json.dump(miner_stats, json_file, indent=4)