# Minerva
Minerva is an experimental, entirely local AI system, designed for the raspberry pi 5. Minerva eschews fast responses in favour of slower and more thoughtful responses, 
and is *intended* to be a personal assistant that can be used for a variety of tasks. Minerva is designed to be semi-modular, with functionality implemented either as java-based tools or advisors, or as markdown skills.

# Capabilities(W.i.P)
* Short and long term memory
* API based interface
* Media-driven personality customization

## Personality customization
Open Minerva at `/personality` to upload a PDF, HTML page, or image, or provide a public image URL. Image comments and document guidance are optional; without an image comment, Minerva first asks what the image is about. Minerva stores interest tags and a separate personality prompt in its local `MinervaOS` data directory. HTML-referenced public images, GIFs, and videos are fetched with size and time limits; GIFs and sampled video frames are prepared for the local model. Private-network and localhost URLs are rejected.

The Docker image includes FFmpeg for video frame extraction. When running the application directly on a host, FFmpeg must be available on `PATH` to analyze referenced videos.

## Local backup and restore

Backups are local ZIP archives and are never uploaded or synced. The archive contains a PostgreSQL custom-format dump (including conversation/session and chat-memory tables) and user-managed `skills`, `memories`, and `personality.md` data. Logs, temporary uploads, Ollama models, `.env` values, and database credentials are excluded. The archive has a versioned manifest and SHA-256 checksums.

With the Compose stack running, create a backup at a path outside the repository:

```sh
python3 scripts/minerva_backup.py backup --output /path/to/minerva-backup.zip
```

Existing backup files are not overwritten unless `--force` is provided.

For the production Compose file, add `-f prod-compose.yaml` before `backup`:

```sh
python3 scripts/minerva_backup.py -f prod-compose.yaml backup --output /path/to/minerva-backup.zip
```

Restore replaces the PostgreSQL database and the backed-up user data. It requires the explicit `--replace` confirmation; it does not merge. The script validates the archive and database dump before stopping Minerva, stages file changes, and retains rollback data until both database and file restoration succeed:

```sh
python3 scripts/minerva_backup.py restore --archive /path/to/minerva-backup.zip --replace
```

Restore also requires the Compose `core` and `db` services to be running. Minerva is stopped while the database and files are captured or restored, then restarted. Keep backups private: conversations and memories may contain sensitive personal data.

The Compose data volume is mounted at the dedicated filesystem-root directory `/MinervaOS`. On first startup after upgrading from the previous `core-data:/home` layout, Minerva copies existing data from `/MinervaOS/app/MinervaOS` into the new locations and leaves the original files in place. If destination data already exists, it is preserved and a warning identifies the legacy copy for manual review.

# Installation Requirements
* Docker

# Optional Requirements
* Python

## .env values
* POSTGRES_PASSWORD - Required
* IO-KEY - Optional, for AdafruitIO integration
* IO-USERNAME - Optional, for AdafruitIO integration
* BRAVE_API_KEY - Optional, for Web searching

# Related projects
* BraincraftHMI - A simple server wrapper for Adafruit's Braincraft Hat. Minerva *will* have an integrated advisor that outputs to the Braincraft Hat's display; the HMI side will be able to send data to Minerva.

# Raspberry PI Quickstart
If you simply want to try this out on a Raspberry Pi 5 8/16GB(I wouldn't recommend this on anything else), you can quickly get started by doing the following:
* Create a directory and drop the prod-compose file into it
* Create an .env file, with the calues outline above
* Create a folder in there called db, and in it create a file called password.txt with a password that matches what's in the .env
* Bring up the docker compose file

# Building from source/Contributing
* Clone the repo
* Pull Submodules
* Use the dev-compose file to spin up the core system, db, voice api & ollama

# Testing
From the `core` directory, run the Docker-free unit tests with `mvn -B -Dgroups=Unit test`.
The container-backed integration tests use `mvn -B -Dgroups=ContinuousIntegration test`.
The backup archive tests use `python3 -m unittest discover -s scripts`.
