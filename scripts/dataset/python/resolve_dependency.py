import os
import re
import sys
import json
import xml.etree.ElementTree as ET

ns = {}

def parse_pom(pom_file):
    """Parse a POM file and return its root element."""
    tree = ET.parse(pom_file)
    return tree.getroot()

def get_info(root_element):
    """Extract POM information if available."""
    # Look for the direct child 'artifactId' (not the one inside <parent>)
    artifact_id = None
    raw_group_id = None
    raw_version_tag = None
    for child in root_element:
        if child.tag == f"{ns['m']}artifactId":
            artifact_id = child.text.strip()
        if child.tag == f"{ns['m']}groupId":
            raw_group_id = child
        if child.tag == f"{ns['m']}version":
            raw_version_tag = child
        if artifact_id is not None and raw_group_id is not None and raw_version_tag is not None:
            break
    if artifact_id is None:
        raise Exception(f"Error while processing artifactId (not found).")
    if raw_group_id is None or raw_version_tag is None:
        parent = root_element.find(f"{ns['m']}parent",ns)
        if parent is None:
            raise Exception(f"Error while processing parent (not found).")
        group_id, _, version = get_info(parent)
        return group_id, artifact_id, version
    else:
        group_id = raw_group_id.text.strip()
        version = raw_version_tag.text.strip()
        return group_id, artifact_id, version

def resolve_property_in_dependency(dep_attr_text, properties_dict, pom_root, pom_xml_dict):
    """Resolve a property in a dependency version."""
    # Regex pattern to match content within curly brackets preceded by $
    pattern = r'\$\{(.*?)\}'
    # Find all matches
    match = re.search(pattern, dep_attr_text)
    # Replace the property with its value
    if match:
        property_key = match.group(1)
        if property_key in properties_dict:
            return dep_attr_text.replace(f"${{{property_key}}}", properties_dict[property_key])
        else:
            parent = pom_root.find(f"{ns['m']}parent")
            if parent is None:
                raise Exception(f"Property {property_key} of dependency not found in properties")
            try:
                parent_group_id, parent_artifact_id, parent_version = get_info(parent)
                parent_id = f"{parent_group_id}:{parent_artifact_id}:{parent_version}"
                if parent_id in pom_xml_dict:
                    parent_pom_root = pom_xml_dict[parent_id]
                    properties_dict = get_properties_as_dict(parent_pom_root)
                    return resolve_property_in_dependency(dep_attr_text, properties_dict, parent_pom_root, pom_xml_dict)
                else:
                    raise Exception(f"Parent {parent_id} not found in the dictionary")
            except Exception as e:
                raise Exception(f"Error when processing parent of {properties_dict['project.groupId']}:{properties_dict['project.artifactId']} in {pom_id}")
    return dep_attr_text

def find_dependency(dependencies, group_id, artifact_id):
    """Find a dependency in the list of dependencies."""
    for dependency in dependencies:
        dep_group_id = dependency.find(f"{ns['m']}groupId",ns).text.strip()
        dep_artifact_id = dependency.find(f"{ns['m']}artifactId").text.strip()
        if dep_group_id == group_id and dep_artifact_id == artifact_id:
            return dependency
    return None

def resolve_dependency_version(group_id, artifact_id, raw_version, properties_dict, pom_root, pom_xml_dict):
    """Resolve the version of a dependency."""
    if raw_version is not None:
        return resolve_property_in_dependency(raw_version.text.strip(), properties_dict, pom_root, pom_xml_dict)
    else:
        parent = pom_root.find(f"{ns['m']}parent",ns)
        if parent is None:
            Exception(f"Version not found for dependency {group_id}:{artifact_id}. Error while processing parent (not found).")
        try:
            parent_group_id, parent_artifact_id, parent_version = get_info(parent)
            parent_id = f"{parent_group_id}:{parent_artifact_id}:{parent_version}"
            if parent_id in pom_xml_dict:
                parent_pom_root = pom_xml_dict[parent_id]
                parent_dependency = find_dependency(get_dependencies(parent_pom_root), group_id, artifact_id)
                if parent_dependency is not None:
                    raw_version = parent_dependency.find(f"{ns['m']}version")
                    properties_dict = get_properties_as_dict(parent_pom_root)
                    return resolve_dependency_version(group_id, artifact_id, raw_version, properties_dict, parent_pom_root, pom_xml_dict)
                else:
                    raise Exception(f"Dependency {group_id}:{artifact_id} not found in parent {parent_id}")
            else:
                raise Exception(f"Parent {parent_id} not found in the dictionary")
        except Exception as e:
            raise Exception(f"Error when processing parent of dependency {group_id}:{artifact_id} in {pom_id}")

