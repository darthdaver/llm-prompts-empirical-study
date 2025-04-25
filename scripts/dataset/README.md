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
bash scripts/bash/dataset/generate-raw-oracles-dataset.sh [path-to-csv-file]
```
By default, the script downloads and processes the Java projects listed in the `csv` file stored in `scr/main/resources/dataset/m2t_repos.csv`
corresponding to the Java projects used in [Method2Test](https://github.com/microsoft/methods2test), a related work published [here](https://arxiv.org/pdf/2009.05617).

However, it is possible to specify a path to a different `csv` file (as first parameter), containing the list of 
Java projects to process. The content of the `csv` file must be a list of rows, where each row is in the following format:

```csv
project-identifier,project-url,commit-sha
```

(No header is required)

### Tokens dataset
To generate the token dataset, it is necessary to follow the following steps:

- Copy at least one datapoint of the Oracle Dataset in json format in: 
    ```symbolic-module/src/main/resources/dataset/partial-oracles/input```
- Run the file: 
    ```symbolic-module/src/main/java/star/tracto/preprocessing/PartialOracleDataset.java```
- You will find the generated token dataset in the folder: 
    ```symbolic-module/src/main/resources/dataset/partial-oracles/output/```


## Conventions
* JDK Version: `21`
* Build tool: `3.9.4`