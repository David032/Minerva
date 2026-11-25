# Minerva
Minerva is an experimental, entirely local AI system, designed for the raspberry pi 5. Minerva eschews fast responses in favour of slower and more thoughtful responses, 
and is *intended* to be a personal assistant that can be used for a variety of tasks. Minerva is designed to be semi-modular, with functionality implemented either as Java-based tools or advisors, or as Markdown skills.

# Capabilities(W.i.P)
* Short and long term memory
* API based interface
* Media-driven personality customisation
* Personality-driven appearance and speaking-style customisation
* Generated About page with local heartbeat history

## Personality customisation
Open Minerva at `/personality` to upload a PDF, HTML page, or image, or provide a public image URL. You can also upload a ZIP containing up to 10,000 files in nested folders, with shared preference guidance for HTML pages and images. ZIPs are limited to 1 GB compressed, 20 MB per non-CSS file, 2 GB uncompressed in total for non-CSS files, and 10,000 entries including directories and ignored CSS files. CSS files are ignored without being extracted; other unsupported files are reported as failures. Validated files are staged in temporary storage; allow space for the expanded contents and copies of the compressed upload. Files are processed independently and failures are reported; relative references to files within the ZIP are not resolved. Large archives can take a long time to process; keep the page open and ensure any reverse proxy permits a long-running request. Image comments and document guidance are optional; without an image comment, Minerva first asks what the image is about. Minerva stores interest tags and a separate personality prompt in its local `MinervaOS` data directory. Its assistant can update validated colour palettes, layout, typography, and speaking style; review or reset these settings at `/personality`. The generated, editable `/about` biography reflects saved personality and interests, selected library media, and recent local heartbeat activity. It refreshes when those sources change and can replace manual biography edits. HTML-referenced public images, GIFs, and videos are fetched with size and time limits; GIFs and sampled video frames are prepared for the local model. Private-network and localhost URLs are rejected.

Personality imports are dispatched immediately after they are saved; if the worker queue rejects a job, Minerva retries queued jobs every 10 seconds by default. Set `MINERVA_PERSONALITY_IMPORT_POLL_INTERVAL` to a positive interval in milliseconds to change that delay. Queued and interrupted imports are recovered on startup. A busy worker, unavailable database, or delayed scheduler can increase the time before an import starts.

Referenced media downloads share a bounded pool of four workers across requests, and results retain their original order. Document extraction and media preparation can overlap with other personality operations; prompt-dependent analysis and updates remain serialised. If several downloads fail, the first observed failure is reported.

Available colour palettes include Gothic, Cyberpunk, Futuristic, Occult, Grimdark and Dark, alongside Forest, Ocean, Rose and Midnight.

The Docker image includes FFmpeg for video frame extraction. When running the application directly on a host, FFmpeg must be available on `PATH` to analyse referenced videos.

## Interest media library

Open `/library` to manage RSS feeds and Minerva's AI-curated media library together. Submit one public PDF, HTML article, image, or video URL, or upload one PDF, HTML file, or JPEG, PNG, GIF, or WebP image. Minerva evaluates each candidate against its saved interests and personality preferences; rejected candidates are not retained. Minerva can also consider public URLs and retrieve or remove saved items with its media-library tools.

The REST API accepts `multipart/form-data` at `POST /api/library` with exactly one `url` or `file` field. An accepted candidate returns HTTP 201 and its item metadata; a rejected candidate returns HTTP 200 with `accepted: false` and a reason; remote download failures return HTTP 502 with an error message. Use `GET /api/library` to list items, `GET /api/library/{id}` for metadata and extracted text, `GET /api/library/{id}/content` to download saved content, and `DELETE /api/library/{id}` to remove an item.

For bounded listings, use `GET /api/library/page?limit=20` (limit 1–100, default 20). The response contains `items` and `nextCursor`; pass the latter as the `cursor` parameter for the next page, until it is null. Items are newest first, with UUIDs breaking timestamp ties. Pages are not snapshots: items added before the current cursor will appear when starting again at the first page, and deleted items disappear. The original `GET /api/library` and assistant list tool still return the entire list for compatibility and may use substantial memory for large libraries.

Library files remain authoritative. Minerva rebuilds a PostgreSQL metadata index from them at startup and after an interrupted index update, including after backup restore. Startup reports invalid metadata or missing content instead of silently omitting entries. If a database error occurs after a file operation, the request fails even though the file operation may have taken effect; check the library before retrying. Do not edit library files while Minerva is running, and run only one instance against the library and database. PostgreSQL must permit creation of the index table and index.

PDF uploads and public PDF URLs are limited to 20 MB; public PDF URLs must end in `.pdf`. HTML uploads are limited to 20 MB, and public HTML articles to 2 MB. Uploaded images and remotely fetched media are limited to 5 MB each. HTML documents can reference at most 50 public media items with a 20 MB combined limit; no more than 12 prepared images or video frames are sent to the model at once. URLs are limited to 2,048 characters and must use public HTTP(S) destinations on standard ports; private-network addresses and redirects are not allowed. The library stores accepted files and provenance under `library` in Minerva's persistent data directory. Local backups include this directory.

