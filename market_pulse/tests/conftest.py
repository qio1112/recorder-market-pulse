import sys
from pathlib import Path


MARKET_PULSE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(MARKET_PULSE_ROOT))
