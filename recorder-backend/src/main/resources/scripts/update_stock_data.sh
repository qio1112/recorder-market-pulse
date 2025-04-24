#!/bin/zsh
if [ -z "$MARKET_PULSE_PATH" ]; then
  echo "Error: MARKET_PULSE_PATH environment variable is not set."
  exit 1
fi

echo "Using path: $MARKET_PULSE_PATH"
cd $MARKET_PULSE_PATH
source venv/bin/activate

output=$(python -m main.main 2>&1)  # Capture output in a variable
echo "$output"

if echo "$output" | grep -q -i "Failed to update option data for symbols"; then
  exit 1
fi

exit 0
