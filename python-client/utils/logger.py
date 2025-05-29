import logging
import sys
from datetime import datetime

def setup_logger(name="NodeClient", level=logging.INFO):
    """Setup logger for the node client"""
    
    # Create logger
    logger = logging.getLogger(name)
    logger.setLevel(level)
    
    # Create console handler
    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setLevel(level)
    
    # Create formatter
    formatter = logging.Formatter(
        '%(asctime)s - %(name)s - %(levelname)s - %(message)s'
    )
    console_handler.setFormatter(formatter)
    
    # Add handler to logger
    if not logger.handlers:
        logger.addHandler(console_handler)
    
    return logger

# Usage example:
# from utils.logger import setup_logger
# logger = setup_logger()
# logger.info("Node client starting...")