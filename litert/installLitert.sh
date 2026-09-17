#!/usr/bin/env bash

set -euo pipefail

echo "========================================="
echo " Installing litert-lm via uv "
echo "========================================="
# LiteRT-LM requires Python 3.10 or higher.
echo "📦 Installing litert-lm CLI..."
uv tool install --python 3.11 litert-lm

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

# Download the e2b model
litert-lm import --from-huggingface-repo litert-community/gemma-4-E2B-it-litert-lm gemma-4-E2B-it.litertlm gemma4-e2b

# Start the server
litert-lm serve
