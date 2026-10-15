#!/usr/bin/env bash
# Install UV via pipx, install liteRt-lm via `uv tool install`, import gemma4-e2b,
# and create+enable a systemd service that starts the liteRt-lm OpenAI-compatible server.
# The service runs as the user who invoked this script (SUDO_USER if script run with sudo).
# Run as root: sudo bash ./install_litert.sh

set -euo pipefail

# Configurable
SERVICE_NAME="litert-openai"
SERVICE_PORT=9379
GEMMA_IMPORT_CMD="litert-lm import --from-huggingface-repo litert-community/gemma-4-E2B-it-litert-lm gemma-4-E2B-it.litertlm gemma4-e2b"
EXEC_START_CMD="litert-lm serve --host 0.0.0.0 --port ${SERVICE_PORT}"

# Helpers
run() { echo "+ $*"; "$@"; }

# Must run as root so we can create systemd unit and install system packages
if [ "$EUID" -ne 0 ]; then
  echo "This script must be run as root (use sudo)." >&2
  exit 1
fi

# Determine the user the service should run as:
# Prefer SUDO_USER (original invoking user when using sudo), otherwise fall back to the current user.
RUN_AS_USER="${SUDO_USER:-$(logname 2>/dev/null || whoami)}"
if [ -z "$RUN_AS_USER" ]; then
  echo "Unable to determine invoking user." >&2
  exit 1
fi

# Determine user's home directory
USER_HOME="$(getent passwd "$RUN_AS_USER" | cut -d: -f6 || true)"
if [ -z "$USER_HOME" ]; then
  # fallback
  if [ "$RUN_AS_USER" = "root" ]; then
    USER_HOME="/root"
  else
    USER_HOME="/home/${RUN_AS_USER}"
  fi
fi

echo "Service will run as user: $RUN_AS_USER (home: $USER_HOME)"

# Install system prerequisites (Debian/Ubuntu apt-get preferred)
if command -v apt-get >/dev/null 2>&1; then
  run apt-get update
  run apt-get install -y git curl python3 python3-venv python3-pip build-essential
else
  echo "Non-apt system detected. Ensure python3, pip and build tools are installed manually." >&2
fi

# Ensure pipx exists (system-wide). Prefer package manager, else pip.
if ! command -v pipx >/dev/null 2>&1; then
  echo "pipx not found, attempting installation..."
  if command -v apt-get >/dev/null 2>&1; then
    if apt install -y pipx 2>/dev/null; then
      echo "Installed pipx via apt."
    else
      echo "apt install pipx failed; installing pipx via pip."
      run python3 -m pip install --upgrade pipx
      # ensure /usr/local/bin in PATH for the rest of the script
      export PATH="/usr/local/bin:${PATH}"
    fi
  else
    run python3 -m pip install --upgrade pipx || true
    export PATH="/usr/local/bin:${PATH}"
  fi
fi

# Locate pipx binary
PIPX_BIN="$(command -v pipx 2>/dev/null || true)"
if [ -z "$PIPX_BIN" ]; then
  # Try common locations
  for p in /usr/local/bin/pipx /usr/bin/pipx /root/.local/bin/pipx; do
    if [ -x "$p" ]; then PIPX_BIN="$p" && break; fi
  done
fi
if [ -z "$PIPX_BIN" ]; then
  echo "pipx not found after installation attempts. Install pipx and re-run." >&2
  exit 1
fi
echo "Using pipx at: $PIPX_BIN"

# Ensure the invoking user's ~/.local exists and is owned by them
run mkdir -p "${USER_HOME}/.local/bin" "${USER_HOME}/.local/pipx"
run chown -R "${RUN_AS_USER}:${RUN_AS_USER}" "${USER_HOME}/.local" || true

# Install uv via pipx as the invoking user
echo "Installing uv via pipx for user ${RUN_AS_USER}..."
run su - "${RUN_AS_USER}" -s /bin/bash -c "PATH=\$HOME/.local/bin:/usr/local/bin:/usr/bin:${PATH}; ${PIPX_BIN} install uv"

# Install liteRt-lm using uv (as invoking user)
echo "Installing liteRt-lm via 'uv tool install litert-lm' as ${RUN_AS_USER}..."
run su - "${RUN_AS_USER}" -s /bin/bash -c "PATH=\$HOME/.local/bin:/usr/local/bin:/usr/bin:${PATH}; uv tool install litert-lm"

# Run gemma import as the invoking user
echo "Running gemma4-e2b import as ${RUN_AS_USER} (this may take a while)..."
run su - "${RUN_AS_USER}" -s /bin/bash -c "PATH=\$HOME/.local/bin:/usr/local/bin:/usr/bin:${PATH}; ${GEMMA_IMPORT_CMD}"

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
Environment=PATH=${USER_HOME}/.local/bin:/usr/local/bin:/usr/bin
ExecStart=${USER_HOME}/.local/bin/${EXEC_START_CMD}
Restart=on-failure
RestartSec=5
LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
EOF

chmod 644 "${SERVICE_FILE}"

# Reload systemd and enable/start the service
run systemctl daemon-reload
run systemctl enable --now "${SERVICE_NAME}.service"

echo "Done. The service '${SERVICE_NAME}' is enabled and starting (as ${RUN_AS_USER})."
echo "Check status: systemctl status ${SERVICE_NAME}.service"
echo "View logs: journalctl -u ${SERVICE_NAME}.service -f"