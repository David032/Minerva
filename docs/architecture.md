---
layout: page
title: Architecture
permalink: /architecture/
---

Minerva is a small set of cooperating services. Docker Compose provides the default local development and Raspberry Pi 5 deployments.

## Services

- **Core** is the Java web application. It serves the user interface and API, coordinates assistant requests, and supplies tools, advisors and imported Markdown skills.
- **PostgreSQL** stores application records, including chat and reminder data. User-managed media, skills, personality settings and other files are kept in Minerva's data directory.
- **Ollama** runs the configured language model locally and is the default model endpoint for the core service.
- **Voice** is the companion service built from the `SimpleVoice` submodule.

The development Compose file builds the core and voice images from the checked-out source. The production Compose file uses published images. Both connect the services on a private Compose network and persist their data in named volumes.

## Data flow

The browser communicates with Core over HTTP on port 8080. Core uses PostgreSQL for structured application data, Ollama for local model requests and Voice for speech functionality. Assistant tools can use local data or optional external integrations when configured.

Minerva's user-managed files are stored under `/MinervaOS` in the core data volume. Database data, Ollama models and voice caches use separate volumes, so removing one service's data does not require deleting the others.

## Repository layout

- `core/` contains the Java application, resources, tests and Maven build.
- `SimpleVoice/` is the Voice service submodule.
- `db/` contains database-related local files; credentials belong in the ignored `.env`, not in tracked files.
- `scripts/` contains local backup and restore support.
- `.github/workflows/` contains CI, image publishing and documentation publishing workflows.
