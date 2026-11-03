#!/usr/bin/env bash
# Creates a deploy directory, downloads prod-compose.yaml and install script from the repo,
# auto-generates a strong POSTGRES_PASSWORD, writes it to .env and db/password.txt (used by your compose secrets),
# runs the install script (with sudo if necessary), then brings up the docker-compose stack.
set -euo pipefail

# Configurable: target directory and c
TARGET_DIR="${1:-./minerva-deploy}"
RAW_BASE="https://raw.githubusercontent.com/David032/Minerva"
PROD_FILE="prod-compose.yaml"
PROD_URL="${RAW_BASE}/refs/heads/master/${PROD_FILE}"

echo "Target directory: $TARGET_DIR"

# Ensure curl exists
if ! command -v curl >/dev/null 2>&1; then
  echo "Error: curl is required but not installed. Install curl and re-run." >&2
  exit 1
fi

# Create directory and download files
mkdir -p "$TARGET_DIR"
cd "$TARGET_DIR"

echo "Downloading ${PROD_FILE} from ${PROD_URL}..."
curl -fsSL "$PROD_URL" -o "$PROD_FILE" || { echo "Failed to download $PROD_FILE"; exit 1; }

# Auto-generate a strong password (64 hex chars = 256 bits)
if command -v openssl >/dev/null 2>&1; then
  POSTGRES_PASSWORD="$(openssl rand -hex 32)"
else
  # Fallback using /dev/urandom + od
  POSTGRES_PASSWORD="$(od -An -N32 -tx1 /dev/urandom | tr -d ' \n' )"
fi

# Create .env with POSTGRES_PASSWORD
cat > .env <<EOF
POSTGRES_PASSWORD=${POSTGRES_PASSWORD}
EOF
chmod 600 .env
echo ".env created with POSTGRES_PASSWORD (permissions 600)."

# Ensure db/password.txt exists for Docker secrets (path referenced in prod-compose.yaml)
mkdir -p db
echo -n "${POSTGRES_PASSWORD}" > db/password.txt
chmod 600 db/password.txt
echo "Wrote db/password.txt (permissions 600) for Docker secret 'db-password'."

# Print the generated password once for the operator to copy/store securely
echo
echo "Generated POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}"
echo "Stored in: ${TARGET_DIR}/.env and ${TARGET_DIR}/db/password.txt (both permission 600)."
echo

# Start docker compose stack (attempt docker compose plugin first, fallback to docker-compose)
if ! command -v docker >/dev/null 2>&1; then
  echo "Error: Docker is not installed or not in PATH. Install Docker and re-run to bring up the stack." >&2
  exit 1
fi

COMPOSE_CMD=""
if docker compose version >/dev/null 2>&1; then
  COMPOSE_CMD="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE_CMD="docker-compose"
else
  echo "Error: docker compose plugin or docker-compose not found. Install a compose tool and re-run." >&2
  exit 1
fi

echo "Bringing up services with: $COMPOSE_CMD -f ${PWD}/${PROD_FILE} up -d"
# Docker Compose automatically reads .env from current directory, so no extra args needed
$COMPOSE_CMD -f "${PWD}/${PROD_FILE}" up -d

echo "Stack started. Showing compose ps:"
$COMPOSE_CMD -f "${PWD}/${PROD_FILE}" ps

echo "All done. The files are in: ${TARGET_DIR}"
echo "prod-compose: ${PWD}/${PROD_FILE}"
echo "If you need to view logs: ${COMPOSE_CMD} -f ${PWD}/${PROD_FILE} logs -f"