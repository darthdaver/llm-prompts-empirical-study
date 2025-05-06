# Test Miner

The test miner is the submodule of the project that is responsible for mining Java repositories compiled with Maven build system
and collect the test cases added or changed starting from a given date, up to the latest commit.

The test mining process is necessary to guarantee that the test cases used to evaluate the vanilla LLMs on the given research
questions (RQs) have not been used in the training phase of the models leading to possible data leakage and bias in the results.

## Setup

The scripts require `Python ≥ 3.10` and exploit the dependencies installed in the root of the project `llm-prompts-empirical-study`.

Before to continue, please, follow the instructions in the section `Setup` of the corresponding [README.md](../../README.md) file
in the root `llm-prompts-empirical-study` folder.

If you are in a new terminal session, remember to activate the venv or conda environment. Move to the `scripts/test-miner` folder and run the following command:

```shell
source ../../.venv/bin/activate
```

## Run

To run the test miner, move to the `scripts/test-miner` folder and execute the following command:

```shell
bash bash/mine.sh
```

To speed up the process, you can run the script in parallel. To do so, you can use the following command:

```shell
bash bash/mine-parallel.sh [number_of_processes]
```

where:

* `number_of_processes` - is the number of processes to run in parallel. The default value is 100.

**Note:** Before to run the script make sure to run the script in the `root` folder to split the original list of repositories
into smaller chunks. The script will create a folder named `split` in the `resources` folder containing the files with the
repositories to mine. The command to run from the root of the current repository is:

```shell
python3 scripts/utils/bash/split-github-repos.sh
```