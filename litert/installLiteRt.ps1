$ErrorActionPreference = 'Stop'

Write-Host '========================================='
Write-Host ' Installing litert-lm via uv '
Write-Host '========================================='
# LiteRT-LM requires Python 3.10 or higher.
Write-Host 'Installing litert-lm CLI...'
uv tool install --python 3.11 litert-lm

# Verify the installation
Write-Host 'Verifying installation...'
if (Get-Command litert-lm -ErrorAction SilentlyContinue) {
    Write-Host 'Success! litert-lm has been installed.'
} else {
    # Fallback check if the local bin path isn't refreshed in the current shell
    Write-Host 'Installation complete, but litert-lm is not yet in your PATH.'
    Write-Host 'Try restarting your terminal or refreshing your environment variables.'
    Write-Host 'Or run it directly via uv: uv tool run litert-lm --help'
}

# Copy config.json to ~/.litert-lm/config.json
$ConfigDir = Join-Path $HOME '.litert-lm'
if (-not (Test-Path $ConfigDir)) {
    New-Item -ItemType Directory -Path $ConfigDir | Out-Null
}
$DestinationFile = Join-Path $ConfigDir 'config.json'
Copy-Item -Path 'config.json' -Destination $DestinationFile -Force

# Download the e2b model
litert-lm import --from-huggingface-repo litert-community/gemma-4-E2B-it-litert-lm gemma-4-E2B-it.litertlm gemma4-e2b

# Start the server
litert-lm serve