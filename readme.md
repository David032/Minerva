# Minerva

Minerva is an experimental, local AI assistant designed for the Raspberry Pi 5. It favours thoughtful responses over speed and combines a core Java application, local AI services and a few other bells and whistles.

## Highlights
* Configurable intrests & knowledge. Minerva can be provided with media, links or items that it'll use to work out what it's intrested in & how it feels about things
* Personality customisation. Minerva's appearence, personality & speaking style change based on it's intrests.
* Generates it's own 'About' page based on it's intrests & activity
* Independent activity. Minerva will occasionally go off and do something of it's own accord. It can: look at things it finds interesting, catch-up on news or other feeds, research more about a new interest, try to understand it's user(i.e. you) better, or just browse the web for a bit


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
