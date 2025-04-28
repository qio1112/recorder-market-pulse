#!/usr/bin/env bash
set -euo pipefail

# 1) load your prod env (fails if missing)
ENV_FILE=".env.prod"
if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE – aborting!" >&2
  exit 1
fi
# this exports DB_HOST, DB_USER, etc., into your shell
export $(grep -v '^#' "$ENV_FILE" | xargs)

# 2) optionally pull down the newest images
docker-compose pull

# 3) bring up the stack, recreating only what’s changed
docker-compose up -d --remove-orphans

# 4) show status
docker-compose ps
