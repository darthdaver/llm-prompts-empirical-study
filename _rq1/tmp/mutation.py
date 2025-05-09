import os
import subprocess
import xml.etree.ElementTree as ET
import pandas as pd
import re

# -------------------- CONFIG PATHS --------------------
JAVA_PROJECT_PATH = "/Users/davidemolinelli/Documents/phd/repositories/llm-prompts-empirical-study/input/github-repos/twilio/twilio-java"
CSV_PATH = "/Users/davidemolinelli/Documents/phd/repositories/llm-prompts-empirical-study/_rq1/output/inference/phi4_14b-1/oracles-datapoints-3627-0.csv"
POM_PATH = os.path.join(JAVA_PROJECT_PATH, "pom.xml")
PIT_PLUGIN_XML = '''
<plugin>
    <groupId>org.pitest</groupId>
    <artifactId>pitest-maven</artifactId>
    <version>LATEST</version>
</plugin>
'''
# ------------------------------------------------------

def ensure_pit_plugin(pom_path):
    tree = ET.parse(pom_path)
    root = tree.getroot()

    ns = {'mvn': 'http://maven.apache.org/POM/4.0.0'}
    plugins = root.find('.//mvn:plugins', ns)

    if plugins is None:
        build = root.find('mvn:build', ns)
        if build is None:
            build = ET.SubElement(root, 'build')
        plugins = ET.SubElement(build, 'plugins')

    for plugin in plugins.findall('mvn:plugin', ns):
        artifact_id = plugin.find('mvn:artifactId', ns)
        if artifact_id is not None and artifact_id.text == 'pitest-maven':
            print("PIT plugin already exists in pom.xml")
            return

    # Add plugin
    plugins.append(ET.fromstring(PIT_PLUGIN_XML))
    tree.write(pom_path)
    print("PIT plugin added to pom.xml")

def run_pitest(test_class_fqcn, target_class_fqcn):
    # ------------------ CONFIG ------------------
    java11_home = "/Users/davidemolinelli/Documents/phd/repositories/llm-prompts-empirical-study/scripts/dataset/bash/resources/sdkman/candidates/java/11.0.21-amzn"  # update as needed
    # --------------------------------------------
    # Build the mvn command
    cmd = [
        "mvn", "-e",
        "clean", "compile",
        "org.pitest:pitest-maven:mutationCoverage",
        f"-DtargetClasses={target_class_fqcn}",
        f"-DtargetTests={test_class_fqcn}",
        "-DoutputFormats=HTML",
        "-DreportDir=target/pit-reports",
        "-Dmutators=ALL"
    ]
    # Set environment to force Java 8
    env = os.environ.copy()
    env["JAVA_HOME"] = java11_home
    env["PATH"] = f"{java11_home}/bin:" + env["PATH"]
    # Run the command and capture output
    result = subprocess.run(
        cmd,
        capture_output=True,
        text=True,
        cwd=JAVA_PROJECT_PATH,
        env=env
    )
    output = result.stdout + result.stderr
    return extract_mutation_score(output)

def extract_mutation_score(pit_output):
    match = re.search(r"Generated \d+ mutations Killed \d+ \((\d+)%\)", pit_output)
    return int(match.group(1)) if match else -1

def modify_assertion(java_file_path, target_assertion, generated_assertion):
    with open(java_file_path, 'r') as f:
        code = f.read()

    if target_assertion not in code:
        print("Target assertion not found in file.")
        return False

    modified_code = code.replace(target_assertion, generated_assertion)

    with open(java_file_path, 'w') as f:
        f.write(modified_code)

    print("Assertion replaced in Java file.")
    return True

def fqcn_from_path(test_file_path):
    rel_path = os.path.relpath(test_file_path, os.path.join(JAVA_PROJECT_PATH, 'src/test/java'))
    return rel_path.replace('/', '.').replace('.java', '')

def infer_target_class_fqcn(test_fqcn):
    # Remove 'Test' suffix and assume same package
    return test_fqcn.replace("Test", "")

# -------------------- MAIN --------------------
def main():
    ensure_pit_plugin(POM_PATH)

    df = pd.read_csv(CSV_PATH, header=None)
    row = df.iloc[0]

    test_file_path = row[4]  # 5th column
    test_file_full_path = os.path.join(JAVA_PROJECT_PATH, test_file_path)
    test_fqcn = fqcn_from_path(test_file_path)
    target_class_fqcn = infer_target_class_fqcn(test_fqcn)

    print(f"Test class: {test_fqcn}")
    print(f"Target class: {target_class_fqcn}")

    print("\nRunning PIT before assertion change...")
    score_before = run_pitest(test_fqcn, target_class_fqcn)
    print(f"Mutation score before: {score_before}%")

    target_assertion = row[2]  # 3rd column
    generated_assertion = row[3]  # 4th column

    if modify_assertion(test_file_full_path, target_assertion.strip(), generated_assertion.strip()):
        print("\nRunning PIT after assertion change...")
        score_after = run_pitest(test_fqcn, target_class_fqcn)
        print(f"Mutation score after:  {score_after}%")

        diff = score_after - score_before
        print("\n📊 Mutation score change: ", end="")
        if diff > 0:
            print(f"+{diff}% (improved)")
        elif diff < 0:
            print(f"{diff}% (worsened)")
        else:
            print("No change")
    else:
        print("Failed to modify Java file. Skipping second PIT run.")

if __name__ == "__main__":
    main()