## Skills

Open `/skills` to import a ZIP archive or a public HTTPS ZIP/Git repository. Sources may contain a skill at the root or multiple nested skill directories. Each skill's `SKILL.md` must have `name` and `description` fields in YAML front matter. ZIP uploads are limited to 10 MB; imports reject unsafe archives, local/private network URLs, and name collisions without replacing existing skills. Imported skills are made available to Minerva immediately. Git repository imports require `git` on `PATH`; the Docker image includes it.

The assistant can also find and install skills using its import tool. This uses web search when `BRAVE_API_KEY` is configured; otherwise, provide the assistant with a source URL. Imported skill instructions are third-party content and should be treated cautiously.

## Reminders

Create, list, and cancel one-time reminders in chat, or manage them from the chat page. Reminders are stored in the local PostgreSQL database and survive application restarts. Upcoming reminders remain visible in the chat page until acknowledged; after downtime, overdue reminders are recovered as due. Set `MINERVA_TIME_ZONE` to the desired IANA time-zone ID (defaults to UTC) for local date/time input without an explicit offset. Times in daylight-saving gaps are rejected; ambiguous local times use the earlier offset. Past reminders are rejected. The page polls for due reminders while open; reminders do not send browser or operating-system notifications while the page is closed.

The server checks for due reminders every 5 seconds by default; set `MINERVA_REMINDERS_POLL_INTERVAL` to a positive interval in milliseconds to change it. The chat page refreshes its reminder list separately every 15 seconds while open, so display may lag the due time by roughly the sum of those intervals. Scheduler load or database outages can cause additional delay.

## Heartbeat

Minerva can run a scheduled assistant check using instructions in `heartbeat.md` at the root of its data directory (`MINERVA_DATA_DIR`, or `~/MinervaOS` by default). By default, Minerva checks once at the top of each UTC hour. A missing or empty file is ignored. If an AI request is active at the scheduled tick, that tick is skipped rather than queued; the next check is at the following hour. Set `MINERVA_HEARTBEAT_ENABLED=false` to disable it, or use `MINERVA_HEARTBEAT_CRON` and `MINERVA_HEARTBEAT_ZONE` to change the schedule. A running heartbeat holds the exclusive AI activity slot; new AI requests receive a busy message and can be retried after it finishes. Heartbeat instructions and session history are backed up with other local user data.

Application logs in the data directory's `logs` folder record heartbeat run IDs, tool names, whether calls returned or raised exceptions, and bounded tool results and assistant responses. A separate `memories/heartbeat-memories.json` file keeps the latest 100 timestamped heartbeat outcomes and action counts for the About page, even when Journiv is not connected. **These logs and memory files can contain sensitive information**; restrict access to them. Tool inputs and direct memory-tool results are omitted, but other tool results or assistant responses may contain memory contents or secrets. A returned result is not necessarily a successful action. Memory tools and managed interests also log successful additions, updates, deletions and renames. Generic filesystem and shell operations can change memory files without producing memory-change events. Logs are not included in local backups.

## Managed routines

Ask Minerva to create, list, inspect, update, enable or disable a recurring routine, or to show its recent execution history. Definitions and outcomes are stored in PostgreSQL and included in database backups; they may contain sensitive information. Each enabled routine uses the existing heartbeat schedule, not an individual schedule. At most one routine is attempted per idle tick, choosing the least recently attempted first; busy ticks are skipped, not queued. Disabling the heartbeat also disables routine execution. A restart marks unfinished attempts interrupted rather than replaying them; the routine remains eligible on a later tick.

The default limit is 50 routines, including disabled ones (`minerva.routines.max-count`, 1–1000). Names are limited to 200 characters and instructions to 4000. The assistant has up to 120 seconds per routine (`minerva.routines.timeout-seconds`, 1–600); timeouts and exceptions are recorded, and the latest 20 outcomes are available through the history tool. A successful outcome means the assistant returned a response, not that every requested action succeeded. The normal heartbeat is skipped on a routine timeout.

Cancellation is cooperative: a timed-out tool or model call may ignore interruption and continue in the background, possibly overlapping a later foreground conversation. Further routines and heartbeats are skipped while that worker remains active. Routine timeouts do not bound the separate normal heartbeat, whose existing behaviour is unchanged. Use trusted, bounded instructions and tools.

Run only one Minerva instance against a database: routine selection and restart recovery are not coordinated across instances. Execution history is retained without automatic deletion.

## Tool search

Tool search is experimental and disabled by default. Set `MINERVA_TOOL_SEARCH_ENABLED=true` in the environment (or the Compose `.env` file) and restart Minerva to let the assistant and heartbeat discover tools on demand using a local keyword index. This can reduce the tool definitions sent to the model, but requires the configured model to search for and call tools reliably; keyword searches can also miss relevant tools. If tools stop working, set it to `false` and restart to restore the normal tool-calling path. Minerva does not automatically retry failed tool calls because a tool may already have performed an action.
## Discord

