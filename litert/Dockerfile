# Use a Python image with uv pre-installed
FROM ghcr.io/astral-sh/uv:python3.14-trixie-slim

# Keeps Python from buffering stdout and stderr to avoid situations where
# the application crashes without emitting any logs due to buffering.
ENV PYTHONUNBUFFERED=1

# Enable bytecode compilation
ENV UV_COMPILE_BYTECODE=1

# Copy from the cache instead of linking since it's a mounted volume
ENV UV_LINK_MODE=copy

# Ensure installed tools can be executed out of the box
ENV UV_TOOL_BIN_DIR=/usr/local/bin

# Place executables in the environment at the front of the path
ENV PATH="/app/.venv/bin:$PATH"

COPY installLitert.sh /installLitert.sh
RUN chmod +x /installLitert.sh
CMD ["/installLitert.sh"]