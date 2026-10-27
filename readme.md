# Minerva
Minerva is an experimental, entirely local AI system
# Capabilities

# Installation Requirements
* Docker

# Raspberry PI Quickstart
If you simply want to try this out on a Raspberry Pi 5(or any other device), you can quickly get started by running the following commands:
* `wget https://raw.githubusercontent.com/David032/Minerva/refs/heads/master/scripts/installer.sh`
* `sudo chmod +x installer.sh`
* `sudo ./installer.sh`

This downloads and runs an installer script that: Creates a deploy directory, downloads prod-compose.yaml,
auto-generates a strong POSTGRES_PASSWORD, writes it to .env and db/password.txt,
then brings up the docker-compose stack.

# Building from source/Contributing
* Clone the repo
* Pull Submodules
* Use the dev-compose file to spin up the core system, db, voice api & ollama
