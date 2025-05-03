from dataclasses import dataclass, field
from typing import Optional

@dataclass
class ModelArgs:
    model_name_or_path: str = field(
        metadata={"help": "Path to pretrained model, checkpoint, or model identifier."}
    )
    tokenizer_name: str = field(
        metadata={"help": "Pretrained tokenizer name or path."}
    )
    model_type: Optional[str] = field(
        default=None,
        metadata={"help": "Model type (not used, for compatibility with shell script)."}
    )