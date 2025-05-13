import xml.etree.ElementTree as ET
import re
import sys

PIT_PLUGIN_XML = '''
<plugin>
    <groupId>org.pitest</groupId>
    <artifactId>pitest-maven</artifactId>
    <version>LATEST</version>
</plugin>
'''

def ensure_pit_plugin(pom_path):
    """
    Ensure the PIT plugin is present in the POM file, otherwise add it and save the new file.

    Args:
        pom_path (str): The path to the POM file.
    """
    pom_tree = ET.parse(pom_path)
    pom_xml = pom_tree.getroot()
    # Extract the namespace from the root tag
    m = re.match(r'\{(.*)\}', pom_xml.tag)
    ns_uri = m.group(1) if m else ''
    ns = {'mvn': ns_uri}
    ET.register_namespace('', ns_uri)

    plugins = pom_xml.find('.//mvn:plugins', ns)
    if plugins is None:
        build = pom_xml.find('mvn:build', ns)
        if build is None:
            build = ET.SubElement(pom_xml, f'{{{ns_uri}}}build')
        plugins = ET.SubElement(build, f'{{{ns_uri}}}plugins')

    for plugin in plugins.findall('mvn:plugin', ns):
        artifact_id = plugin.find('mvn:artifactId', ns)
        if artifact_id is not None and artifact_id.text == 'pitest-maven':
            print("PIT plugin already exists in pom.xml")
            return

    plugins.append(ET.fromstring(PIT_PLUGIN_XML))
    pom_tree.write(pom_path, encoding='utf-8', xml_declaration=True)
    print("PIT plugin added to pom.xml")

if __name__ == "__main__":
    # Example usage
    repo_path = sys.argv[1]
    pom_path = f"{repo_path}/pom.xml"
    ensure_pit_plugin(pom_path)