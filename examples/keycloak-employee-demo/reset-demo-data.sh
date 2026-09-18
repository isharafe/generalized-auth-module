#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"
COMPOSE_PROJECT="keycloak-employee-demo"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required but was not found on PATH." >&2
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "The Docker Compose plugin is required (docker compose)." >&2
  exit 1
fi

echo "Removing the Keycloak employee demo containers, network, and persistent volumes..."
docker compose \
  --project-name "$COMPOSE_PROJECT" \
  --project-directory "$SCRIPT_DIR" \
  --file "$COMPOSE_FILE" \
  down --volumes --remove-orphans

echo "Demo data removed. Run 'docker compose up -d' from $SCRIPT_DIR to recreate it."