def retrieve_pom_files(root_directory):
    pom_files = []
    for dirpath, _, filenames in os.walk(root_directory):
        for filename in filenames:
            if (not "processed_libs" in dirpath) and (filename.endswith('pom.xml')):
                pom_files.append(os.path.join(dirpath, filename))
    return pom_files

def get_dependencies(pom_xml):
    if pom_xml.find(f"{ns['m']}dependencyManagement") is not None:
        return pom_xml.findall(
            f"{ns['m']}dependencyManagement/" \
            f"{ns['m']}dependencies/" \
            f"{ns['m']}dependency"
        )
    else:
        return pom_xml.findall(
            f"{ns['m']}dependencies/" \
            f"{ns['m']}dependency"
        )

def get_properties_as_dict(pom_xml):
    properties = pom_xml.find(f"{ns['m']}properties")
    properties_dict = {
        "project.groupId": get_info(pom_xml)[0],
        "project.artifactId": get_info(pom_xml)[1],
        "project.version": get_info(pom_xml)[2]
    }
    if properties is not None:
        for property in properties:
            if property.text is not None:
                properties_dict[property.tag.replace(ns['m'], "")] = property.text.strip()
    return properties_dict

def resolve_pom_dependencies(pom_id, pom_xml, pom_xml_dict):
    dependencies_processed = []
    dependencies = get_dependencies(pom_xml)
    properties_dict = get_properties_as_dict(pom_xml)
    properties_dict["project.groupId"] = get_info(pom_xml)[0]
    properties_dict["project.artifactId"] = get_info(pom_xml)[1]
    properties_dict["project.version"] = get_info(pom_xml)[2]

    for dependency in dependencies:
        try:
            raw_group_id = dependency.find(f"{ns['m']}groupId")
            raw_artifact_id = dependency.find(f"{ns['m']}artifactId")
            raw_version = dependency.find(f"{ns['m']}version")
            dep_group_id = resolve_property_in_dependency(raw_group_id.text.strip(), properties_dict, pom_xml, pom_xml_dict)
            dep_artifact_id = resolve_property_in_dependency(raw_artifact_id.text.strip(), properties_dict, pom_xml, pom_xml_dict)
            dep_version = resolve_dependency_version(dep_group_id, dep_artifact_id, raw_version, properties_dict, pom_xml, pom_xml_dict)
            dependencies_processed.append(f"{dep_group_id}:{dep_artifact_id}:{dep_version}")
        except Exception as e:
            print(f"Error when processing dependency {dep_group_id}:{dep_artifact_id} in {pom_id}: {e}", file=sys.stderr)
    return dependencies_processed

def resolve_remote_repos(pom_xml):
    repositories = pom_xml.findall(
        f"{ns['m']}repositories/" \
        f"{ns['m']}repository"
    )
    remote_repos = []
    for repository in repositories:
        remote_repos.append(repository.find(f"{ns['m']}url").text.strip())
    return list(set(remote_repos))

# Example usage
if __name__ == "__main__":
    output = {}
    root_path = sys.argv[1]
    pom_file_path_list = retrieve_pom_files(root_path)
    pom_xml_dict = {}
    pom_paths_dict = {}
    remote_repos = []
    for pom_file_path in pom_file_path_list:
        pom_xml = parse_pom(pom_file_path)
        # Extract the namespace from the root tag
        m = re.match(r'\{(.*)\}', pom_xml.tag)
        ns_uri = m.group(1) if m else ''
        ns['m'] = '{' + ns_uri + '}'
        try:
            group_id, artifact_id, version = get_info(pom_xml)
            pom_xml_dict[f"{group_id}:{artifact_id}:{version}"] = pom_xml
            pom_paths_dict[f"{group_id}:{artifact_id}:{version}"] = pom_file_path
        except:
            print(f"Missing information when processing .pom file {pom_file_path}", file=sys.stderr)
    for pom_id, pom_xml in pom_xml_dict.items():
        dependencies = resolve_pom_dependencies(pom_id, pom_xml, pom_xml_dict)
        remote_repos.extend(resolve_remote_repos(pom_xml))
        output[pom_paths_dict[pom_id]] = [dependencies, list(set(remote_repos))]
    print(json.dumps(output))