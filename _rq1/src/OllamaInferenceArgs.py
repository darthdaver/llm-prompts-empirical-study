from dataclasses import dataclass, field
from typing import Optional

@dataclass
class OllamaInferenceArgs:
    dataset_path: str = field(
        metadata={"help": "Path to the dataset file or folder."}
    )
    output_path: str = field(
        metadata={"help": "Output path where to save the results."}
    )
    src_col: str = field(
        metadata={"help": "Column name in the dataset containing the input."}
    )
    tgt_col: str = field(
        metadata={"help": "Column name in the dataset containing the target."}
    )
    num_ctx: int = field(
        metadata={"help": "The context length. Sequences exceeding the length will be truncated or padded."}
    )
    query_path: Optional[str] = field(
        default=None,
        metadata={"help": "Path to the query request template file."}
    )
    ram_saving: Optional[bool] = field(
        default=False,
        metadata={"help": "Dummy flag to accept --ram_saving from shell script."}
    )
