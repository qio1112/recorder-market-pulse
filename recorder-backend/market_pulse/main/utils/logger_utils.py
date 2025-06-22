import logging
from logging.handlers import RotatingFileHandler
import os
from pathlib import Path

from main.utils.path_utils import get_resources_path


def setup_logging(logger_name: str, log_file_name: str = "log.txt"):
    log_location = os.path.join(get_resources_path(), "logs", log_file_name)
    log_format = '%(asctime)s - %(name)s - %(levelname)s - %(message)s'
    date_format = '%Y-%m-%d %H:%M:%S'

    # Create a logger
    logger = logging.getLogger(logger_name)
    logger.setLevel(logging.INFO)  # Set the minimal level of logging

    # Create a file handler that logs even debug messages
    handler = RotatingFileHandler(log_location, maxBytes=1048576, backupCount=5)
    handler.setLevel(logging.INFO)

    # Create a formatter and add it to the handler
    formatter = logging.Formatter(log_format, datefmt=date_format)
    handler.setFormatter(formatter)

    # Add the handler to the logger
    logger.addHandler(handler)

    # Optional: Add a stream handler to output logs to the console as well
    console_handler = logging.StreamHandler()
    console_handler.setLevel(logging.INFO)
    console_handler.setFormatter(formatter)
    logger.addHandler(console_handler)

    return logger
