#!/usr/bin/env bash
set -euo pipefail

# Default values
BUILD_JAR=true
REBUILD_IMAGE=true

# Parse command line arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --no-build)
      BUILD_JAR=false
      shift
      ;;
    --no-rebuild-image)
      REBUILD_IMAGE=false
      shift
      ;;
    --help|-h)
      echo "Usage: $0 [OPTIONS]"
      echo ""
      echo "Options:"
      echo "  --no-build          Skip building the JAR file"
      echo "  --no-rebuild-image  Skip rebuilding the Docker image"
      echo "  --help, -h          Show this help message"
      echo ""
      echo "Examples:"
      echo "  $0                    # Full build and rebuild"
      echo "  $0 --no-build         # Skip JAR build, rebuild image"
      echo "  $0 --no-rebuild-image # Build JAR, use existing image"
      echo "  $0 --no-build --no-rebuild-image # Use existing JAR and image"
      exit 0
      ;;
    *)
      echo "Unknown option: $1"
      echo "Use --help for usage information"
      exit 1
      ;;
  esac
done

# Build JAR if requested
if [[ "$BUILD_JAR" == "true" ]]; then
  echo "Building JAR file..."
  mvn clean package -DskipTests
else
  echo "Skipping JAR build..."
fi

# 1) Load env vars from .env
ENV_FILE="../.env"
if [[ ! -f "$ENV_FILE" ]]; then
  echo "Error: $ENV_FILE not found. Create it with your prod secrets." >&2
  exit 1
fi
# Export every non-comment line
echo "Exporting env variables from $ENV_FILE:"
#grep -v '^#' "$ENV_FILE" | xargs -n1 echo
export $(grep -v '^#' "$ENV_FILE" | xargs)

# make directories for market_pulse logs
mkdir -p ${MARKET_PULSE_PATH_SERVER}/resources/logs
touch ${MARKET_PULSE_PATH_SERVER}/resources/logs/log.txt

# 2) Build the image if requested
if [[ "$REBUILD_IMAGE" == "true" ]]; then
  echo "Building Docker image..."
  docker build \
    --build-arg MARKET_PULSE_PATH=${MARKET_PULSE_PATH} \
    --build-arg BACKEND_APP_LOG_PATH=${BACKEND_APP_LOG_PATH} \
    --build-arg BACKEND_APP_FILE_PATH=${BACKEND_APP_FILE_PATH} \
    --build-arg MARKET_PULSE_PATH_SERVER=${MARKET_PULSE_PATH_SERVER} \
    -t recorder-backend:latest .
else
  echo "Skipping Docker image rebuild..."
fi

# 3) Stop & remove any existing container
if docker ps -a --format '{{.Names}}' | grep -q '^recorder-backend$' ; then
  echo "Removing old container..."
  docker rm -f recorder-backend
fi

# 4) Run the new container
echo "Starting recorder-backend container..."
docker run -d \
  --name recorder-backend \
  --restart unless-stopped \
  --env-file ../.env \
  -v ${BACKEND_APP_LOG_PATH_SERVER}:${BACKEND_APP_LOG_PATH} \
  -v ${BACKEND_APP_FILE_PATH_SERVER}:${BACKEND_APP_FILE_PATH} \
  -v ${MARKET_PULSE_PATH_SERVER}:${MARKET_PULSE_PATH} \
  -p 8080:8080 \
  recorder-backend:latest

echo "→ recorder-backend is up!  Logs: docker logs -f recorder-backend"