Discord is optional. To connect Minerva to a server:

1. Create an application and bot in the [Discord Developer Portal](https://discord.com/developers/applications). On its Bot page, enable the privileged **Message Content Intent** and obtain the bot token.
2. Use the application's OAuth2 URL generator to invite the bot to your server with the `bot` scope and permission to view channels, read message history and send messages in the channels you want it to use.
3. Set `DISCORD_TOKEN` in your Compose `.env` file and apply the configuration with `docker compose up -d core` (add `-f prod-compose.yaml` after `compose` for production). Keep the token secret; never commit it or share it. Reset it in the Developer Portal if exposed.

Leave `DISCORD_TOKEN` unset or blank to disable Discord. When enabled, Minerva responds to non-bot messages in channels it can access, not just messages that mention it. Limit the bot's channel access accordingly.

## Journiv

To connect the optional Journiv integration:

1. Start a Journiv server reachable from Minerva. For a new account, the server must allow password registration. Journiv may make its first account an administrator; provision your own administrator first if that is not intended. For an existing account, use its existing password.
2. Replace the three empty Journiv entries in the `core` service's `environment` section of `compose.yaml` or `prod-compose.yaml` with the mappings below. Set `JOURNIV_URL` to the server's HTTP(S) base URL and `JOURNIV_EMAIL` and `JOURNIV_PASSWORD` to dedicated account credentials in `.env`. The supplied empty Compose values override `.env`, so setting the values only in `.env` without changing the mappings is not sufficient.
3. Apply the configuration with `docker compose up -d core` (add `-f prod-compose.yaml` after `compose` for production) and check the `core` logs for connection or authentication errors after activity is recorded. Leave `JOURNIV_URL` unset or empty to disable the integration.

```yaml
      JOURNIV_URL: ${JOURNIV_URL:-}
      JOURNIV_EMAIL: ${JOURNIV_EMAIL:-}
      JOURNIV_PASSWORD: ${JOURNIV_PASSWORD:-}
```

From a Compose container, use a hostname reachable from that container; `localhost` refers to the Minerva container itself. Use HTTPS when traffic crosses an untrusted network and keep credentials private.

Minerva creates or reuses its own journal and records concise AI-generated summaries of completed text, image and audio interactions and heartbeat outcomes. Image and audio files are not sent to Journiv. A daily entry is created when there is activity, and the assistant can read prior entries. `JOURNIV_TIME_ZONE` selects the IANA time zone for daily entries; it defaults to `MINERVA_TIME_ZONE`, or UTC. `JOURNIV_RETRY_INTERVAL` sets the retry interval in milliseconds (default 60000).

Pending activity is stored in PostgreSQL for retry after a restart or an outage. **Until summarisation succeeds, this queue contains the original request and response text**, including audio transcripts; after summarisation only the summary remains pending. Both database backups and the Journiv server may therefore contain sensitive information. Keep the database, backups, `.env`, and Journiv account private. The summariser uses the configured AI model and may omit or misrepresent details. Failures are logged and retried; database write failures are logged but cannot be queued.

Each delivered summary includes a visible event marker used to recognise retries. Do not remove these markers while activity may still be pending. Updates preserve existing entry content and reject conflicting edits, but Journiv does not expose an atomic daily-entry upsert: run only one Minerva instance per Journiv account to avoid concurrent duplicate entries. Avoid renaming Minerva's dated entries. If several matching journals or daily entries exist, delivery stops and logs an error for manual resolution. Changing the server URL or account leaves existing queued events blocked rather than sending them to the new account; restore the original settings to deliver them.

The `ContinuousIntegration` Journiv test uses `JOURNIV_TEST_URL` when supplied and creates then deletes a dedicated test account. Point it only at a disposable server with password registration enabled; integration tests are not run locally.

## Conversation logging

The logging advisor records the current user message and completed assistant response at INFO level, including heartbeat requests. For streamed responses it logs at most 10,000 UTF-16 characters followed by `[truncated]` when needed; the stream itself is unchanged. Logs are written to the console and to `logs` under `MINERVA_DATA_DIR` (or `~/MinervaOS` by default). These logs can contain sensitive conversation text; keep them private. Set `LOGGING_LEVEL_COM_MINERVA_ADVISORS=OFF` to disable the advisor's logs. Media attachments are not logged by the advisor; image requests and responses are also logged separately by the conversation manager.

## Local backup and restore

Backups are local ZIP archives and are never uploaded or synced. The archive contains a PostgreSQL custom-format dump (including conversation/session and chat-memory tables) and user-managed `skills`, `memories`, the `library` media collection, `personality.md`, and `heartbeat.md` data. Logs, temporary uploads, Ollama models, `.env` values, and database credentials are excluded. The archive has a versioned manifest and SHA-256 checksums.

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
* DISCORD_TOKEN - Optional, for the Discord bot
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
