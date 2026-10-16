#!/usr/bin/env bash
# Install liteRt-lm via `uv tool install`, import gemma4-e2b,
# and create+enable a systemd service that starts the liteRt-lm OpenAI-compatible server.
# Assumes UV is already installed and available to the invoking user.
# Run as root (so the script can write the systemd unit): sudo bash ./install_litert.sh

set -euo pipefail

SERVICE_NAME="litert-openai"
SERVICE_PORT=9379
GEMMA_IMPORT_CMD="litert-lm import --from-huggingface-repo litert-community/gemma-4-E2B-it-litert-lm gemma-4-E2B-it.litertlm gemma4-e2b"
LITERT_SERVE_CMD="litert-lm serve --host 0.0.0.0 --port ${SERVICE_PORT} --openai-compatible"

run() { echo "+ $*"; "$@"; }

if [ "$EUID" -ne 0 ]; then
  echo "This script must be run as root (use sudo)." >&2
  exit 1
fi

# Determine the user the service should run as:
RUN_AS_USER="${SUDO_USER:-$(logname 2>/dev/null || whoami)}"
if [ -z "$RUN_AS_USER" ]; then
  echo "Unable to determine invoking user." >&2
  exit 1
fi

# Determine user's home directory
USER_HOME="$(getent passwd "$RUN_AS_USER" | cut -d: -f6 || true)"
if [ -z "$USER_HOME" ]; then
  if [ "$RUN_AS_USER" = "root" ]; then
    USER_HOME="/root"
  else
    USER_HOME="/home/${RUN_AS_USER}"
  fi
fi

echo "Service will run as user: $RUN_AS_USER (home: $USER_HOME)"

# Ensure user's local bin is present and owned by them
run mkdir -p "${USER_HOME}/.local/bin" "${USER_HOME}/.local/share" "${USER_HOME}/.cache"
run chown -R "${RUN_AS_USER}:${RUN_AS_USER}" "${USER_HOME}/.local" "${USER_HOME}/.cache" || true

# PATH to use when running uv and litert-lm for the user
USER_PATH="${USER_HOME}/.local/bin:/usr/local/bin:/usr/bin:/bin"

# Check that uv is available for the invoking user
if ! su - "${RUN_AS_USER}" -s /bin/bash -c "PATH=${USER_PATH}:\$PATH; command -v uv >/dev/null 2>&1"; then
  echo "Error: 'uv' was not found in ${RUN_AS_USER}'s PATH. Ensure UV is installed and available to the user before running this script." >&2
  exit 2
fi

# Install liteRt-lm via uv as the invoking user
echo "Installing liteRt-lm via 'uv tool install litert-lm' as ${RUN_AS_USER}..."
run su - "${RUN_AS_USER}" -s /bin/bash -c "PATH=${USER_PATH}:\$PATH; uv tool install litert-lm"

# Run the Gemma import command as the invoking user
echo "Running gemma4-e2b import as ${RUN_AS_USER} (may take a while)..."
run su - "${RUN_AS_USER}" -s /bin/bash -c "PATH=${USER_PATH}:\$PATH; ${GEMMA_IMPORT_CMD}"

# Create systemd unit file that runs as the invoking user
SERVICE_FILE="/etc/systemd/system/${SERVICE_NAME}.service"
echo "Creating systemd unit at ${SERVICE_FILE}"
cat > "${SERVICE_FILE}" <<EOF
[Unit]
Description=liteRt-lm OpenAI-compatible server (litert) - run as ${RUN_AS_USER}
After=network.target

[Service]
Type=simple
User=${RUN_AS_USER}
Group=${RUN_AS_USER}
WorkingDirectory=${USER_HOME}
Environment=HOME=${USER_HOME}
Environment=PATH=${USER_HOME}/.local/bin:/usr/local/bin:/usr/bin:/bin
ExecStart=/bin/bash -lc 'export HOME=${USER_HOME}; export PATH=${USER_HOME}/.local/bin:/usr/local/bin:/usr/bin:/bin; exec ${LITERT_SERVE_CMD}'
Restart=on-failure
RestartSec=5
LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
EOF

chmod 644 "${SERVICE_FILE}"

# Reload systemd and enable+start the service
run systemctl daemon-reload
run systemctl enable --now "${SERVICE_NAME}.service"

echo "Done. The service '${SERVICE_NAME}' is enabled and starting (as ${RUN_AS_USER})."
echo "Check status: systemctl status ${SERVICE_NAME}.service"
echo "View logs: journalctl -u ${SERVICE_NAME}.service -f"