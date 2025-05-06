import json
import os
import random

# === CONFIG ===
input_folder = "resources/input"
output_file = "resources/output/overall_distribution.json"
TEST_CASES_PER_FILE = 2
RANDOM_SEED = 42
MAX_ATTEMPTS = 100
SIMILARITY_THRESHOLD = 0.1  # Mean absolute diff threshold

def load_test_cases():
    test_cases_by_file = {}
    for filename in os.listdir(input_folder):
        if filename.endswith(".json"):
            with open(os.path.join(input_folder, filename)) as f:
                content = json.load(f)
                if isinstance(content, dict):
                    content = [content]
                test_cases_by_file[filename] = content
    return test_cases_by_file

def compute_statistics(test_cases):
    total_assertions = 0
    total_length_chars = 0
    total_length_lines = 0
    total_variables = 0
    total_method_calls = 0
    test_count = len(test_cases)

    for test in test_cases:
        assertions = test.get("numberOfAssertions", 0)
        length_chars = test.get("testLength", 0)
        length_lines = length_chars // 80 # https://stackoverflow.com/questions/6724945/should-we-keep-80-characters-per-line-in-java
        variables = test.get("numberOfVariables", 0)
        method_calls = test.get("numberOfMethodCalls", 0)

        total_assertions += assertions
        total_length_chars += length_chars
        total_length_lines += length_lines
        total_variables += variables
        total_method_calls += method_calls

    return {
        "totalNumberOfTestCases": test_count,
        "totalNumberOfAssertions": total_assertions,
        "totalPrefixLengthChars": total_length_chars,
        "totalPrefixLengthLines": total_length_lines,
        "totalVariablesInPrefix": total_variables,
        "totalMethodCallsInPrefix": total_method_calls,
        "averagePerTestCase": {
            "assertions": total_assertions / test_count if test_count else 0,
            "prefixLengthChars": total_length_chars / test_count if test_count else 0,
            "prefixLengthLines": total_length_lines / test_count if test_count else 0,
            "variables": total_variables / test_count if test_count else 0,
            "methodCalls": total_method_calls / test_count if test_count else 0
        }
    }

def similarity_score(stats1, stats2):
    keys = stats1["averagePerTestCase"].keys()
    diffs = [
        abs(stats1["averagePerTestCase"][k] - stats2["averagePerTestCase"][k]) 
        for k in keys
    ]
    return sum(diffs) / len(diffs)

def generate_random_subset(test_cases_by_file):
    subset = []
    metadata = []

    for filename, cases in test_cases_by_file.items():
        sample_size = min(TEST_CASES_PER_FILE, len(cases))
        selected = random.sample(cases, sample_size)
        subset.extend(selected)
        for test in selected:
            metadata.append({
                "file": filename,
                "signature": test.get("signature", "unknown")
            })

    return subset, metadata

def main():
    random.seed(RANDOM_SEED)
    data = load_test_cases()

    all_test_cases = [test for file_cases in data.values() for test in file_cases]
    overall_stats = compute_statistics(all_test_cases)

    attempts_log = []
    best_attempt = None
    best_score = float("inf")

    for attempt in range(MAX_ATTEMPTS):
        subset, metadata = generate_random_subset(data)
        subset_stats = compute_statistics(subset)
        score = similarity_score(overall_stats, subset_stats)

        attempt_data = {
            "attempt": attempt + 1,
            "subsetStats": subset_stats,
            "similarityScore": score,
            "selectedTests": metadata
        }

        attempts_log.append(attempt_data)

        if score < best_score:
            best_score = score
            best_attempt = {
                "attempt": attempt + 1,
                "subsetStats": subset_stats,
                "similarityScore": score,
                "testCaseCount": len(subset),
                "selectedTests": metadata
            }

        if score <= SIMILARITY_THRESHOLD:
            break

    output = {
        "config": {
            "testCasesPerFile": TEST_CASES_PER_FILE,
            "maxAttempts": MAX_ATTEMPTS,
            "similarityThreshold": SIMILARITY_THRESHOLD
        },
        "overallStatistics": overall_stats,
        "acceptedSubsetStatistics": best_attempt,
        "subsetSelectionAttempts": attempts_log
    }

    os.makedirs(os.path.dirname(output_file), exist_ok=True)
    with open(output_file, "w") as f:
        json.dump(output, f, indent=2)

    print(f"✅ Output saved to {output_file}")

if __name__ == "__main__":
    main()