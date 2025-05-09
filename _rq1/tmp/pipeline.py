from typing import Tuple
from litellm import completion
from models.odex import Odex
from models.mbpp import MBPP
from models.humaneval import HumanEval
from models.classeval import ClassEval
from models.codereval import CoderEval
from models.benchmark import Benchmark
from models.bigcodebench import BigCodeBench

import os, re, argparse

def get_argparser() -> argparse.ArgumentParser:
    """
    Get the configured argument parser
    """

    parser = argparse.ArgumentParser(description='optional arguments')
    parser.add_argument('--language', '-l',
                        metavar='NAME',
                        dest='language',
                        required=False,
                        type=str,
                        default="Python",
                        choices=['Python', 'Java'],
                        help='Language of the benchmark to use for the evaluation. Options: Python, Java')
    parser.add_argument('--max_tokens', '-m',
                        metavar='NUM',
                        dest='max_tokens',
                        required=False,
                        type=int,
                        default=1024,
                        help='Completion max tokens')
    parser.add_argument('--is_reasoning_model', '-r',
                        dest='is_reasoning_model',
                        type=str,
                        required=False,
                        default='False',
                        choices=['True', 'False'],
                        help='Flag to indicate if the model is a reasoning model')
    parser.add_argument('--api_base', '-a',
                        metavar='URL',
                        dest='api_base',
                        required=False,
                        type=str,
                        default=None,
                        help='API base URL for the model')
    parser.add_argument('--temperature', '-t',
                        metavar='NUM',
                        dest='temperature',
                        required=False,
                        type=float,
                        default=0.2,
                        help='Temperature for the completion')
    parser.add_argument('--verbose', '-v',
                        dest='verbose',
                        action='store_true',
                        help='Flag to enable verbose output')
    parser.add_argument('--empty_mode', '-k',
                        dest='empty_mode',
                        action='store_true',
                        help='If enabled, the benchmark will be tested against an empty solution')
    parser.add_argument('--test_mode', '-j',
                        dest='test_mode',
                        action='store_true',
                        help='If enabled, the benchmark will be tested against the canonical solution')
    parser.add_argument('--iterations', '-x',
                        metavar='NUM',
                        dest='iterations',
                        required=False,
                        type=int,
                        default=1,
                        help='Number of iterations to run the evaluation')
    parser.add_argument('--output_dir', '-o',
                        metavar='PATH',
                        dest='output_dir',
                        required=False,
                        type=str,
                        default='results',
                        help='Output directory to store the evaluation results')

    required = parser.add_argument_group('required arguments')
    required.add_argument('--model', '-i',
                        metavar='NAME',
                        dest='model',
                        required=True,
                        type=str,
                        help='Name of the model to use for code completion')
    required.add_argument('--benchmark_name', '-n',
                        metavar='NAME',
                        dest='benchmark_name',
                        required=True,
                        type=str,
                        choices=['bigcodebench', 'humaneval', 'mbpp', 'odex', 'classeval', 'codereval'],
                        help='Name of the benchmark to use for the evaluation. Options: bigcodebench, humaneval, mbpp, odex, classeval, codereval')
    
    return parser

def get_benchmark_by_name(benchmark_name: str) -> Benchmark:
    """
    Get the benchmark object by its name
    Args:
        benchmark_name (str): Name of the benchmark
        benchmarks_dir (str): Directory where the benchmark files are stored
    Returns:
        Benchmark: The benchmark object
    """
    if benchmark_name == "bigcodebench":
        return BigCodeBench()
    elif benchmark_name == "humaneval":
        return HumanEval()
    elif benchmark_name == "mbpp":
        return MBPP()
    elif benchmark_name == "odex":
        return Odex()
    elif benchmark_name == "classeval":
        return ClassEval()
    elif benchmark_name == "codereval":
        return CoderEval()
    else:
        raise ValueError(f"Invalid benchmark name: {benchmark_name}")

