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

## Phase 2 — Data model & architecture

`docs/architecture.md` plus a Mermaid diagram (renders in the repo, no binaries).

Entities: `Employee`, `Department`, `Level`, `Country` (with `fx_rate_to_base`),
`CompensationRecord`, `SalaryBand`.

Three decisions to make loudly, because each is a quality signal:

1. Money as integer minor units + ISO-4217 currency — never floats.
2. Effective-dated compensation (`effective_from` / `effective_to`); a raise is
   an insert, not an update. Gives salary history for free.
3. Salary bands per level + country — unlocks compa-ratio, which is product
   thinking rather than CRUD.

`gender` enables pay-gap analysis (a natural reading of "how the org pays
people") but is sensitive data. Either way, **write down the reasoning**.

## Phase 3 — Seed script & performance baseline

- Fixed-seed generator → 10,000 employees across ~8 countries, ~10 departments,
  6 levels; 1–5 compensation rows each (~30k rows)
- Batched inserts (Hibernate `batch_size`), not a 10k-iteration loop
- Indexes: `department_id`, `country_code`, `level_id`, partial index on
  `effective_to IS NULL`, trigram index for name search
- Record baseline timings in `docs/performance.md`

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
