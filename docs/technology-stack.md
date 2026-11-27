---
layout: page
title: Technology stack
permalink: /technology-stack/
---

| Area | Technology |
| --- | --- |
| Core application | Java 26, Spring Boot 4.1.2 and Maven |
| AI integration | Spring AI 2.0.2 and Ollama |
| Database | PostgreSQL 17 |
| Voice service | `SimpleVoice` submodule |
| Packaging and local runtime | Docker and Docker Compose |
| CI and publishing | GitHub Actions |

Version details come from `core/pom.xml` and the Compose files. The default local setup runs the language model through Ollama; Minerva does not require a hosted model API key for the quickstart.