def parse_code_block(string: str, language: str) -> str:
    markdown_label = language.lower()
    code_pattern = fr"```{markdown_label}\n(.*?)\n```"
    match = re.search(code_pattern, string, re.DOTALL)
    if match: return match.group(1)

    generic_code_pattern = r"```\n(.*?)\n```"
    match = re.search(generic_code_pattern, string, re.DOTALL)
    if match: return match.group(1)

    if "```" in string:
        return string.split("```")[1]
    else:
        return string

def generate(messages: list[dict], language: str, **args) -> Tuple[str, object]:
    response = completion(
        messages=messages,
        **args, seed=42,
        caching=False,
        cache={"no-cache": True, "no-store": True}
    )

    result = response.choices[0].message.content
    return parse_code_block(result, language)

def print_messages(messages: list[dict]):
    for message in messages:
        print(f"---------------------------- {message['role'].upper()} ----------------------------")
        print(message['content'])
        print("------------------------------------------------------------------------------------")

def export_jsonl(row, output_file):
    with open(output_file, 'a') as f:
        f.write(row.to_json() + '\n')

if __name__ == '__main__':
    parser = get_argparser()
    args = parser.parse_args()

    model = args.model
    benchmark_name = args.benchmark_name
    language = args.language
    max_tokens = args.max_tokens
    temperature = args.temperature

    output_dir = args.output_dir
    os.makedirs(output_dir, exist_ok=True)

    print(f"===== Arguments =====")
    print(f"Model: {model}")
    print(f"Benchmark: {benchmark_name}")
    print(f"Language: {language}")
    print(f"Max tokens: {max_tokens}")
    print(f"Temperature: {temperature}")
    print(f"Is reasoning model: {args.is_reasoning_model}")
    print(f"API base: {args.api_base}")
    print(f"Test mode: {args.test_mode}")
    print(f"Output dir: {output_dir}")
    print(f"======================")

    benchmark = get_benchmark_by_name(benchmark_name)
    benchmark_df = benchmark.load_data()

    passed, num_instances = 0, len(benchmark_df)
    print(f"Loaded {num_instances} benchmarks for {language} from {benchmark_name}")

    # System prompt adapted from Reflexion (https://github.com/noahshinn/reflexion)
    system_prompt = f"""You are an AI that only responds with {language} code, NOT ENGLISH. You will be given a function signature and its docstring by the user. Write your full implementation. You always return the signature and anything that came before it in the input prompt (such as the docstring, libraries, imports, and so on) along with the full implementation of the function. Write the output in a markdown code block. For example:\n```\n<your code here>\n```"""

    completion_kwargs = {
        "model": model,
        "temperature": args.temperature,
    }
    completion_kwargs["max_completion_tokens" if args.is_reasoning_model == "True" else "max_tokens"] = args.max_tokens
    if args.api_base:
        completion_kwargs["api_base"] = args.api_base
    
    for i in range(args.iterations):
        passed = 0
        print(f"Running iteration {i+1}/{args.iterations}...")

        model_name_extracted = model.split("/")[-1].replace("-", "_")
        iteration_dir = os.path.join(output_dir, model_name_extracted, benchmark_name, f"iter_{i+1}")
        os.makedirs(iteration_dir, exist_ok=True)

        for idx, row in benchmark_df.iterrows():
            benchmark.row = row

            messages = [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": benchmark.prompt()}
                ]
            
            if args.empty_mode:
                solution = benchmark.empty_solution()
            elif args.test_mode:
                solution = benchmark.canonical_solution()
            else:
                solution = generate(messages, language, **completion_kwargs)

            if args.verbose: 
                messages.append({"role": "assistant", "content": solution})
                print_messages(messages)

            status, output = benchmark.run_tests(solution)

            if status == True: passed += 1
            print(f"\n\n## Prompt {idx+1}/{num_instances} - Current accuracy: {(passed/(idx+1))*100:.2f}% ({passed}/{idx+1})\n\n")

            # Export the results to a JSONL file in append mode
            row['evaluated_prompt'] = benchmark.prompt()
            row['evaluated_tests'] = benchmark.tests()
            row['completion'] = solution
            row['test_output'] = output
            row['passed'] = status
            export_jsonl(row, os.path.join(iteration_dir, f"result.jsonl"))