# Build plan

Phased plan for the assessment. Roughly 22–25 focused hours.

If time runs short, cut **Phase 6** scope (fewer screens, simpler charts). Never
cut Phases 1–2 or the tests in Phases 4–5 — the brief says outright that it is
looking for engineering judgement, not the most complex system.

---

## Phase 0 — Groundwork ✅

Monorepo scaffold, tooling, CI, and the conventions everything else inherits.

- `api/` (Spring Boot 4.1, Java 17, Maven) and `web/` (Next.js 16, React 19, TS, Tailwind 4)
- Spotless (Google Java Format) + JaCoCo; ESLint + Prettier; husky + lint-staged
- GitHub Actions: `api` (`./mvnw verify`), `web` (lint + build), `format`
- Postgres 16 via `docker-compose.yml`, pinned to the same image Testcontainers uses
- `CLAUDE.md` conventions, `docs/ai/prompts.md` AI log started

## Phase 1 — Requirements document ✅

One page, written _before_ feature code — see [requirements.md](requirements.md).

Scope is framed around the five questions the HR Manager needs answered, rather
than a feature list. Decisions worth carrying forward into Phase 2:

- Compensation is **annual base salary only** — bonus/equity would be an extra
  table, not a redesign.
- **Gender is included** but nullable, used only in aggregates, and suppressed
  below a minimum group size.
- **FX rates are snapshotted** with an `as_of` date; historical amounts are not
  restated.
- **No auth/RBAC** — a single trusted HR-manager caller.
- Bulk CSV import is cut; the seed script already proves bulk loading.

## Phase 2 — Data model & architecture ✅

[architecture.md](architecture.md) with a Mermaid ER diagram, `V1__initial_schema.sql`,
`V2__reference_data.sql`, and eight JPA entities. `./mvnw verify` proves the entities
match the migrated schema, because `ddl-auto` is `validate`.

Entities: `Currency`, `Country`, `FxRate`, `Department`, `JobLevel`, `Employee`,
`CompensationRecord`, `SalaryBand`.

What the phase settled:

1. Money as integer minor units + ISO-4217 currency — never floats — with the
   **currency exponent** as a first-class column, since JPY has no minor units.
2. Effective-dated compensation, with non-overlap and one-current-record enforced
   by **database constraints** rather than service-layer checks.
3. Salary bands per level + country, derived in SQL rather than hand-written.
4. Sequences with `INCREMENT BY 50` instead of `IDENTITY`, so Phase 3 can batch.
5. `gender` included but nullable; `manager_id` cut.

## Phase 3 — Seed script & performance baseline ✅

`DataSeeder` under the `seed` profile, plus real baselines in
[performance.md](performance.md).

- 10,000 employees and 25,643 compensation records in **7.8 s**, via JDBC batches
  of 1,000 and bulk sequence allocation
- Deterministic: a fixed random seed reproduces identical data, asserted by test
- Weighted distributions (country, department, seniority pyramid) rather than
  uniform, plus deliberate out-of-band outliers for the insights to find
- Terminated employees hold no open record, so current headcount is meaningful
- Trigram index measured at **~13x faster** than the sequential scan it replaces
- 9 tests green

## Phase 4 — Core API & tests

- `GET /api/employees` — server-side pagination, search, filter, sort
- `GET /api/employees/{id}` — with full compensation history
- `POST` / `PATCH /api/employees`
- `POST /api/employees/{id}/compensation` — inserts a new effective-dated row and closes the prior one
- Bean Validation, layered structure (controller → service → repository),
  `@RestControllerAdvice` error handling returning RFC 9457 problem details
- Tests written alongside: unit tests on salary/FX/band domain logic,
  `@SpringBootTest` + MockMvc integration tests per endpoint, Testcontainers Postgres

## Phase 5 — Insights API

The differentiating phase — "answer questions about how the org pays people".

- `GET /api/insights/overview` — headcount, total annualised cost, median
- `GET /api/insights/distribution?groupBy=department|country|level` — p25/median/p75, min/max
- `GET /api/insights/bands` — compa-ratio, counts below band min / above band max
- `GET /api/insights/pay-gap?dimension=…` — if that dimension is kept

Aggregate in SQL (`percentile_cont`, `GROUP BY`) with FX normalisation applied
in-query.

## Phase 6 — Frontend

1. Shell — layout, nav, typed API client, TanStack Query
2. Directory — server-driven table, debounced search, skeletons, empty states
3. Employee detail — profile, current comp, history timeline, band position
4. Edit flows — salary change modal requiring effective date + reason
5. Insights dashboard — a few well-chosen charts, not a wall of them

## Phase 7 — Hardening & delivery

- Performance pass: `EXPLAIN ANALYZE` the insights queries, no N+1;
  update `docs/performance.md` with before/after
- Accessibility pass; error/empty/loading states everywhere
- Deploy API + Postgres + web; seed production
- Record a 3–5 min demo video — the HR Manager's workflow, not a code tour
- Finalise `docs/tradeoffs.md`, tidy `docs/ai/prompts.md`, README with live URLs

---

## Two habits that run across every phase

- **Commit incrementally.** The brief mentions it twice. Do not squash at the end.
- **Log AI prompts as you go** into `docs/ai/prompts.md`. It is a required
  artifact and the only one that cannot be reconstructed afterwards.
