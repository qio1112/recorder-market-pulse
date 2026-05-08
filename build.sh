#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ENV_FILE=".env"
FRONTEND_PATH="./recorder-frontend"
DOCKER_PUSH=false
RUN_DOCKER_COMPOSE=true

# Build targets: 1=frontend, 2=backend, 3=market-pulse
BUILD_FRONTEND=true
BUILD_BACKEND=true
BUILD_MARKET_PULSE=true
SKIP_BUILD=false

usage() {
  cat <<'EOF'
Usage: ./build.sh [options]
  --env-file <path>       Path to .env file (default: .env)
  --build <list>          Comma-separated build targets: 1=frontend, 2=backend, 3=market-pulse (default: 1,2,3)
  --skip-build            Skip all build steps (frontend/backend/images) and just run docker compose
  --docker-push           After build, push images defined in docker-compose.yml
  --run-docker-compose <true|false>  Whether to run docker compose up (default: true)
  --help                  Show this help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      ENV_FILE="$2"
      shift 2
      ;;
    --build)
      BUILD_FRONTEND=false
      BUILD_BACKEND=false
      BUILD_MARKET_PULSE=false
      IFS=',' read -r -a targets <<< "$2"
      for t in "${targets[@]}"; do
        case "$t" in
          1) BUILD_FRONTEND=true ;;
          2) BUILD_BACKEND=true ;;
          3) BUILD_MARKET_PULSE=true ;;
          *) echo "Unknown build target: $t (use 1,2,3)" >&2; exit 1 ;;
        esac
      done
      shift 2
      ;;
    --docker-push)
      DOCKER_PUSH=true
      shift
      ;;
    --skip-build)
      SKIP_BUILD=true
      shift
      ;;
    --run-docker-compose)
      RUN_DOCKER_COMPOSE="$2"
      shift 2
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
MARKET_PULSE_VENV_PATH_SERVER="${MARKET_PULSE_VENV_PATH_SERVER:-${SCRIPT_DIR}/market_pulse/.venv_docker}"
export MARKET_PULSE_VENV_PATH_SERVER

echo "Ensuring resource directories exist..."
mkdir -p "${MARKET_PULSE_RESOURCE_PATH_SERVER}/logs" \
         "${MARKET_PULSE_RESOURCE_PATH_SERVER}/qdrant" \
         "${MARKET_PULSE_RESOURCE_PATH_SERVER}/models/sentence_transformers" \
         "${MARKET_PULSE_VENV_PATH_SERVER}" \
         "${BACKEND_APP_LOG_PATH_SERVER}" \
         "${BACKEND_APP_FILE_PATH_SERVER}"
touch "${MARKET_PULSE_RESOURCE_PATH_SERVER}/logs/log.txt"

if [[ "$SKIP_BUILD" == "true" ]]; then
  echo "--skip-build set; skipping frontend/backend builds and image rebuilds."
else
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
  else
    echo "Skipping frontend build."
  fi
fi

if [[ "$SKIP_BUILD" != "true" && "$BUILD_BACKEND" == "true" ]]; then
  echo "Building recorder-backend JAR..."
  pushd recorder-backend >/dev/null
  mvn clean package -DskipTests
  popd >/dev/null
else
  echo "Skipping backend JAR build."
fi

services_to_build=()
if [[ "$SKIP_BUILD" != "true" && "$BUILD_BACKEND" == "true" ]]; then
  services_to_build+=("recorder-backend")
fi
if [[ "$SKIP_BUILD" != "true" && "$BUILD_MARKET_PULSE" == "true" ]]; then
  services_to_build+=("market-pulse")
fi

if [[ "$SKIP_BUILD" != "true" && "${#services_to_build[@]}" -gt 0 ]]; then
  echo "Building Docker images via docker compose for: ${services_to_build[*]} ..."
  docker compose build "${services_to_build[@]}"
  echo "Pruning stopped containers..."
  docker container prune -f
  echo "Pruning dangling images..."
  docker image prune -f
elif [[ "$SKIP_BUILD" != "true" ]]; then
  echo "No Docker images selected for build (targets 2 or 3)."
fi

if [[ "$RUN_DOCKER_COMPOSE" == "true" ]]; then
  echo "Removing any existing containers with fixed names..."
  if [[ "$SKIP_BUILD" != "true" && "$BUILD_BACKEND" == "true" ]]; then
    docker rm -f recorder-backend 2>/dev/null || true
  fi
  if [[ "$SKIP_BUILD" != "true" && "$BUILD_MARKET_PULSE" == "true" ]]; then
    docker rm -f market-pulse-api 2>/dev/null || true
  fi

  echo "Starting stack with docker compose..."
  docker compose up -d --remove-orphans
else
  echo "RUN_DOCKER_COMPOSE=false; skipping docker compose up."
fi

if [[ "$DOCKER_PUSH" == "true" ]]; then
  if [[ "${#services_to_build[@]}" -gt 0 ]]; then
    echo "Pushing Docker images: ${services_to_build[*]} ..."
    docker compose push "${services_to_build[@]}"
  else
    echo "No services to push."
  fi
fi

if [[ "$RUN_DOCKER_COMPOSE" == "true" ]]; then
  echo "Current service status:"
  docker compose ps
else
  echo "Skipped docker compose up; not showing service status."
fi
