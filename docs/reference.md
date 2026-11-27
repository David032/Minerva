---
layout: page
title: Additional reference
permalink: /reference/
---

This page keeps operational and integration details that do not belong in the short project overview. See [Features](features) for the application capabilities.

## Environment settings

The quickstart needs only `POSTGRES_PASSWORD` in the root `.env` file. The same value is passed to Core and PostgreSQL. Do not commit `.env` or share its contents.

| Variable | Purpose |
| --- | --- |
| `IO_KEY`, `IO_USERNAME` | Optional Adafruit IO credentials. Set both to enable its feed tools. |
| `BRAVE_API_KEY` | Optional key for Brave web and image search. Without a non-blank key those tools are not registered. |
| `DISCORD_TOKEN` | Optional Discord bot token. |
| `JOURNIV_URL`, `JOURNIV_EMAIL`, `JOURNIV_PASSWORD` | Optional Journiv connection settings; see below for the Compose configuration needed to override its empty defaults. |

## Optional integrations

### Adafruit IO

Set `IO_KEY` and `IO_USERNAME` in `.env` to enable Adafruit IO feed tools. Leave either value unset or blank to keep the integration disabled. Restart the `core` service after changing the values.

### Brave search

Set `BRAVE_API_KEY` in `.env` to register Brave web and image search tools. Leave it unset or blank to disable those tools; Minerva still starts without it. Restart the `core` service after changing the value.

### Discord

To connect Minerva to a Discord server:

