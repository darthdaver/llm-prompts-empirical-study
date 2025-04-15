import os, csv, json, sys
import time
from tree_sitter import Language, Parser
from datetime import datetime
from pydriller import Repository
from transformers import HfArgumentParser
sys.path.append(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
from src.parsers.TestMinerArgs import TestMinerArgs
from src.utils.Logger import Logger

# Setup the logger
logger = Logger("main", "test_miner")


def generate_modified_file_entry_track(mf, c, idx):
    """
    Generate a dictionary entry for the modified file in a given commit.
    :param mf: the modified file to track
    :param c: the commit containing the modified file
    :param idx: the index the commit processed in the main branch of the repository
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
        "methods_before_commit": [
            {
                "name": m.name,
                "long_name": m.long_name,
                "params": m.parameters
            } for m in mf.methods_before
        ],
        "methods_after_commit": [
            {
                "name": m.name,
                "long_name": m.long_name,
                "params": m.parameters
            } for m in mf.methods
        ],
        "changed_methods": [
            {
                "name": m.name,
                "long_name": m.long_name,
                "params": m.parameters
            } for m in mf.changed_methods
        ]
    }

if __name__ == '__main__':

    # Read the args passed to the script
    parser = HfArgumentParser(TestMinerArgs)
    miner_args = parser.parse_args_into_dataclasses()[0]
    since = datetime.strptime(miner_args.since, '%m/%d/%Y %H:%M:%S')
    input_path = miner_args.input_path
    output_path = miner_args.output_path
    logger.log(f"Mining the repositories since {miner_args.since} ...")
    # Load retrieved repositories
    with open(miner_args.repo_file_path, "r") as repo_file:
        repos = csv.DictReader(repo_file)
        ext_data_count = 0
        test_miner = {}
        miner_stats = {}
        for repo in repos:
            logger.log(f'Mining the repo name: {repo["name"]} available on {repo["url"]} ...')
            repo_track = { "url": repo["url"] , "since": miner_args.since, "commits": {}, "track": {} }
            file_path_track = {}
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
                print(f'Skipping repo {repo}: Error: {ex}')
                continue
            commit_idx = 0
            for idx, commit in enumerate(commits, 1):
                try :
                    logger.log(f'Processing commit {commit.hash} {idx}/{len(commits)})')
                    if commit.in_main_branch:
                        start_commit = time.time()
                        for modified_file in commit.modified_files:
                            new_file_path = modified_file.new_path
                            src_code = modified_file.source_code
                            methods_before_commit = modified_file.methods_before
                            methods_after_commit = modified_file.methods
                            # Check that the modified file is a test class containing test cases
                            if "@Test" in src_code or "@org.junit.Test" in src_code or "org.junit.jupiter.api.Test" in src_code:
                                # Manage deleted methods
                                deleted_methods = [
                                    {
                                        "name": b_method.name,
                                        "long_name": b_method.long_name,
                                        "params": b_method.parameters
                                    } for b_method in modified_file.methods_before if not b_method in modified_file.methods
                                ]
                                # Manage added methods
                                added_methods = [
                                    {
                                        "name": a_method.name,
                                        "long_name": a_method.long_name,
                                        "params": a_method.parameters
                                    } for a_method in modified_file.methods if not a_method in modified_file.methods_before
                                ]
                                # Manage changed methods
                                changed_methods = [
                                    {
                                        "name": c_method.name,
                                        "long_name": c_method.long_name,
                                        "params": c_method.parameters
                                    } for c_method in modified_file.changed_methods if not c_method in modified_file.methods
                                ]

                                if not modified_file.new_path in file_path_track.keys():
                                    file_path_track[modified_file.new_path] = modified_file.old_path

                                original_file_path = modified_file.new_path
                                while original_file_path != file_path_track[original_file_path]:
                                    original_file_path = file_path_track[original_file_path]
                                if not original_file_path in repo_track['commits'].keys():
                                    repo_track['commits'][original_file_path] = []
                                repo_track['commits'][original_file_path].append(generate_modified_file_entry_track(modified_file, commit, commit_idx))

                                if not original_file_path in repo_track['track']:
                                    repo_track['track'][original_file_path] = []
                                for d_m in deleted_methods:
                                    if d_m in repo_track['track'][original_file_path]:
                                        repo_track['track'][original_file_path].remove(d_m)
                                for a_m in added_methods:
                                    if not a_m in repo_track['track'][original_file_path]:
                                        repo_track['track'][original_file_path].append(a_m)
                                for c_m in changed_methods:
                                    if not c_m in repo_track['track'][original_file_path]:
                                        repo_track['track'][original_file_path].append(c_m)
                        commit_idx += 1
                        end_commit = time.time()
                        miner_stats[repo["url"]]["time_commits"].append({
                            "commit": commit.hash,
                            "time": end_commit - start_commit
                        })
                    else:
                        logger.log(f'Skipping commit {commit.hash} not in main branch')
                    end_repo = time.time()
                    miner_stats[repo["url"]]["time"] = end_repo - start_repo
                    miner_stats[repo["url"]]["processed_commits"] = commit_idx + 1
                except Exception as ex:
                    print(f'Error: {ex}')
                    continue
            # Save the mined data
            if not os.path.exists(os.path.join(output_path, "miner")):
                os.mkdirs(os.path.join(output_path, "miner"))
            if not os.path.exists(os.path.join(output_path, "stats")):
                os.mkdirs(os.path.join(output_path, "stats"))
            with open(os.path.join(output_path, "miner", f"{repo['name']}.json"), "w") as json_file:
                json.dump(repo_track, json_file, indent=4)
            # Save the miner stats
            with open(os.path.join(miner_args.output_path, "stats", f"{repo['name']}_stats.json"), "w") as json_file:
                json.dump(miner_stats, json_file, indent=4)