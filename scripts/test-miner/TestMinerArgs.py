from dataclasses import dataclass, field
from typing import Optional

@dataclass
class TestMinerArgs:
    """
    This helper class collects all test miner-related arguments.
    """
    input_path: str = field(
        metadata={"help": "Path to the file of the CSV containing the repositories to mine"}
    )
    output_path: str = field(
        metadata={"help": "Path to the file of the CSV containing the mined repositories"}
    )
    since: str = field(
        metadata={"help": "The date from which to start the mining process. Format: MM/DD/YYYY HH:MM:SS."}
    )
    repos_file_path: Optional[str] = field(
        default=None,
        metadata={"help": "Path to the file containing the repositories to mine."}
    )
    repo_name: Optional[str] = field(
        default=None,
        metadata={"help": "The name of the repository to mine."}
    )
    repo_url: Optional[str] = field(
        default=None,
        metadata={"help": "The URL of the repository to mine."}
    )
    operation: Optional[str] = field(
        default="all",
        metadata={"help": "The test cases to mine. Options: all, added, changed."}
    )