1. Create an application and bot in the [Discord Developer Portal](https://discord.com/developers/applications). On its Bot page, enable the privileged **Message Content Intent** and obtain the bot token.
2. Use the application's OAuth2 URL generator to invite the bot with the `bot` scope and permission to view channels, read message history and send messages in the channels you want it to use.
3. Set `DISCORD_TOKEN` in the Compose `.env` file and apply the configuration with `docker compose up -d core` (add `-f prod-compose.yaml` after `compose` for production).

Leave `DISCORD_TOKEN` unset or blank to disable Discord. When enabled, Minerva responds to non-bot messages in channels it can access, not only messages that mention it. Limit the bot's channel access accordingly. Keep the token secret and reset it in the Developer Portal if it is exposed.

### Journiv

To connect the optional Journiv integration:

1. Start a Journiv server reachable from Minerva. For a new account, the server must allow password registration. Journiv may make its first account an administrator; provision your own administrator first if that is not intended. For an existing account, use its existing password.
2. Set `JOURNIV_URL`, `JOURNIV_EMAIL` and `JOURNIV_PASSWORD` in `.env`, then replace the three empty Journiv entries in the `core` service's `environment` section of `compose.yaml` or `prod-compose.yaml` with:

   ```yaml
   JOURNIV_URL: ${JOURNIV_URL:-}
   JOURNIV_EMAIL: ${JOURNIV_EMAIL:-}
   JOURNIV_PASSWORD: ${JOURNIV_PASSWORD:-}
   ```

   The existing empty Compose values override `.env`, so setting the values only in `.env` is not sufficient.
3. Apply the configuration with `docker compose up -d core` (add `-f prod-compose.yaml` after `compose` for production) and check the `core` logs for connection or authentication errors after activity is recorded. Leave `JOURNIV_URL` unset or empty to disable the integration.

From a Compose container, use a hostname reachable from that container; `localhost` refers to the Minerva container itself. Use HTTPS when traffic crosses an untrusted network and keep credentials private.

Minerva creates or reuses its own journal and records concise AI-generated summaries of completed text, image and audio interactions and heartbeat outcomes. Image and audio files are not sent to Journiv. A daily entry is created when there is activity, and the assistant can read prior entries. `JOURNIV_TIME_ZONE` selects the IANA time zone for daily entries; it defaults to `MINERVA_TIME_ZONE`, or UTC. `JOURNIV_RETRY_INTERVAL` sets the retry interval in milliseconds (default 60000).

Pending activity is stored in PostgreSQL for retry after a restart or outage. Until summarisation succeeds, this queue contains the original request and response text, including audio transcripts; after summarisation only the summary remains pending. Database backups and the Journiv server may therefore contain sensitive information. Keep the database, backups, `.env` and Journiv account private. The summariser uses the configured AI model and may omit or misrepresent details. Failures are logged and retried; database write failures cannot be queued.

Each delivered summary includes a visible event marker used to recognise retries. Do not remove these markers while activity may still be pending. Updates preserve existing entry content and reject conflicting edits, but Journiv does not expose an atomic daily-entry upsert. Run only one Minerva instance per Journiv account to avoid concurrent duplicate entries. Avoid renaming Minerva's dated entries. If several matching journals or daily entries exist, delivery stops and logs an error for manual resolution. Changing the server URL or account leaves existing queued events blocked rather than sending them to the new account; restore the original settings to deliver them.

The `ContinuousIntegration` Journiv test uses `JOURNIV_TEST_URL` when supplied and creates then deletes a dedicated test account. Point it only at a disposable server with password registration enabled; integration tests are not run locally.

## Logging and privacy

The conversation logging advisor records the current user message and completed assistant response at INFO level, including heartbeat requests. For streamed responses it logs at most 10,000 UTF-16 characters followed by `[truncated]`. Logs are written to the console and to `logs` under `MINERVA_DATA_DIR` (or `~/MinervaOS` by default). These logs can contain sensitive conversation text; keep them private. Set `LOGGING_LEVEL_COM_MINERVA_ADVISORS=OFF` to disable the advisor's logs.

Media attachments are not logged by the advisor; image requests and responses are also logged separately by the conversation manager. Heartbeat logs record run IDs, tool names, whether calls returned or raised exceptions, and bounded tool results and assistant responses. A separate `memories/heartbeat-memories.json` file keeps the latest 100 timestamped heartbeat outcomes and action counts for the About page, even when Journiv is not connected. Tool inputs and direct memory-tool results are omitted, but other results and assistant responses may contain memory contents or secrets. A returned result is not necessarily a successful action. Generic filesystem and shell operations can change memory files without producing memory-change events. Logs are not included in local backups.

## Local backup and restore

Backups are local ZIP archives and are never uploaded or synced. They contain a PostgreSQL custom-format dump (including conversation/session and chat-memory tables) and user-managed `skills`, `memories`, the `library` media collection, `personality.md` and `heartbeat.md`. Logs, temporary uploads, Ollama models, `.env` values and database credentials are excluded. The archive has a versioned manifest and SHA-256 checksums.

With the Compose stack running, create a backup at a path outside the repository:

```sh
python3 scripts/minerva_backup.py backup --output /path/to/minerva-backup.zip
```

For the production Compose file, add `-f prod-compose.yaml` before `backup`:

```sh
python3 scripts/minerva_backup.py -f prod-compose.yaml backup --output /path/to/minerva-backup.zip
```

Existing backup files are not overwritten unless `--force` is provided.

Restore replaces the PostgreSQL database and backed-up user data; it does not merge. It requires the explicit `--replace` confirmation:

```sh
python3 scripts/minerva_backup.py restore --archive /path/to/minerva-backup.zip --replace
```

Restore also requires the Compose `core` and `db` services to be running. The script validates the archive and database dump before stopping Minerva, stages file changes, and retains rollback data until both database and file restoration succeed. Minerva is stopped while the database and files are captured or restored, then restarted. Keep backups private: conversations and memories may contain sensitive personal data.

The Compose data volume is mounted at `/MinervaOS`. On first startup after upgrading from the previous `core-data:/home` layout, Minerva copies existing data from `/MinervaOS/app/MinervaOS` into the new locations and leaves the original files in place. If destination data already exists, it is preserved and a warning identifies the legacy copy for manual review.

## Related project

[BraincraftHMI](https://github.com/David032/BraincraftHMI) is a simple server wrapper for Adafruit's Braincraft Hat. Minerva may integrate an advisor that outputs to the display; the HMI side can send data to Minerva.
