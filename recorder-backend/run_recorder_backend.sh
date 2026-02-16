#!/usr/bin/env bash
set -euo pipefail

# Default values
BUILD_JAR=true
REBUILD_IMAGE=true
ENV_FILE="../.env"
DOCKER_PUSH=false
BUILD_FRONTEND=false
FRONTEND_PATH="../recorder-frontend"
RUN_DEV_SERVER=true

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
    --env-file)
      ENV_FILE="$2"
      shift 2
      ;;
    --docker-push)
      DOCKER_PUSH=true
      shift
      ;;
    --not-run-dev-server)
      RUN_DEV_SERVER=false
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
    --help|-h)
      echo "Usage: $0 [OPTIONS]"
      echo ""
      echo "Options:"
      echo "  --no-build          Skip building the JAR file"
      echo "  --no-rebuild-image  Skip rebuilding the Docker image"
      echo "  --env-file          Path of file with env variables"
      echo "  --docker-push       Push docker image to dockerhub"
      echo "  --not-run-dev-server   Not running docker container after running this script"
      echo "  --build-frontend   [path] Build frontend (npm run build) from path (default ../recorder-frontend) and copy dist to backend static"
      echo "  --help, -h          Show this help message"
      echo ""
      exit 0
      ;;
    *)
      echo "Unknown option: $1"
      echo "Use --help for usage information"
      exit 1
      ;;
  esac
done

# Build frontend if requested
if [[ "$BUILD_FRONTEND" == "true" ]]; then
  echo "Building frontend from ${FRONTEND_PATH}..."
  if [[ ! -d "$FRONTEND_PATH" ]]; then
    echo "Error: frontend path '$FRONTEND_PATH' does not exist." >&2
    exit 1
  fi
  pushd "$FRONTEND_PATH" >/dev/null
  npm run build
  popd >/dev/null

  STATIC_DIR="./src/main/resources/static"
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

# Build JAR if requested
if [[ "$BUILD_JAR" == "true" ]]; then
  echo "Building JAR file..."
  mvn clean package -DskipTests
else
  echo "Skipping JAR build..."
fi

# 1) Load env vars from .env
if [[ ! -f "$ENV_FILE" ]]; then
  echo "Error: $ENV_FILE not found. Create it with your prod secrets." >&2
  exit 1
fi
# Export every non-comment line
echo "Exporting env variables from $ENV_FILE:"
#grep -v '^#' "$ENV_FILE" | xargs -n1 echo
export $(grep -v '^#' "$ENV_FILE" | xargs)


# 2) Build the image if requested
if [[ "$REBUILD_IMAGE" == "true" ]]; then
  echo "Building Docker image..."
  docker build \
    --build-arg BACKEND_APP_LOG_PATH=${BACKEND_APP_LOG_PATH} \
    --build-arg BACKEND_APP_FILE_PATH=${BACKEND_APP_FILE_PATH} \
    --build-arg APP_TIMEZONE=${APP_TIMEZONE} \
    -t recorder-backend:latest .
else
  echo "Skipping Docker image rebuild..."
fi

if [[ "$DOCKER_PUSH" == "true" ]]; then
  docker login
  docker tag recorder-backend:latest yipeng6257/recorder-backend:latest
  docker push yipeng6257/recorder-backend:latest
fi

# 3) Stop & remove any existing container
if docker ps -a --format '{{.Names}}' | grep -q '^recorder-backend$' ; then
  echo "Removing old container..."
  docker rm -f recorder-backend
fi

if [[ "$RUN_DEV_SERVER" == "true" ]]; then
  # 4) Run the new container
  echo "Starting recorder-backend container..."
  docker network inspect recorder-net >/dev/null 2>&1 || docker network create recorder-net
  docker run -d \
    --name recorder-backend \
    --restart unless-stopped \
    --env-file ../.env \
    --network recorder-net \
    -v ${BACKEND_APP_LOG_PATH_SERVER}:${BACKEND_APP_LOG_PATH} \
    -v ${BACKEND_APP_FILE_PATH_SERVER}:${BACKEND_APP_FILE_PATH} \
    -p 8080:8080 \
    recorder-backend:latest

  echo "→ recorder-backend is up!  Logs: docker logs -f recorder-backend"
fi