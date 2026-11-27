---
layout: page
title: Development
permalink: /development/
---

## Building and testing

Clone Minerva and its submodules:

```sh
git clone --recurse-submodules https://github.com/David032/Minerva.git
cd Minerva
```

The Java application targets Java 26. Build it from the repository root with:

```sh
mvn -f core/pom.xml -DskipTests package
```

Run the Docker-free unit tests with:

```sh
mvn -f core/pom.xml '-Dgroups=Unit' test
```

Run the backup script tests with:

```sh
python3 -m unittest discover -s scripts
```

Container-backed `ContinuousIntegration` tests require external services or containers and run in CI; do not run that group locally.

## Contributing

Keep changes focused and include unit tests for new behaviour. Use the existing test tags: `Unit` for local tests and `ContinuousIntegration` for tests that require external services. Update the relevant documentation when behaviour or setup changes.

## GitHub Pages and Wiki publishing

Markdown in `docs/` is the source of truth for both published destinations. The Pages workflow builds the Jekyll site, and the Wiki workflow copies the same pages to the repository Wiki when changes reach the `dev` branch.

To enable publishing:

1. In repository settings, enable GitHub Pages and select **GitHub Actions** as the build and deployment source.
2. If the Wiki has not been initialised yet, create its first page in the GitHub Wiki UI. This creates the separate Wiki Git repository that the publishing workflow mirrors.
3. Create an Actions secret named `WIKI_DEPLOY_TOKEN`. Its token must be able to write to the Minerva Wiki repository.

The Wiki is a separate Git repository, so the workflow uses this secret rather than the workflow's default token. Do not put the token in the repository or documentation. Both workflows can also be started manually from the Actions tab.
