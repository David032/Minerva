#!/usr/bin/env bash

set -euo pipefail

echo "========================================="
echo " Installing litert-lm via uv "
echo "========================================="
# LiteRT-LM requires Python 3.10 or higher.
echo "📦 Checking litert-lm CLI..."
if ! command -v litert-lm >/dev/null 2>&1; then
    uv tool install --python 3.11 litert-lm
fi

# Verify the installation
echo "⚡ Verifying installation..."
if command -v litert-lm &> /dev/null; then
    echo "🎉 Success! litert-lm has been installed."
else
    # Fallback check if the local bin path isn't refreshed in the current shell
    echo "⚠️  Installation complete, but 'litert-lm' is not yet in your PATH."
    echo "Running: source \$HOME/.local/bin/env"
    source \$HOME/.local/bin/env
fi

# Copy config.json to ~/.litert-lm/config.json
#CONFIG_DIR="$HOME/.litert-lm"
#if [ ! -d "$CONFIG_DIR" ]; then
#    mkdir -p "$CONFIG_DIR"
#fi
#cp "config.json" "$CONFIG_DIR/config.json"

# Define the directory and file path
TARGET_DIR="$HOME/.litert-lm"
TARGET_FILE="$TARGET_DIR/config.json"

# 1. Create the directory and check for success
if ! mkdir -p "$TARGET_DIR"; then
    echo "Error: Failed to create directory $TARGET_DIR" >&2
    exit 1
fi

# 2. Check if file exists. If it does, skip everything else.
if [ -f "$TARGET_FILE" ]; then
    echo "File already exists at $TARGET_FILE. Skipping creation."
else
    # 3. Detect hardware and generate the JSON (Only runs if file doesn't exist)
    if grep -Eqi "raspberry pi|bcm[0-9]{4}" /proc/cpuinfo 2>/dev/null || [ -f /sys/firmware/devicetree/base/model ] && grep -qi "raspberry pi" /sys/firmware/devicetree/base/model; then
        echo "Raspberry Pi detected"

        cat << 'EOF' > "$TARGET_FILE" 2>/dev/null
{
  "default": {
    "backend": "cpu",
    "audio-backend": "gpu",
    "vision-backend": "gpu",
    "max_num_tokens": 8192
  }
}
EOF
    else
        echo "non-pi, so assuming container"

        cat << 'EOF' > "$TARGET_FILE" 2>/dev/null
{
  "default": {
    "backend": "cpu",
    "audio-backend": "cpu",
    "vision-backend": "cpu",
    "max_num_tokens": 8192
  }
}
EOF
    fi

    # 4. Verify the file was written successfully
    if [ $? -eq 0 ]; then
        echo "Configuration file successfully created at: $TARGET_FILE"
    else
        echo "Error: Failed to write configuration to $TARGET_FILE" >&2
        exit 1
    fi
fi

# Download the e2b model
litert-lm import --from-huggingface-repo litert-community/gemma-4-E2B-it-litert-lm gemma-4-E2B-it.litertlm gemma4-e2b

# Start the server
litert-lm serve --host 0.0.0.0 --port 9379
