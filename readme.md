# Minerva

Minerva is an experimental, local AI assistant designed with the Raspberry Pi 5 in mind. It favours thoughtful responses over speed and combines a Java application, local AI services, tools and Markdown skills.

## Quickstart

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

Open [http://localhost:8080](http://localhost:8080). Keep `.env` private; it is ignored by Git. The development stack builds the application and supporting services locally. For the pre-built Raspberry Pi 5 deployment, use `docker compose -f prod-compose.yaml up -d` instead.

Adafruit IO, Brave search, Discord and Journiv are optional integrations. Configure only the credentials for the integrations you want to use.

## Documentation

- [Architecture](docs/architecture.md)
- [Development and contributing](docs/development.md)
- [Features](docs/features.md)
- [Technology stack](docs/technology-stack.md)
- [Additional reference](docs/reference.md)

The `docs/` pages are the source for [GitHub Pages](https://david032.github.io/Minerva/) and the [GitHub Wiki](https://github.com/David032/Minerva/wiki). Both are published automatically once the repository's publishing settings are enabled.
