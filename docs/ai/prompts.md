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

---

## 2026-09-06 — Phase 1 requirements document

**Phase:** 1
**Tool:** Claude Code (Opus 5)

**Prompt / intent**

> Write the one-page requirements document, before any feature code.

**Outcome**

- `docs/requirements.md`: goal, persona, the five questions the system must answer,
  in-scope table, out-of-scope list with reasoning, assumptions, non-functional
  requirements.

**What we accepted / changed / rejected**

- Framed scope around **five questions the HR Manager needs answered** rather than a
  feature list. The brief's problem statement emphasises "answer questions about how
  the org pays people", so features are justified by the question they serve.
- **Included** a nullable gender attribute for pay-gap analysis, but constrained it to
  aggregates only and with a minimum-group-size threshold. Rejected both alternatives:
  omitting it entirely (the system then cannot answer the equity question at all) and
  including it unguarded (risks identifying individuals from small cohorts).
- **Cut bulk CSV import**, despite it fitting the "escape from Excel" narrative,
  because the seed script already demonstrates bulk loading. Recorded in the document
  as the closest thing to a regret, rather than silently dropped.
- **Rejected live FX rates** in favour of a snapshotted rate table with an `as_of`
  date — a live feed makes tests non-deterministic and historical reports
  irreproducible.
- Scoped compensation to **annual base salary only**, noting that bonus/equity would
  be an extra table rather than a redesign, so the cut is cheap to reverse.

---

## 2026-09-07 — Phase 2 data model and architecture

**Phase:** 2
**Tool:** Claude Code (Opus 5)

**Prompt / intent**

> Design the data model and architecture; write the migrations and JPA entities.

**Outcome**

- `docs/architecture.md` with a Mermaid ER diagram and the five load-bearing decisions.
- `V1__initial_schema.sql`, `V2__reference_data.sql`, and eight JPA entities.
- `./mvnw verify` green: Flyway applies both migrations and Hibernate validates every
  entity mapping against the result.

**What we accepted / changed / rejected**

- **Caught a real bug before it shipped.** The first draft of the derived salary bands
  divided USD _cents_ by the FX rate for every currency, which made JPY bands 100x too
  large (JPY has no minor units). Fixed by adding a `currency` table with an
  `exponent` column and routing every conversion through it. Verified against a live
  Postgres: L1 Japan is now ¥7,835,821 ≈ $52,500, not ¥783,582,100.
- **`ddl-auto: validate` earned its keep immediately** — it failed the build on a
  `CHAR` vs `VARCHAR` mismatch that would otherwise have surfaced as blank-padded
  `'USD '` strings at runtime. Fixed in the schema rather than by annotating around
  it, since Postgres documents no advantage for `CHAR`.
- **Rejected `IDENTITY` columns in favour of sequences with `INCREMENT BY 50`.**
  `IDENTITY` forces Hibernate to read the generated key back per row, silently
  disabling JDBC batching — which would have quietly undermined the Phase 3 seed.
- **Pushed two invariants into the database** rather than the service layer: a gist
  exclusion constraint for non-overlapping pay periods, and a partial unique index for
  at most one open record. Both were verified by attempting the violating inserts;
  service-layer checks would have had a read-then-write race.
- Deliberately did **not** annotate the generated `full_name` column with
  `@Generated` — it would trigger a re-select per insert and defeat batching.
- **Derived the 48 salary bands in SQL** from a per-level USD midpoint and a
  per-country cost factor, rather than hand-writing 48 rows and 48 chances to
  misplace a zero.
- **Cut `manager_id`** from `employee`. Realistic and cheap, but no question in
  requirements.md needs an org hierarchy.

---

## 2026-09-07 — Phase 3 seed script and performance baseline

**Phase:** 3
**Tool:** Claude Code (Opus 5)

**Prompt / intent**

> Build the 10,000-employee seed and record real performance baselines.

**Outcome**

- `DataSeeder` behind the `seed` profile: 10,000 employees and 25,643 compensation
  records in 7.8 s.
- 8 seed tests plus the existing context test, all green.
- [performance.md](../performance.md) with measured timings and query plans.

**What we accepted / changed / rejected**

- **Caught a 10,000-round-trip bug in review, before measuring.** The first draft
  allocated compensation-record identifiers inside the per-employee loop, so each
  employee triggered its own `nextval` query. Hoisted to a single bulk allocation
  after generation. Measuring first would have produced a baseline that looked
  acceptable and quietly encoded the mistake.
- **Also caught a constraint violation waiting to happen**: a terminated employee's
  closing date was clamped to "yesterday", which for a recently-hired employee could
  equal their start date and trip `compensation_period_ordered`. Guarded explicitly.
- **Used JDBC batching rather than JPA for the seed.** Bulk loading gains nothing
  from an entity cache and dirty checking. Identifiers still come from the same
  sequences the entities use, so the two paths cannot collide. This makes the Phase 2
  sequence decision less load-bearing than stated at the time — it still matters for
  the JPA write paths in Phase 4.
- **Rejected a faker library** in favour of fixed name pools. Fewer dependencies, and
  determinism becomes trivially auditable rather than resting on a library's seeding
  behaviour.
- **Deliberately generated out-of-band outliers** (612 below, 620 above). A dataset
  where everyone sits inside their band would make the band-breach insight look
  broken and give an HR manager nothing to act on. Asserted by test, so the property
  cannot silently regress.
- **Terminated employees hold no open compensation record**, so "current headcount"
  means something without a special case in every query.
- Measured the trigram index against a forced sequential scan rather than asserting
  it helps: 0.76 ms vs 9.75 ms, ~13x.
- **Recorded the limitations honestly** rather than only the good numbers — offset
  pagination will not scale past this dataset, and these are single-query timings,
  not load-test results.
