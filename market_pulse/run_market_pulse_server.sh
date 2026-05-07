#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${SCRIPT_DIR}/../.env"
DOCKER_PUSH=false
VENV_PATH="${SCRIPT_DIR}/.venv_docker"

usage() {
  cat >&2 <<EOF
Usage: $0 [--docker-push] [--env-file PATH] [--venv-path PATH]

Options:
  --docker-push       Push the built image after building.
  --env-file PATH     Load environment variables from PATH.
  --venv-path PATH    Bind mount PATH as the container .venv_docker. Defaults to market_pulse/.venv_docker.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --docker-push)
      DOCKER_PUSH=true
      shift
      ;;
    --env-file)
      ENV_FILE="$2"
      shift 2
      ;;
    --venv-path)
      VENV_PATH="$2"
      shift 2
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
done

# Load env vars from .env
if [[ ! -f "$ENV_FILE" ]]; then
  echo "Error: $ENV_FILE not found. Create it with your prod secrets." >&2
  exit 1
fi
# Export every non-comment line
echo "Exporting env variables from $ENV_FILE:"
# shellcheck disable=SC2046
export $(grep -v '^#' "$ENV_FILE" | xargs)

IMAGE_NAME="${MARKET_PULSE_CONTAINER_NAME}"
CONTAINER_NAME="${MARKET_PULSE_CONTAINER_NAME}"
PORT="${MARKET_PULSE_API_PORT}"
RESOURCE_PATH="${MARKET_PULSE_RESOURCE_PATH_SERVER}"
VENV_PATH="${MARKET_PULSE_VENV_PATH_SERVER:-$VENV_PATH}"

if [[ "${VENV_PATH}" != /* ]]; then
  mkdir -p "$(dirname "${VENV_PATH}")"
  VENV_PATH="$(cd "$(dirname "${VENV_PATH}")" && pwd)/$(basename "${VENV_PATH}")"
fi

mkdir -p "${RESOURCE_PATH}"/logs
touch "${RESOURCE_PATH}"/logs/log.txt
mkdir -p "${VENV_PATH}"

echo "Building image: $IMAGE_NAME"
docker build -t "$IMAGE_NAME" "${SCRIPT_DIR}"

# Remove existing container if present
if docker ps -a --format '{{.Names}}' | grep -Eq "^${CONTAINER_NAME}$"; then
  echo "Removing old container: $CONTAINER_NAME"
  docker rm -f "$CONTAINER_NAME" >/dev/null
fi

if [[ "$DOCKER_PUSH" == "true" ]]; then
  docker login
  docker tag "${IMAGE_NAME}":latest yipeng6257/"${IMAGE_NAME}":latest
  docker push yipeng6257/"${IMAGE_NAME}":latest
fi

echo "Starting container: ${CONTAINER_NAME} on port ${PORT} (host) -> 8000 (container)"
docker network inspect recorder-net >/dev/null 2>&1 || docker network create recorder-net
docker run -d \
  --name "${CONTAINER_NAME}" \
  --network recorder-net \
  --env-file "${ENV_FILE}" \
  -p "${PORT}:8000" \
  -v "${RESOURCE_PATH}:/app/market_pulse/resources" \
  -v "${VENV_PATH}:/app/market_pulse/.venv_docker" \
  "${IMAGE_NAME}"

echo "Container ${CONTAINER_NAME} is running. Health endpoint: http://localhost:${PORT}/health, Logs: docker logs -f ${CONTAINER_NAME}"
