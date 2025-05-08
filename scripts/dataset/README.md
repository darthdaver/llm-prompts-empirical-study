# Dataset
The dataset generator is the submodule of the project responsible for generating the dataset with the target oracles
and the contextual information for each pair of test class and focal class within a Java project, within the github repository list
stored in the root directory of the project (`resources/github-repos.csv`).

The submodule analyzes each Java project (only the maven projects are considered) and for each couple of test class and focal class found,
it processes the test cases collected from the [test-miner](../test-miner/README.md) submodule, splitting them at any occurrence of an assertion statement
and generating the corresponding dataset of test prefixes and target assertions (including contextual information related to
the test class, the focal class and the methods invoked within the test cases.

## Setup
The current folder contains bash scripts to install the required dependencies and to run the program as a standalone application
and replicate the generation of the dataset.

Run the following command:

```shell
bash bash/utils/init.sh
```

to install the dependencies required to compile and run the program. 

In particular, the script installs:
- A local version of `SDKman`, an SDK manager to effortlessly manage multiple Java versions on Unix systems.
- A local version of the JDK `8`, `11`, `17`, and `21` used within the other scripts to process the Java projects and run the symbolic module both at development and inference time.
- A local version of `Maven` to compile the symbolic module

The script does not install anything globally, but only local versions within the project.

Some of the scripts require `Python ≥ 3.10` and exploit the dependencies installed in the root of the project `llm-prompts-empirical-study`.

Before to continue, please, follow the instructions in the section `Setup` of the corresponding [README.md](../../README.md) file
in the root `llm-prompts-empirical-study` folder.

If you are in a new terminal session, remember to activate the venv or conda environment. Move to the `scripts/dataset` folder and run the following command:

```shell
source ../../.venv/bin/activate
```

## Run

### Oracles dataset
To replicate the generation of the dataset, it is necessary to run the following command:

```shell
bash bash/generate-dataset.sh [path-to-csv-file] [resolve-dependencies]
```
By default, the script downloads and processes the Java projects listed in the `csv` file stored in the root directory 
of the project (`resources/github-repos.csv`).

However, it is possible to specify a path to a different `csv` file (as first parameter), containing the list of 
Java projects to process. The content of the `csv` file must be a list of rows, where each row is in the following format:
```csv
repository-id,repository-name
```
The second parameter is optional and specifies whether to resolve the dependencies of the Java projects, i.e. download the 
source code and the documentation (if available) of the external maven dependencies. If set to true, the script will require
a lot of time to complete, since it will download the source code and the documentation of all the dependencies of the Java projects.

To speed up the process, it is also possible to run multiple instances of the script in parallel, using the following command:

```shell
bash bash/generate-dataset-parallel.sh [num_processes] [resolve-dependencies]
```

The first parameter is the number of processes to run in parallel (default is 100), while the second is optional and specifies whether to 
resolve the dependencies of the Java projects.

**Note:** Before to run the script make sure to run the script in the `root` folder to split the original list of repositories
into smaller chunks. The script will create a folder named `github-repos-split` in the `resources` folder containing the files with the
repositories to mine. The command to run from the root of the current repository is:

```shell
python3 scripts/utils/bash/split-github-repos.sh
```

## Conventions
* JDK Version: `21`
* Build tool: `3.9.4`