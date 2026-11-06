# Minerva
Minerva is an experimental, entirely local AI system, designed for the raspberry pi 5. Minerva eschews fast responses in favour of slower and more thoughtful responses, 
and is *intended* to be a personal assistant that can be used for a variety of tasks. Minerva is designed to be semi-modular, with functionality implemented either as java-based tools or advisors, or as markdown skills.

# Capabilities(W.i.P)
* Short and long term memory
* API based interface
* 


# Installation Requirements
* Docker

# Related projects
* BraincraftHMI - A simple server wrapper for Adafruit's Braincraft Hat. Minerva *will* have an integrated advisor that outputs to the Braincraft Hat's display; the HMI side will be able to send data to Minerva.

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

# Testing
From the `core` directory, run the Docker-free unit tests with `mvn -B -Dgroups=Unit test`.
The container-backed integration tests use `mvn -B -Dgroups=ContinuousIntegration test`.
