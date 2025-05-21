# Harnessing Prompt Engineering to Infer Concrete Test Oracles from Vanilla LLMs

This repository contains the replication package of the paper _**Harnessing Prompt Engineering to Infer Concrete Test Oracles from Vanilla LLMs**_.

## Setup

To set up and run the models with **Ollama**, the following requirements must be satisfied:

* `Python ≥ 3.10`
* `Pip`

The experiments were performed on a GPU `Nvidia A-100` with `80GB` of memory (vRAM) and `147GB` of RAM.

### 1. Environment

In order to set up the environment is not strictly necessary, but recommended to create a [_venv_](https://docs.python.org/3/library/venv.html)
or [_conda_](https://docs.conda.io/en/latest/) environment.
In the following section we will provide the general information to configure the environment.

### 1.A.1 Create and activate _venv_ environment

Follow the instructions to properly create and activate the environment

1. Open a terminal and move to the `root` directory of the project.
2. Create the _venv_ environment with `Python ≥ 3.10`:
   ```shell
   python3 -m venv .venv
   ```
   The command will generate a _venv_ environment within `.venv`.
3. Activate the _venv_ environment:
   ```shell
   source .venv/bin/activate
   ```
4. Upgrade _pip_ and _setuptools_ to the latest version:
   ```shell
   pip install --upgrade pip setuptools
   ```
### 1.A.2 Install the requirements

Install the dependencies within the active _venv_ environment, using the following command:

```shell
python3 -m pip install --upgrade -r requirements.txt --target=[path_to_repo]/.venv/lib/[python_version]/site-packages
```

where:

   * `path_to_repo` must be substituted the absolute path to the replication package repository
   * `python_version` must be substituted with the version of Python used in the environment (e.g., `python3.10`)

### 1.B.1 Install Conda

The reader can find all the instructions to properly install and setup _conda_, on the official [user guide](https://docs.conda.io/projects/conda/en/stable/user-guide/install/index.html).

We provide an example on how to install conda on Linux operating systems. The installation is relatively similar for
macOS and Windows systems.

1. Open a terminal and type the following command to download the installer:
    ```shell
    wget -P [path_to_destination_directory] [conda_download_http_link]
    ```
    * `path_to_destination_directory` - represents the destination path where the installer will be downloaded.
    * `conda_download_http_link` - is the link of the installer. You can choose the installer [here](https://docs.conda.io/en/latest/miniconda.html#linux-installers), copying the corresponding url.

2. Execute the installer:
    ```shell
    bash [fullname_of_the_installer]
    ```
   * `fullname_of_the_installer` - is the whole name of the downloaded installer (it is a _.sh_ file, like for example
     _Miniconda3-py39_23.3.1-0-Linux-aarch64.sh_)

3. Close and re-open the terminal at the end of the installation. Check _conda_ has been successfully installated typing the command:
    ```shell
    conda --version
    ```

### 1.B.2 Create and activate _conda_ environment

4. Create a new conda environment named _llm_prompts_ with Python version ≥ 3.10 and pip.
   ```bash
    conda create --name llm_prompts python=[python_version] pip
    ```
   where:
   * `python_version` must be substituted with the version of Python used in the environment (e.g., `3.10`)


5. Activate the conda environment
    ```bash
    conda activate llm_prompts
    ```

### 1.B.3 Install the requirements

6. Move to the `root` folder of the repository

7. Install all the required dependencies
    ```shell
    pip install -r requirements.txt
    ```
   
2. Bash requirements

In order to run the bash scripts, the following requirements must be satisfied:

* jq
* zip
* unzip
* git