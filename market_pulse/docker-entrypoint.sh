#!/usr/bin/env bash
set -euo pipefail

VENV_PATH="${MARKET_PULSE_VENV_PATH:-/app/market_pulse/.venv_docker}"
REQUIREMENTS_FILE="${MARKET_PULSE_REQUIREMENTS_FILE:-/app/market_pulse/requirements.txt}"

clear_venv() {
  mkdir -p "${VENV_PATH}"
  find "${VENV_PATH}" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
}

if [[ -e "${VENV_PATH}/bin/python" ]] && ! "${VENV_PATH}/bin/python" --version >/dev/null 2>&1; then
  echo "Existing virtual environment at ${VENV_PATH} is not compatible with this container"
  clear_venv
fi

if [[ ! -x "${VENV_PATH}/bin/python" ]]; then
  echo "Creating Python virtual environment at ${VENV_PATH}"
  clear_venv
  python -m venv "${VENV_PATH}"
fi

echo "Installing Python dependencies from ${REQUIREMENTS_FILE}"
"${VENV_PATH}/bin/python" -m pip install --no-cache-dir -r "${REQUIREMENTS_FILE}"

echo "Upgrading yfinance to the latest available version"
"${VENV_PATH}/bin/python" -m pip install --no-cache-dir --upgrade yfinance

export PATH="${VENV_PATH}/bin:${PATH}"
export VIRTUAL_ENV="${VENV_PATH}"

exec "$@"
