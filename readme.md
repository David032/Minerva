# Minerva
Minerva is an experimental, entirely local AI system, designed for the raspberry pi 5. Minerva eschews fast responses in favour of slower and more thoughtful responses, 
and is *intended* to be a personal assistant that can be used for a variety of tasks. Minerva is designed to be semi-modular, with functionality implemented either as Java-based tools or advisors, or as Markdown skills.

# Capabilities(W.i.P)
* Short and long term memory
* API based interface
* Media-driven personality customisation

## Personality customisation
Open Minerva at `/personality` to upload a PDF, HTML page, or image, or provide a public image URL. Image comments and document guidance are optional; without an image comment, Minerva first asks what the image is about. Minerva stores interest tags and a separate personality prompt in its local `MinervaOS` data directory. HTML-referenced public images, GIFs, and videos are fetched with size and time limits; GIFs and sampled video frames are prepared for the local model. Private-network and localhost URLs are rejected.

The Docker image includes FFmpeg for video frame extraction. When running the application directly on a host, FFmpeg must be available on `PATH` to analyse referenced videos.

## Skills

Open `/skills` to import a ZIP archive or a public HTTPS ZIP/Git repository. Sources may contain a skill at the root or multiple nested skill directories. Each skill's `SKILL.md` must have `name` and `description` fields in YAML front matter. ZIP uploads are limited to 10 MB; imports reject unsafe archives, local/private network URLs, and name collisions without replacing existing skills. Imported skills are made available to Minerva immediately. Git repository imports require `git` on `PATH`; the Docker image includes it.

The assistant can also find and install skills using its import tool. This uses web search when `BRAVE_API_KEY` is configured; otherwise, provide the assistant with a source URL. Imported skill instructions are third-party content and should be treated cautiously.

## Reminders

Create, list, and cancel one-time reminders in chat, or manage them from the chat page. Reminders are stored in the local PostgreSQL database and survive application restarts. Upcoming reminders remain visible in the chat page until acknowledged; after downtime, overdue reminders are recovered as due. Set `MINERVA_TIME_ZONE` to the desired IANA time-zone ID (defaults to UTC) for local date/time input without an explicit offset. Times in daylight-saving gaps are rejected; ambiguous local times use the earlier offset. Past reminders are rejected. The page polls for due reminders while open; reminders do not send browser or operating-system notifications while the page is closed.

## Heartbeat

Minerva can run a scheduled assistant check using instructions in `heartbeat.md` at the root of its data directory (`MINERVA_DATA_DIR`, or `~/MinervaOS` by default). By default, Minerva checks once at the top of each UTC hour. A missing or empty file is ignored. If an AI request is active at the scheduled tick, that tick is skipped rather than queued; the next check is at the following hour. Set `MINERVA_HEARTBEAT_ENABLED=false` to disable it, or use `MINERVA_HEARTBEAT_CRON` and `MINERVA_HEARTBEAT_ZONE` to change the schedule. A running heartbeat holds the exclusive AI activity slot; new AI requests receive a busy message and can be retried after it finishes. Heartbeat instructions and session history are backed up with other local user data.

## Tool search

Tool search is experimental and disabled by default. Set `MINERVA_TOOL_SEARCH_ENABLED=true` in the environment (or the Compose `.env` file) and restart Minerva to let the assistant and heartbeat discover tools on demand using a local keyword index. This can reduce the tool definitions sent to the model, but requires the configured model to search for and call tools reliably; keyword searches can also miss relevant tools. If tools stop working, set it to `false` and restart to restore the normal tool-calling path. Minerva does not automatically retry failed tool calls because a tool may already have performed an action.

## Journiv

To enable the optional Journiv integration, set `JOURNIV_URL` to the Journiv server's HTTP(S) base URL and set `JOURNIV_EMAIL` and `JOURNIV_PASSWORD` to dedicated account credentials in the `core` service's `environment` section of `compose.yaml` or `prod-compose.yaml`. From a Compose container, use a hostname reachable from that container; `localhost` refers to the Minerva container itself. Use HTTPS when traffic crosses an untrusted network. The server must allow password registration for a new account. Journiv may make its first account an administrator; provision your own administrator first if that is not intended. Existing accounts must use the configured password. The integration is disabled when `JOURNIV_URL` is empty.

Minerva creates or reuses its own journal and records concise AI-generated summaries of completed text, image and audio interactions and heartbeat outcomes. Image and audio files are not sent to Journiv. A daily entry is created when there is activity, and the assistant can read prior entries. `JOURNIV_TIME_ZONE` selects the IANA time zone for daily entries; it defaults to `MINERVA_TIME_ZONE`, or UTC. `JOURNIV_RETRY_INTERVAL` sets the retry interval in milliseconds (default 60000).

Pending activity is stored in PostgreSQL for retry after a restart or an outage. **Until summarisation succeeds, this queue contains the original request and response text**, including audio transcripts; after summarisation only the summary remains pending. Both database backups and the Journiv server may therefore contain sensitive information. Keep the database, backups, `.env`, and Journiv account private. The summariser uses the configured AI model and may omit or misrepresent details. Failures are logged and retried; database write failures are logged but cannot be queued.

Each delivered summary includes a visible event marker used to recognise retries. Do not remove these markers while activity may still be pending. Updates preserve existing entry content and reject conflicting edits, but Journiv does not expose an atomic daily-entry upsert: run only one Minerva instance per Journiv account to avoid concurrent duplicate entries. Avoid renaming Minerva's dated entries. If several matching journals or daily entries exist, delivery stops and logs an error for manual resolution. Changing the server URL or account leaves existing queued events blocked rather than sending them to the new account; restore the original settings to deliver them.

The `ContinuousIntegration` Journiv test uses `JOURNIV_TEST_URL` when supplied and creates then deletes a dedicated test account. Point it only at a disposable server with password registration enabled; integration tests are not run locally.

## Local backup and restore

Backups are local ZIP archives and are never uploaded or synced. The archive contains a PostgreSQL custom-format dump (including conversation/session and chat-memory tables) and user-managed `skills`, `memories`, `personality.md`, and `heartbeat.md` data. Logs, temporary uploads, Ollama models, `.env` values, and database credentials are excluded. The archive has a versioned manifest and SHA-256 checksums.

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
* JOURNIV_TIME_ZONE, JOURNIV_RETRY_INTERVAL - Optional, Journiv date zone and retry interval

# Related projects
* BraincraftHMI - A simple server wrapper for Adafruit's Braincraft Hat. Minerva *will* have an integrated advisor that outputs to the Braincraft Hat's display; the HMI side will be able to send data to Minerva.

# Raspberry PI Quickstart
If you simply want to try this out on a Raspberry Pi 5 8/16GB(I wouldn't recommend this on anything else), you can quickly get started by doing the following:
* Create a directory and drop the prod-compose file into it
* Create an .env file, with the values outline above
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
