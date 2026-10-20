#!/bin/bash

# Start Ollama in the background.
/bin/ollama serve &
# Record Process ID.
pid=$!

# Pause for Ollama to start.
sleep 5

echo "🔴 Retrieve model..."
ollama pull gemma4:e2b
echo "Downloading!"

# Wait for Ollama process to finish.
wait $pid
echo "🟢 Done!"