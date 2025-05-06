import os
from dataclasses import dataclass, field

@dataclass
class EwashArgsParser:
    input:str = field(
        metadata={"help": "The path to the input file or directory (containing the files to process)."}
    )
    output:str = field(
        metadata={"help": "The path to the output directory (where to save the processed files)."}
    )
    config:str = field(
        default=os.path.join(os.path.abspath(os.path.dirname(__file__)), '..', 'resources', 'ewash_config.json'),
        metadata={"help": "The path to the configuration file for the E-WASH approach."}
    )
    intros:str = field(
        default=os.path.join(os.path.abspath(os.path.dirname(__file__)), '..', 'resources', 'ewash_intros.json'),
        metadata={"help": "The path to the dictionary of intro comments for each section of the input for the E-WASH approach."}
    )