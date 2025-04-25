# RQ1

This folder contains the results of `RQ1` and the scripts to replicate them.

## Setup

The scripts require `Python ≥ 3.10` and exploit the dependencies installed in the root of the project `llm-prompts-empirical-study`.

Before to continue, please, follow the instructions in the section `Setup` of the corresponding [README.md](../README.md) file
in the root `llm-prompts-empirical-study` folder.

If you are in a new terminal session, remember to activate the venv or conda environment. Move to the `_rq1` folder and run the following command:

```shell
source ../.venv/bin/activate
```

### Ollama

Install Ollama by following the instructions in the [Ollama](https://ollama.com/download/linux) official website.

Start the Ollama server in background by running the following command:

```shell
  nohup ollama serve > log-ollama-serve.out 2>&1 & echo $! >> pid-ollama-serve.txt
```

(The script will start the Ollama server in the background and save the logs in `log-ollama-serve.out` and the process id in `pid-ollama-serve.txt`. If you want to run the process in the current terminal without saving the log, run `ollama serve &` instead).

Pull the vanilla llms used in the experiments running the command:

```shell
  bash ./bash/utils/ollama_pull_models.sh
```

## Inference

To replicate the inference task, run the following command from the current directory (`_rq1`):

```shell
  bash ./bash/inference.sh > log-inference.out 2>&1 & echo $! >> pid-inference.txt
```

(The script will start the inference process in the background and save the logs in `log-inference.out` and the process id in `pid-inference.txt`. 
If you want to run the process in the current terminal without saving the logs, run `bash ./bash/inference.sh` instead).

## Moderator

To replicate the moderator task, run the following command from the current directory (`_rq1`):

```shell
  bash ./bash/moderator.sh > log-moderator.out 2>&1 & echo $! >> pid-moderator.txt
```

(The script will start the inference process in the background and save the logs in `log-moderator.out` and the process id in `pid-moderator.txt`.
If you want to run the process in the current terminal without saving the logs, run `bash ./bash/moderator.sh` instead).
