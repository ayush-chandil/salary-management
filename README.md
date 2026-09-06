# Salary Management

Web-based employee salary management software for an organisation of ~10,000
employees across multiple countries. Built for the HR Manager persona: manage
compensation data, and answer questions about how the organisation pays people.

> **Status:** Phase 0 — scaffold. See [docs/plan.md](docs/plan.md) for the phased
> build plan and [docs/requirements.md](docs/requirements.md) for scope (Phase 1).

## Stack

|             |                                                                      |
| ----------- | -------------------------------------------------------------------- |
| **API**     | Spring Boot 4.1, Java 17, Maven, Spring Data JPA, Flyway             |
| **DB**      | PostgreSQL 16                                                        |
| **Web**     | Next.js 16 (App Router), React 19, TypeScript, Tailwind CSS 4        |
| **Tests**   | JUnit 5, Testcontainers, JaCoCo                                      |
| **Quality** | Spotless (Google Java Format), ESLint, Prettier, husky + lint-staged |

## Prerequisites

- JDK 17
- Node 22
- Docker (running) — for Postgres and Testcontainers

## Getting started

```bash
npm install          # repo-level tooling (prettier, husky)
npm run db:up        # start Postgres on :5432
```

**API** — http://localhost:8080

```bash
cd api && ./mvnw spring-boot:run
```

**Web** — http://localhost:3000

```bash
cd web && npm install && npm run dev
```

## Quality gates

```bash
cd api && ./mvnw verify      # Spotless + JUnit + JaCoCo
cd web && npm run lint
npm run format:check         # Prettier
```

CI runs all three on every push and pull request.

## Repository layout

```
api/     Spring Boot REST API
web/     Next.js UI
docs/    Requirements, architecture, tradeoffs, performance, AI usage log
```

## Documentation

- [Build plan](docs/plan.md) — phases and sequencing
- [Requirements & scope](docs/requirements.md) — including what is deliberately out
- [Architecture](docs/architecture.md) — data model and design decisions
- [AI usage log](docs/ai/prompts.md) — prompts and how their output was used
