import csv
import time
import re
import os

# Set this to True to use Ollama, or False to use OpenAI ChatGPT API
USE_OLLAMA = True

# If using OpenAI, make sure to set your API key first
if not USE_OLLAMA:
    import openai
    
    openai.api_key = os.getenv("OPENAI_API_KEY") # export OPENAI_API_KEY="your-openai-api-key"
    gpt_llm = "gpt-4.1"  
    output_csv_path = f'output_{gpt_llm}.csv'

if USE_OLLAMA:
    from ollama import generate
    ollma_llm = "deepseek-r1" #'mistral-small3.1:24b' #'phi4:14b'#"qwen2.5-coder:32b"  
    print(f"Using Ollama model: {ollma_llm}")
    output_csv_path = f'output_{ollma_llm}.csv'

# Define paths
input_csv_path = 'samples.csv'

def extract_assertion(text):
    """
    Extracts the first non-empty assertion value from the text using regex.
    Returns the extracted string or an empty string if none found.
    Handles both <assertion> and <keep> tags.
    """
    # Find all <assertion> and <keep> matches
    matches = re.findall(r'<(assertion|keep)>(.*?)</\1>', text, re.DOTALL)
    
    for tag, match in matches:
        cleaned = match.strip()
        if cleaned:
            return cleaned
    return ''


# List of query templates
template_paths = [
    './zero-shot/query-template-1.txt',
    './zero-shot/query-template-2.txt',
    './zero-shot/query-template-3.txt',
    './few-shot/query-template-1.txt',
    './few-shot/query-template-2.txt',
    './few-shot/query-template-3.txt',
    './chain-of-thought/query-template-1.txt',
    './chain-of-thought/query-template-2.txt',
    './chain-of-thought/query-template-3.txt',
]

# Load all templates into memory
templates = {}
for path in template_paths:
    with open(path, 'r', encoding='utf-8') as f:
        templates[path] = f.read()

# Process input and write output
with open(input_csv_path, 'r', newline='', encoding='utf-8') as infile, \
     open(output_csv_path, 'w', encoding='utf-8') as outfile:

    reader = csv.DictReader(infile)
    fieldnames = ['query', 'expected', 'generated_regex_extracted', 'generated_raw', 'match', 'template_used']
    writer = csv.DictWriter(outfile, fieldnames=fieldnames)
    writer.writeheader()

    for row in reader:
        try:
            tp = row['tp']
            context = row['context']
            tgt = row['tgt'].strip()
            print(f"\n")

            for template_path, template_str in templates.items():
                
                query = template_str.replace('<QUERY_INPUT>', tp + "\n\n" + context)

                start_time = time.time()

                if USE_OLLAMA:
                    response = generate(
                        model=ollma_llm,
                        prompt=query,
                        options={
                            "seed": 42,
                            "num_predict": 500
                        }
                    )
                    generated = response['response'].strip()
                else:
                    client = openai.OpenAI()
                    response = client.responses.create(
                        model="gpt-4.1",
                        input=query,
                )
                    generated = response.output_text
                    print(f"Generated: {generated}")

                end_time = time.time()
                cleaned_generated = extract_assertion(generated)
                match = (tgt.strip() == cleaned_generated)

                print(f"Is an exact match? {match}. {template_path} for {tgt}")

                writer.writerow(
                    {
                    'query': query,
                    'expected': tgt,
                    'generated_regex_extracted': cleaned_generated,
                    'generated_raw': generated,
                    'match': match,
                    'template_used': template_path
                }
                )
                #break
        except Exception as e:
            print(f"Error processing row: {row}, error: {e}")
            continue

        #break  # Only process the first row