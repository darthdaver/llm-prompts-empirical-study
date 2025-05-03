import os
import re
import sys
import logging
from transformers import AutoTokenizer
sys.path.append(os.path.join(os.path.dirname(__file__), ".."))
from utils.Logger import Logger

logger = Logger("EWash", "ewash")

class EWash:
    """
    Implement the whole process to generate the input string for the model using the E-WASH approach, progressively
    integrating the information about the invoked methods, the test class, and the focal class, only if the whole
    information fits in the input length limit of the model.
    The approach supported are:
        - Cascade: the information is integrated in the order defined in the configuration file (e.g. given the order
        [test prefix, test class, focal class ], means that the information (signature, javadoc and body) of the methods
        of the test prefix is added first, then the information of the fields and methods of the test class, and finally
        the information of the fields and methods of the focal class).
        - Incremental: the information is integrated progressively adding, in order of preference (e.g. [test prefix,
        test class, focal class ]) and in order of information type (e.g. [signature, javadoc, body]), the current
        information type of each element (test prefix, test class, focal class), then the next information type of the
        test_prefix, the test class and the focal class, and so on.
    The class support the generation of the whole input string if the information progressively added with the selected
    approach fits in the input length limit of the model, otherwise the input string only the information added before the
    limit was exceeded.
    """
    def __init__(self, tp, tc, fc, ju_v, intros, config):
        """
        Initialize the E-wash class with the information about the test prefix, the test class, the focal class, and the
        target oracle to generate. The configuration file for the E-WASH approach can be provided as an argument, otherwise
        the default configuration file is loaded. The tokenizer of the model used to encode the input is initialized from
        the configuration file.
        :param tp: the test prefix to process
        :param tc: the test class where the test prefix is defined
        :param fc: the focal class corresponding to the test class
        :param ju_v: the version of Junit used in the test class
        :param intros: the intro comments for the sections of the input
        :param config: the configuration object
        """
        self.counter = 0
        self.ewash_config = config
        self.tokenizer = AutoTokenizer.from_pretrained(self.ewash_config['tokenizer'])
        self.ewash_dict = self.initialize_ewash(intros, tp, tc, fc, ju_v)

    def count_tokens(self, input_text):
        """
        Count the number of tokens in the input text encoding it with the model's tokenizer (considering the special tokens).
        :param input_text: the input text to encode
        :return: the number of tokens in the input text
        """
        # Tokenize the input text encoding it with the model's tokenizer (not considering the special tokens)
        tokens = self.tokenizer.encode(input_text, add_special_tokens=False)
        # Return the number of tokens in the input text
        return len(tokens)

    @staticmethod
    def tuple_info_2_str(t):
        """
        Convert the tuple containing the information (javadoc, signature, body) about the method or field to a string.
        :param t: the tuple containing the information (javadoc, signature, body) about the method or field
        :return: the string containing the information about the method or field
        """
        javadoc = t[0] + "\n" if len(t[0]) > 0 else ""
        signature = t[1]
        body = t[2]
        return f"{javadoc}{signature}{body}"

    @staticmethod
    def tuples_info_2_str(tuples):
        """
        Convert the list of tuples containing the information (javadoc, signature, body) about the methods or fields to a string.
        :param tuples: the list of tuples containing the information (javadoc, signature, body) about the methods or fields
        :return: the string containing the information about the methods or fields
        """
        return f"\n\n".join([EWash.tuple_info_2_str(t) for t in tuples if t[0] != "" or t[1] != "" or t[2] != ""]) + "\n"

    def update_tupled_elements(self, info_type, original_elements, tupled_elements):
        """
        Update the list of tuples of the fields or methods with the given information type, adding the information to the
        input string progressively, only if the whole information fits in the input length limit of the model. If the input
        length limit is exceeded, the methods don't stop the processing of the remaining elements that could not exceed
        the limit. The method returns a tuple containing the updated counter of the tokens in the input (after adding the
        new pieces of information), a boolean indicating if the input length limit was exceeded at least once during the
        update of the tuples, and a boolean indicating if at least one element was added to the input.
        :param info_type: the type of information to add (signature, javadoc, body)
        :param original_elements: the list of the original elements (fields or methods) to process
        :param tupled_elements: the list containing the tuples of the fields/methods that are progressively enriched with
        :return: a tuple containing a boolean indicating if the input length limit was exceeded at least once during the
        update of the tuples, and a boolean indicating if at least one element was added to the input.
        """
        # Initialize flag to check if at least one element was added to the input. It is important to avoid adding intro comment
        # if no element was added to the input
        element_added = False
        exceeded_limit = False
        # Iterate over the elements and add them sequentially to the input
        for i, el in enumerate(original_elements):
            t = tupled_elements[i]
            # Check if the input length limit is not exceeded before adding the current field
            try:
                self.update_counter(el[info_type], action="add")
                # Update the flag: at least one field was added to the input
                element_added = True
            except ValueError:
                exceeded_limit = True
                # Do not add the current field to the input, since the input length limit would be exceeded
                continue
            if info_type == 'javadoc':
                # When adding the javadoc, the signature must be always present in the tuple
                # (otherwise the javadoc alone is not identifiable)
                # tuple = (f"{el[info_type]}\n", f"{el['signature']}\n", tuple[2])
                clean_javadoc = "\n".join([ " " + l.strip() if l.strip().startswith("*") else l.strip() for l in el[info_type].split("\n")])
                t = (clean_javadoc, t[1], t[2])
            elif info_type == 'signature' or info_type == 'fullSignature':
                t = (t[0], f"{el[info_type]}", t[2])
            elif info_type == 'body':
                # When adding the body, the signature must be always present in the tuple
                # (otherwise the body alone is not identifiable)
                # tuple = (tuple[0], el['signature'], f"{el[info_type].replace(el['signature'],"")}\n")
                t = (t[0], t[1], f"{el[info_type]}")
            tupled_elements[i] = t
        return exceeded_limit, element_added

    @staticmethod
    def normalize_section(section):
        """
        Normalize the section and its attributes to conform to standard of the e-wash dictionary and configuration file.
        :param section: the section to process (test prefix, test class, or focal class)
        :return: the normalized section with the attributes in the expected format to be processed by the E-WASH approach
        """
        normalized_section = {}
        for s_type in section.keys():
            if s_type == 'fields':
                if not "fields" in normalized_section:
                    normalized_section['fields'] = []
                fields = section[s_type]
                if len(fields) > 0:
                    if "fullSignature" in section[s_type][0]:
                        # Normalize the label "fullSignature" to "fields" for the fields in the section
                        normalized_section[s_type] = [{"signature": f["fullSignature"]} for f in section[s_type]]
                    elif "signature" in section[s_type][0]:
                        # If already normalized, return the fields
                        normalized_section[s_type] = section[s_type]
                    else:
                        # Raise an error if the labels in the section are unexpected
                        raise ValueError(f"Unexpected labels ({', '.join(section[s_type])}) in the section {section} for the type {s_type}")
            elif s_type in ["invokedMethods", "methods", "auxiliaryMethods", "setupTeardownMethods"]:
                if not "methods" in normalized_section:
                    normalized_section['methods'] = []

                for m in section[s_type]:
                    if m['body'] is None:
                        normalized_section['methods'].append({ **m, "body": "" })
                    else:
                        m['body'] = m['body'].replace(m['signature'], "").strip()
                        m['body'] = m['body'] if not m['body'] == ";" else ""
                        normalized_section['methods'].append(m)
            elif s_type in ['identifier', 'package', 'packageIdentifier', 'signature', 'javadoc', 'body']:
                if s_type in ['identifier', 'signature']:
                    # Remove split pattern from the signature of test prefix to avoid the model to learn patterns from the split number
                    split_pattern = r'_split_\d+'
                    normalized_section[s_type] = re.sub(split_pattern, '', section[s_type])
                elif s_type == 'body':
                    section[s_type] = section[s_type].replace(section['signature'], "").strip()
                    normalized_section[s_type] = section[s_type] if not section[s_type] == ";" else ""
                else:
                    # Return the body of the section
                    normalized_section[s_type] = section[s_type]
            elif s_type in ['nextPossibleProjectTokens', 'nextPossibleJavaTokens', 'nextPossibleGenericTokens']:
                # Remove redundant information from the section label
                ns_type = s_type.replace("nextPossible", "")
                # Normalize Java to Python label
                ns_type = ''.join([f"_{c.lower()}" if c.isupper() else c for c in ns_type[0].lower() + ns_type[1:]])
                normalized_section[ns_type] = section[s_type]
        return normalized_section

    def initialize_ewash(self, intros, tp, tc, fc, ju_v):
        """
        Initialize the dictionary with the structured information of the datapoint to assemble the model input of the datapoint
        :param intros: the intro comments for the sections of the input
        :param tp: the test prefix to process
        :param tc: the test class where the test prefix is defined
        :param fc: the focal class corresponding to the test class
        :param ju_v: the version of Junit used in the test class
        :return: a dictionary containing the structured information of the datapoint, divided per test prefix, test class,
        and focal class. The dictionary contains the original information of the test prefix, test class, and focal class,
        the intro comments for the sections, and initializes the tuples of the fields and methods that will be progressively
        enriched with signatures, javadocs, and bodies. It also initializes the intro comments for each section of the input
        with flag False (the comments will be added only if at least a piece of information of one element in the section
        will be added to the final input). Finally, the dictionary contains the base input string (always present in the input).
        If the configuration file is set to 'tokens' generation, the dictionary contains also the lists of candidate tokens
        to add to the input, divided by type.
        """
        # Initialize ewash dictionary
        ewash_dict = {}
        # Generate the base input string (always present in the input)
        # Add the test prefix to the input (src) (removing the target oracle to produce from the body)
        # Add the tokens of the new base input string to the counter
        base_src = self.generate_base_src(intros, tp, ju_v)
        try:
            self.update_counter(base_src)
        except ValueError:
            err_msg = "The base input (test prefix + assertion mask [+ tokens]) exceeds the input length limit."
            logger.log(err_msg, logging.ERROR)
            raise ValueError(err_msg)
        # Store the base input string in the ewash dictionary
        ewash_dict['base_src'] = base_src
        # Store the junit version
        ewash_dict['junit_version'] = ju_v
        # Iterate over the sections of the input (test prefix, test class, focal class)
        for section_id in self.ewash_config['sections']:
            # Get the information about the current section to process (test prefix, test class, or focal class)
            current_section = tp if section_id == 'test_prefix' else tc if section_id == 'test_class' else fc
            ewash_dict[section_id] = self.initialize_section(section_id, current_section, intros[section_id])
        # Return the initialized ewash dictionary
        return ewash_dict

    def generate_base_src(self, intros, tp, ju_v):
        """
        Generate the base input string for the model using the E-WASH approach, assembling the test prefix and the assertion
        mask (always present in the input). The method returns the base input string with the test prefix and the assertion mask
        or raise an exception if the input length limit is exceeded.
        :param intros: the intro comments for the sections of the input
        :param tp: the test prefix to process
        :param ju_v: the version of Junit used in the test class
        :return: the base input string for the model using the E-WASH approach
        :raise: ValueError if the base input (test prefix + assertion mask) exceeds the input length limit
        :raise: ValueError if the target oracle is not found in the test prefix and the configuration file is set to 'training'
        """
        # Add the test prefix to the input (src) (removing the target oracle to produce from the body)
        base_src = "// Test prefix\n"
        base_src += f"{tp['signature']} "
        base_src += f"{tp['body'].replace('/*<MASK_PLACEHOLDER>*/', self.ewash_config['mask_token'])}\n\n"
        # Add Junit Version
        base_src += "// Junit Version\n"
        base_src += f"{ju_v}\n\n"
        # Return the base input string
        return base_src

    def update_base_src(self, intros, tp, ju_v):
        """
        Update the base input string in the ewash dictionary already initialized.
        :param intros: the intro comments for the sections of the input
        :param tp: the test prefix to process
        :param ju_v: the version of Junit used in the test class
        :raise: ValueError if the base input exceeds the input length limit
        """
        # Generate the base input string (always present in the input)
        # Add the test prefix to the input (src) (removing the target oracle to produce from the body)
        base_src = self.generate_base_src(intros, tp, ju_v)
        # Subtract the tokens of the old base input string from the counter
        self.update_counter(self.ewash_dict['base_src'], action="subtract")
        # Add the tokens of the new base input string to the counter
        try:
            self.update_counter(base_src)
        except ValueError:
            # Re-add the tokens of the old base input string to the counter
            self.update_counter(self.ewash_dict['base_src'], action="add")
            raise ValueError("The base input (test prefix + assertion mask [+ tokens]) exceeds the input length limit.")
        # Store the base input string in the ewash dictionary
        self.ewash_dict['base_src'] = base_src

    def update(self, intros, tp, ju_v, update_section=False):
        """
        Update the E-WASH dictionary with the new information about the test prefix and the oracle to generate.
        Update the base input string (test prefix + assertion mask).
        :param intros: the intro comments for the test prefix section of the input
        :param tp: the test prefix to process
        :param ju_v: the version of Junit used in the test class
        :param update_section: a flag indicating if the section should be updated (True) or not (False). If True, the
        current section is removed and replaced with the new one.
        """
        # Generate the base input string (always present in the input)
        # Add the test prefix to the input (src) (removing the target oracle to produce from the body)
        self.update_base_src(intros, tp, ju_v)
        if update_section:
            # Remove the information of the test prefix from the ewash dictionary
            self.remove_section_info('test_prefix')
            # Update the test prefix in the ewash dictionary
            self.ewash_dict['test_prefix'] = self.initialize_section('test_prefix', tp, intros['test_prefix'])

    def update_counter(self, content, action="add"):
        """
        Update the counter of the tokens in the input string.
        :param content: the content to add or subtract to the input string
        :param action: the action to perform on the counter (add or subtract)
        :return: the updated counter of the tokens in the input string
        :raise: ValueError if the input length limit is exceeded
        """
        # Count the number of tokens in the content to add to the input string
        num_tokens = self.count_tokens(content)
        # Compute new counter
        if action == "add":
            new_counter = self.counter + num_tokens
        elif action == "subtract":
            new_counter = self.counter - num_tokens
        else:
            raise ValueError(f"Action {action} not supported.")
        if new_counter > self.ewash_config['max_length']:
            # Raise an error if the input length limit is exceeded
            raise ValueError(f"The input length limit is exceeded.")
        self.counter = new_counter

    def initialize_section(self, section_id, section, intros):
        # Initialize the section in the ewash dictionary
        return {
            # Store the original section in the ewash dictionary
            "original": section,
            # Set the intro comments for the current section
            "intros": intros,
            # Initialize the list containing the tuples of the fields/methods that are progressively enriched with
            # signatures, javadocs and bodies. The first element of the tuple is the javadoc, the second is the
            # signature, and the third is the body (the order reflects the order of the information in a java class)
            "tuples": {t: [("", "", "") for _ in section[t]] for t in self.ewash_config[section_id].keys()}
        }

    def run(self, sections, update_section=False):
        """
        Run the E-WASH approach to generate the input string for the model. The methods collect the information and then
        generate the input string for the model using the E-WASH approach. The method returns a tuple containing the input
        string generated, the flag indicating if the input length limit was exceeded at least once, and the number of
        tokens in the final input.
        :param collect_info: a flag indicating if the information should be collected before generating the input string
        :param sections: the sections to process (e.g. test prefix, test class, focal class)
        :param update_section: a flag indicating if the method is call only for specific section update. In that case, if
        the limit is exceeded, the method will raise an exception because the whole collection should be repeated from
        scratch.
        :return: a tuple containing the input string generated, the flag indicating if the input length limit was exceeded,
        and the number of tokens in the input string
        """
        # Collect e-wash information
        exceeded_limit = self.collect_ewash_info(sections, update_section)
        # Generate the input string for the model using the E-WASH approach
        ewash_str = self.generate_input()
        # Returns a tuple containing the input string generated, the flag indicating if the input length limit was exceeded,
        # and the number of tokens in the input string
        return ewash_str, exceeded_limit, self.counter

    def generate_input(self):
        """
        Generate the input string for the model using the E-WASH approach, assembling the base input with the information
        collected. The method returns the input string with the information progressively added according to the approach
        used (cascade or incremental).
        :return: the input string for the model using the E-WASH strategy selected (cascade or incremental)
        """
        # Initialize the input string with the input already provided (test prefix and assertion mask)
        ewash_str = self.ewash_dict['base_src']
        # Set the outer and inner loop to process the sections and types according to the approach used (cascade or incremental)
        outer_loop = self.ewash_config['sections'] if self.ewash_config['strategy'] == 'cascade' else self.ewash_config['types']
        inner_loop = self.ewash_config['types'] if self.ewash_config['strategy'] == 'cascade' else self.ewash_config['sections']
        for o_id in outer_loop:
            # Iterate over the elements of the current type (fields or methods)
            for i_id in inner_loop:
                section = self.ewash_dict[o_id] if self.ewash_config['strategy'] == 'cascade' else self.ewash_dict[i_id]
                s_type = i_id if self.ewash_config['strategy'] == 'cascade' else o_id
                if s_type in section['original']:
                    if section['intros'][s_type]['flag']:
                        ewash_str += section['intros'][s_type]['label']
                        ewash_str += self.tuples_info_2_str(section['tuples'][s_type]) + "\n"
        logger.log(f"E-wash string generated.", logging.INFO)
        return ewash_str

    def remove_section_info(self, section_id):
        """
        Remove the information of the section from the ewash dictionary and update the counter of the tokens in the input.
        :param section_id: the id of the section to remove from the ewash dictionary
        """
        # Get section
        section = self.ewash_dict[section_id]
        for section_type in self.ewash_config['types']:
            if section_type in section['original']:
                # Check if the intro comment for the current section type was added to the input
                if section['intros'][section_type]['flag']:
                    # Get the intro comment for the current section type
                    intro_str = section['intros'][section_type]['label']
                    # Update the counter of the tokens in the input, subtracting the intro comment
                    self.update_counter(intro_str, action="subtract")
                    # Update the flag: the intro comment for the current section type has been removed
                    section['intros'][section_type]['flag'] = False
                    # Get the original elements (fields or methods) and the tuples of the fields or methods
                    for t in section['tuples'][section_type]:
                        # Update the counter of the tokens in the input, subtracting the information of the fields or methods
                        self.update_counter(self.tuple_info_2_str(t), action="subtract")
        del self.ewash_dict[section_id]

    def collect_section_info(self, section_id, section_type, section):
        """
        Collect the information for a specific section type (fields or methods) of the section (test prefix, test class, or
        focal class).
        :param section_id: the id of the section to process (test prefix, test class, or focal class)
        :param section_type: the type of the section to process
        :param section: the section to process
        :return: a boolean indicating if the input length limit was exceeded during the collection of the information
        """
        # Initialize flag to check if the input length limit was exceeded
        exceeded_limit = False
        # Get the original elements (fields or methods) and the tuples of the fields or methods
        original_elements = section['original'][section_type]
        tupled_elements = section['tuples'][section_type]
        # Get the intro comment for the current section type
        intro_str = section['intros'][section_type]['label']
        # Add the intro comment for the current section type to the input if not already added. The intro comment will be
        # maintened in the input only if at least a piece of information of one element in the section will be added to the
        # final input, otherwise the intro comment will be removed from the input (the flag is still not updated)
        if not section['intros'][section_type]['flag']:
            try:
                self.update_counter(intro_str, action="add")
            except ValueError:
                logger.log(f"The intro comment for the {section_id} {section_type} exceeds the input length limit.", logging.INFO)
                return True
        # Check if for the current type, the section has elements to add
        if len(section['original']) == 0:
            logger.log(f"No {section_type}s to add for the {section_id}", logging.INFO)
            return False
        # Iterate over the different part of the information to add (signature, javadoc, body), in the order defined
        # in the configuration file
        for info_type in self.ewash_config[section_id][section_type]:
            logger.log(f"Adding {section_id} {section_type} {info_type}s to the input", logging.INFO)
            # Update the tuples of the fields or methods with the given information type
            exceeded_limit, element_added = self.update_tupled_elements(
                info_type,
                original_elements,
                tupled_elements
            )
            if element_added:
                # Update the flag: at least one field was added to the input
                section['intros'][section_type]['flag'] = True
        # Check if the flag for the intro comment is still False (no element was added to the input)
        if not section['intros'][section_type]['flag']:
            # Remove the intro comment for the current section type from the input as no element was added to the input
            self.update_counter(intro_str, action="subtract")
        return exceeded_limit

    def collect_ewash_info(self, sections, update_section=False):
        """
        Collect all the information to generate the input for the model using the E-WASH approach, progressively integrating
        the information, according to the strategy defined in the configuration file (cascade or incremental). The method
        simulates the generation of the input string integrating the information until the input length limit of the model is
        exceeded or all the information is added to the input string. The method returns a boolean indicating if the input
        length limit was exceeded during the collection of the information.
        :param sections: the sections to process (e.g. test prefix, test class, focal class)
        :param update_section: a flag indicating if the method is call only for specific section update. In that case, if
        the limit is exceeded, the method will raise an exception because the whole collection should be repeated from
        scratch.
        :return: A boolean indicating if the input length limit was exceeded during the collection of the information and
        the simulation of the input string with the information progressively added.
        """
        # Initialize flag to check if the input length limit was exceeded
        exceeded_limit = False
        # Set the outer and inner loop to process the sections and types according to the approach used (cascade or incremental)
        outer_loop = self.ewash_config['sections'] if self.ewash_config['strategy'] == 'cascade' else self.ewash_config['types']
        inner_loop = self.ewash_config['types'] if self.ewash_config['strategy'] == 'cascade' else self.ewash_config['sections']
        # Iterate over the outer loop. If the strategy used is cascade the outer loop contains the sections, in order of
        # precedence (e.g. test prefix, the test class, and the focal class). Otherwise, if the strategy used is incremental
        # the outer loop contains the types to process (fields or methods) for each section, in order of preference.
        for o_id in outer_loop:
            # Iterate over the elements of the current type (fields or methods)
            for i_id in inner_loop:
                section_id = o_id if self.ewash_config['strategy'] == 'cascade' else i_id
                if section_id in sections:
                    section = self.ewash_dict[o_id] if self.ewash_config['strategy'] == 'cascade' else self.ewash_dict[i_id]
                    s_type = i_id if self.ewash_config['strategy'] == 'cascade' else o_id
                    if s_type in section['original']:
                        result = self.collect_section_info(section_id, s_type, section)
                        if result:
                            if update_section:
                                logger.log(f"Updating section {section_id} {s_type} exceeds the input length limit.", logging.ERROR)
                                raise ValueError(f"Updating section {section_id} {s_type} exceeds the input length limit.")
                            exceeded_limit = True
        logger.log(f"E-wash processing completed.", logging.INFO)
        return exceeded_limit
