# AI Usage Log

The assessment asks us to use AI tools intentionally and to commit the prompts and
instructions we used. This file is the running log.

Convention: one entry per meaningful interaction, appended as work happens — not
reconstructed afterwards. Record what we asked, what came back, and (most
importantly) **what we changed or rejected**, since the edits are the signal.

---

## Template

### YYYY-MM-DD — <short title>

**Phase:** <0–7>
**Tool:** <Claude Code / other>

**Prompt / intent**

> ...

**Outcome**

- ...

**What we accepted / changed / rejected**

- ...

---

## 2026-09-06 — Understand the assessment brief

**Phase:** 0
**Tool:** Claude Code (Opus 5)

**Prompt / intent**

> Analyse the assessment document (SharePoint link) and summarise what it actually asks for.

**Outcome**

- Extracted the brief: build employee salary management software for a 10,000-employee
  org, HR Manager persona, web UI + backend, deployed, with a video demo.
- Identified four implicit requirements that are graded but not stated as features:
  AI usage is itself assessed; commit history is an artifact; scope discipline is the
  real test; 10,000 is a performance hint.

**What we accepted / changed / rejected**

- Accepted the reading of "answer questions about how the org pays people" as an
  analytics requirement rather than plain CRUD — this drives the insights feature.

---

## 2026-09-06 — Phase plan for execution

**Phase:** 0
**Tool:** Claude Code (Opus 5)

**Prompt / intent**

> Produce a phased execution plan for the project.

**Outcome**

- Eight phases (0–7), ~22–25 focused hours, with explicit "cut this first" guidance.

**What we accepted / changed / rejected**

- Changed the proposed backend from Node/TypeScript to **Spring Boot (Java 17)** to
  match the JD; frontend stays **Next.js**.

---

## 2026-09-06 — Phase 0 scaffold

**Phase:** 0
**Tool:** Claude Code (Opus 5)

**Prompt / intent**

> Scaffold the monorepo: Spring Boot API, Next.js web, docs, CI, tooling.

**Outcome**

- `api/` (Spring Boot 4.1.1, Java 17, Maven) + `web/` (Next.js 16, React 19, TS,
  Tailwind 4), `docs/`, Docker Compose Postgres 16, GitHub Actions CI.
- Quality gates wired: Spotless (Google Java Format) + JaCoCo on the API,
  ESLint + Prettier on the web, husky + lint-staged at the root.
- `./mvnw verify` green end to end, including a Testcontainers-backed context load.

**What we accepted / changed / rejected**

- **Rejected Gradle, switched to Maven** partway through, on the reasoning that
  Maven is more immediately readable to a wider set of Java reviewers.
- `start.spring.io` is blocked on the corporate network (Zscaler, "Developer Tools"
  category) for both the sandboxed shell and the in-app browser. Generated the
  project from a browser outside that policy rather than hand-rolling the POM —
  Boot 4.1 renamed the starters (`spring-boot-starter-webmvc`, per-module `-test`
  starters), so a hand-derived POM was likely to be subtly wrong.
- Changed the Testcontainers image from the generated `postgres:latest` to
  `postgres:16-alpine`, matching `docker-compose.yml`. `latest` makes test results
  depend on when the image was last pulled, and the brief asks for deterministic tests.
- Set `ddl-auto: validate` rather than the Hibernate default, so Flyway is
  unambiguously the owner of the schema.
- Deliberately kept the pre-commit hook to web/docs only. Booting Maven on every
  commit would discourage the incremental committing the brief asks for; CI runs
  `spotless:check` instead.
