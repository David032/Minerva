---
layout: page
title: Minerva
permalink: /
---

Minerva is an experimental, local AI assistant designed with the Raspberry Pi 5 in mind. It favours thoughtful responses over speed and combines a Java application, local AI services, tools and Markdown skills.

## Run with Docker Compose

You need Docker Compose v2. Clone the repository with its submodules:

```sh
git clone --recurse-submodules https://github.com/David032/Minerva.git
cd Minerva
```

Create a `.env` file in the project root:

```dotenv
POSTGRES_PASSWORD=replace-with-a-long-random-password
```

Start Minerva:

```sh
docker compose up -d
```

Open [http://localhost:8080](http://localhost:8080). Keep `.env` private; it is ignored by Git. The development stack builds the application and supporting services locally. For the pre-built Raspberry Pi 5 deployment, run:

```sh
docker compose -f prod-compose.yaml up -d
```

Adafruit IO, Brave search, Discord and Journiv are optional integrations. Configure only the credentials for integrations you want to use.

## Documentation

Use the navigation above for the [architecture](architecture), [development and contributing](development), [features](features), [technology stack](technology-stack) and [additional reference](reference).
