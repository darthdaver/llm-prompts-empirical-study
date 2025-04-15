import logging
import os
import sys

class Logger:
    """
    Utility class for logging messages to the console and save the logs on file.
    """
    def __init__(self, id: str, filename: str, level: int = logging.INFO):
        """
        Initialize the logger with the name and the logging level.
        Args:
            id (`str`):
                The name of the logger
            filename (`str`):
                The name of the file where to save the logs
            level (`int`):
                The logging level
        """
        logs_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "logs")
        if not os.path.exists(logs_path):
            os.makedirs(logs_path)
        self.logger = logging.getLogger(id)
        # Create a file handler
        file_handler = logging.FileHandler(os.path.join(logs_path, f'{filename}.log'))
        file_handler.setLevel(logging.DEBUG)
        # Create a console handler
        console_handler = logging.StreamHandler()
        console_handler.setLevel(logging.INFO)
        logging.basicConfig(
            level=level,
            format="[%(levelname)s] | [%(name)s] | %(message)s",
            handlers=[file_handler, console_handler]
        )

    def log(self, message: str, level: int = logging.INFO):
        """
        Log the message with the specified logging level.
        Args:
            message (`str`):
                The message to log
            level (`int`):
                The logging level
        """
        self.logger.log(level, message)