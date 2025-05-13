import xml.etree.ElementTree as ET
import sys


def extract_java_version(pom_file):
    try:
        tree = ET.parse(pom_file)
        root = tree.getroot()

        # Define namespaces
        namespaces = {'m': 'http://maven.apache.org/POM/4.0.0'}

        # Check properties for Java version
        source = root.find('.//m:properties/m:maven.compiler.source', namespaces)
        target = root.find('.//m:properties/m:maven.compiler.target', namespaces)

        if source is not None and target is not None:
            return source.text, target.text
        else:
            # Check build section for compiler plugin
            plugin = root.find('.//m:plugin[m:artifactId="maven-compiler-plugin"]', namespaces)
            if plugin is not None:
                source = plugin.find('m:configuration/m:source', namespaces)
                target = plugin.find('m:configuration/m:target', namespaces)
                if source is not None and target is not None:
                    return source.text, target.text

        return None, None
    except Exception as e:
        print(f"Error: {e}")
        return None, None


def get_compatible_java_versions(target_version):
    # Map target versions to compatible Java versions
    version_map = {
        '1.7': '8',
        '1.8': '8',
        '1.9': '8',
        '10': '11',
        '11': '11',
        '12': '11',
        '13': '11',
        '14': '17',
        '15': '17',
        '16': '17',
        '17': '17',
        '18': '17',
        '19': '21',
        '20': '21',
        '21': '21'
    }
    # Check if target version is in the map
    if target_version in version_map:
        return version_map[target_version]
    return None


if __name__ == "__main__":
    # Example usage
    repo_path = sys.argv[1]
    pom_path = f"{repo_path}/pom.xml"
    source_version, target_version = extract_java_version(pom_path)

    if not target_version is None:
        compatible_target = check_compatible_version(target_version)
    elif not source_version is None:
        compatible_target = check_compatible_version(source_version)
    else:
        sys.exit(1)
    print(compatible_target)