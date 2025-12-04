#!/bin/zsh
if [ -z "$MARKET_PULSE_PATH" ]; then
  echo "Error: MARKET_PULSE_PATH environment variable is not set."
  exit 1
fi

echo "Using path: $MARKET_PULSE_PATH"
cd $MARKET_PULSE_PATH

if [ -z "$MARKET_PULSE_VENV_ACTIVATE_PATH" ]; then
  MARKET_PULSE_VENV_ACTIVATE_PATH="venv/bin/activate"
fi
source ${MARKET_PULSE_VENV_ACTIVATE_PATH}

pip install --upgrade yfinance > /dev/null 2>&1

cmd="python -m main.main $@ 2>&1"
echo "Running: $cmd"
output=$(python -m main.main "$@" 2>&1)
echo "$output"

if echo "$output" | grep -q -i "Failed to update option data for symbols"; then
  exit 1
fi

exit 0
