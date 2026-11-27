---
layout: page
title: Features
permalink: /features/
---

Minerva combines local conversation and memory features with optional tools and user-managed content. Details below describe the current implementation; some capabilities are still evolving.

## Conversation and memory

Minerva keeps conversation history and provides short- and long-term memory tools. Memory and chat data are stored with the local PostgreSQL database; backups and data-directory access should therefore be treated as sensitive.

## Personality and About page

Upload a PDF, HTML page or image at `/personality`, or provide a public image URL. A ZIP can contain up to 10,000 nested files, with a 1 GB compressed limit, 2 GB total uncompressed non-CSS content and a 20 MB limit per non-CSS file. CSS files are ignored. Unsupported files and individual processing failures are reported.

HTML references to public images, GIFs and videos can be processed with size and time limits. Private-network and localhost URLs are rejected. The Docker image includes FFmpeg for video frame extraction; when running directly on a host, FFmpeg must be on `PATH`.

Minerva stores interest tags and a separate personality prompt in its local data directory. The assistant can update validated palettes, layout, typography and speaking style. Review or reset settings at `/personality`. The generated `/about` biography reflects saved personality and interests, selected library media and recent local heartbeat activity; it refreshes when those sources change and can replace manual biography edits.

Imports start after they are saved. If the worker queue rejects a job, queued jobs are retried every 10 seconds by default; set `MINERVA_PERSONALITY_IMPORT_POLL_INTERVAL` to a positive number of milliseconds to change the delay. Queued and interrupted imports are recovered on startup. A busy worker, unavailable database or delayed scheduler can delay processing.

Available palettes include Gothic, Cyberpunk, Futuristic, Occult, Grimdark, Dark, Forest, Ocean, Rose and Midnight.

## Interest media library

Open `/library` to manage RSS feeds and Minerva's AI-curated media library. Submit a public PDF, HTML article, image or video URL, or upload a PDF, HTML file, JPEG, PNG, GIF or WebP image. Minerva evaluates each candidate against saved interests and personality preferences; rejected candidates are not retained.

The REST API accepts one `url` or `file` field at `POST /api/library`. An accepted candidate returns HTTP 201; a rejected candidate returns HTTP 200 with `accepted: false`; remote download failures return HTTP 502. Use `GET /api/library` to list items, `GET /api/library/{id}` for metadata and extracted text, `GET /api/library/{id}/content` to download content, and `DELETE /api/library/{id}` to remove an item.

For bounded listings, use `GET /api/library/page?limit=20` (limit 1-100, default 20). Pass the response's `nextCursor` as the `cursor` parameter for the next page until it is null. Items are newest first, with UUIDs breaking timestamp ties. Pages are not snapshots: items added before the current cursor appear when starting from the first page, and deleted items disappear. The unbounded list endpoint and assistant list tool still return all items.

Library files remain authoritative. Minerva rebuilds a PostgreSQL metadata index from them at startup and after an interrupted update, including after a backup restore. Startup reports invalid metadata or missing content. Do not edit library files while Minerva is running, and run only one instance against the library and database.

PDF uploads and public PDF URLs are limited to 20 MB; public PDF URLs must end in `.pdf`. HTML uploads are limited to 20 MB and public HTML articles to 2 MB. Uploaded images and remotely fetched media are limited to 5 MB each. HTML documents can reference at most 50 public media items with a 20 MB combined limit; at most 12 prepared images or video frames are sent to the model at once. URLs are limited to 2,048 characters and must use public HTTP(S) destinations on standard ports; private-network addresses and redirects are not allowed.

Accepted files and provenance are stored under `library` in Minerva's persistent data directory and included in local backups. PostgreSQL must permit creation of the index table and index.

## Skills

Open `/skills` to import a ZIP archive or public HTTPS ZIP/Git repository. Sources may contain one skill at the root or multiple nested skill directories. Each skill's `SKILL.md` needs `name` and `description` fields in YAML front matter. ZIP uploads are limited to 10 MB. Imports reject unsafe archives, local/private network URLs and name collisions without replacing existing skills. Imported skills are available immediately.

The assistant can also find and install skills using its import tool. It uses web search when `BRAVE_API_KEY` is configured; otherwise, provide a source URL. Treat imported skill instructions as third-party content.

## Reminders

Create, list and cancel one-time reminders in chat, or manage them from the chat page. Reminders are stored in PostgreSQL and survive application restarts. Upcoming reminders remain visible until acknowledged; after downtime, overdue reminders are recovered as due.

Set `MINERVA_TIME_ZONE` to an IANA time-zone ID for local date/time input without an explicit offset; it defaults to UTC. Times in daylight-saving gaps are rejected, ambiguous local times use the earlier offset, and past reminders are rejected. The page polls for due reminders while open; reminders do not send browser or operating-system notifications when the page is closed.

The server checks for due reminders every 5 seconds by default. Set `MINERVA_REMINDERS_POLL_INTERVAL` to a positive number of milliseconds to change that interval. The page refreshes its reminder list every 15 seconds while open, so display may lag by roughly the sum of those intervals. Scheduler load or database outages can cause further delay.

## Heartbeat and routines

Minerva can run a scheduled assistant check using `heartbeat.md` in the root of its data directory (`MINERVA_DATA_DIR`, or `~/MinervaOS` by default). By default, it checks at the top of each UTC hour. A missing or empty file is ignored. If an AI request is active at a scheduled tick, that tick is skipped rather than queued.

Set `MINERVA_HEARTBEAT_ENABLED=false` to disable the heartbeat, or use `MINERVA_HEARTBEAT_CRON` and `MINERVA_HEARTBEAT_ZONE` to change its schedule. A running heartbeat holds the exclusive AI activity slot; new AI requests receive a busy message and can be retried after it finishes.

Ask Minerva to create, list, inspect, update, enable or disable a recurring routine, or to show its recent execution history. Definitions and outcomes are stored in PostgreSQL and included in database backups. Each enabled routine uses the heartbeat schedule; at most one routine is attempted per idle tick, choosing the least recently attempted first. Busy ticks are skipped. Disabling the heartbeat also disables routine execution.

The default limit is 50 routines, including disabled ones (`minerva.routines.max-count`, 1-1000). Names are limited to 200 characters and instructions to 4,000. The assistant has up to 120 seconds per routine (`minerva.routines.timeout-seconds`, 1-600); timeouts and exceptions are recorded, and the latest 20 outcomes are available through the history tool. A successful outcome means the assistant returned a response, not that every requested action succeeded. The normal heartbeat is skipped on a routine timeout.

Cancellation is cooperative: a timed-out tool or model call may ignore interruption and continue in the background. Further routines and heartbeats are skipped while that worker remains active. Routine timeouts do not bound the separate normal heartbeat. Use trusted, bounded instructions and tools. Run only one Minerva instance against a database; routine selection and restart recovery are not coordinated across instances. Execution history is retained without automatic deletion.

## Tool search

Tool search is experimental and disabled by default. Set `MINERVA_TOOL_SEARCH_ENABLED=true` in the environment or Compose `.env` file and restart Minerva to let the assistant and heartbeat discover tools on demand using a local keyword index. This can reduce the tool definitions sent to the model, but requires the configured model to search for and call tools reliably; keyword searches can miss relevant tools. If tools stop working, set it to `false` and restart to restore the normal tool-calling path. Minerva does not automatically retry failed tool calls because a tool may already have performed an action.
