#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ENV_FILE=".env"
BUILD_BACKEND=true
BUILD_FRONTEND=false
FRONTEND_PATH="./recorder-frontend"
DOCKER_PUSH=false
REBUILD_IMAGES=true

usage() {
  cat <<'EOF'
Usage: ./build.sh [options]
  --env-file <path>       Path to .env file (default: .env)
  --skip-backend-build    Skip building the recorder-backend JAR
  --build-frontend [path] Build frontend and copy dist to backend static (default path: ./recorder-frontend)
  --docker-push           After build, push images defined in docker-compose.yml
  --no-rebuild-images     Skip docker compose image rebuild (uses existing images)
  --help                  Show this help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      ENV_FILE="$2"
      shift 2
      ;;
    --skip-backend-build)
      BUILD_BACKEND=false
      shift
      ;;
    --build-frontend)
      BUILD_FRONTEND=true
      if [[ -n "${2-}" && "${2:0:1}" != "-" ]]; then
        FRONTEND_PATH="$2"
        shift 2
      else
        shift
      fi
      ;;
    --docker-push)
      DOCKER_PUSH=true
      shift
      ;;
    --no-rebuild-images)
      REBUILD_IMAGES=false
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE – aborting!" >&2
  exit 1
fi

echo "Loading environment from $ENV_FILE"
export $(grep -v '^#' "$ENV_FILE" | xargs)

: "${MARKET_PULSE_RESOURCE_PATH_SERVER:?MARKET_PULSE_RESOURCE_PATH_SERVER not set in env}"
: "${BACKEND_APP_LOG_PATH_SERVER:?BACKEND_APP_LOG_PATH_SERVER not set in env}"
: "${BACKEND_APP_FILE_PATH_SERVER:?BACKEND_APP_FILE_PATH_SERVER not set in env}"

echo "Ensuring resource directories exist..."
mkdir -p "${MARKET_PULSE_RESOURCE_PATH_SERVER}/logs" \
         "${MARKET_PULSE_RESOURCE_PATH_SERVER}/qdrant" \
         "${BACKEND_APP_LOG_PATH_SERVER}" \
         "${BACKEND_APP_FILE_PATH_SERVER}"
touch "${MARKET_PULSE_RESOURCE_PATH_SERVER}/logs/log.txt"

if [[ "$BUILD_FRONTEND" == "true" ]]; then
  echo "Building frontend from ${FRONTEND_PATH}..."
  if [[ ! -d "$FRONTEND_PATH" ]]; then
    echo "Error: frontend path '$FRONTEND_PATH' does not exist." >&2
    exit 1
  fi
  pushd "$FRONTEND_PATH" >/dev/null
  npm run build
  popd >/dev/null

  STATIC_DIR="./recorder-backend/src/main/resources/static"
  DIST_DIR="$FRONTEND_PATH/dist"
  if [[ ! -d "$DIST_DIR" ]]; then
    echo "Error: dist directory '$DIST_DIR' not found after build." >&2
    exit 1
  fi
  echo "Copying frontend dist to backend static directory..."
  mkdir -p "$STATIC_DIR"
  rm -rf "${STATIC_DIR:?}/"*
  cp -R "$DIST_DIR"/. "$STATIC_DIR"/
fi

if [[ "$BUILD_BACKEND" == "true" ]]; then
  echo "Building recorder-backend JAR..."
  pushd recorder-backend >/dev/null
  mvn clean package -DskipTests
  popd >/dev/null
else
  echo "Skipping backend JAR build."
fi

if [[ "$REBUILD_IMAGES" == "true" ]]; then
  echo "Building Docker images via docker compose..."
  docker compose build

  echo "Removing any existing containers with fixed names..."
  docker rm -f recorder-backend 2>/dev/null || true
  docker rm -f market-pulse-api 2>/dev/null || true

  echo "Starting stack with docker compose..."
  docker compose up -d --remove-orphans
else
  echo "Skipping image rebuild and container cleanup; starting existing containers..."
  docker compose up -d
fi

if [[ "$DOCKER_PUSH" == "true" ]]; then
  echo "Pushing Docker images..."
  docker compose push market-pulse recorder-backend
fi

echo "Current service status:"
docker compose ps
