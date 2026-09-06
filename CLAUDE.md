# Project conventions

Employee salary management software for a 10,000-employee organisation.
Persona: HR Manager. See `docs/requirements.md` for scope.

## Layout

| Path                 | What                                                     |
| -------------------- | -------------------------------------------------------- |
| `api/`               | Spring Boot 4.1 / Java 17 / Maven — REST API             |
| `web/`               | Next.js 16 / React 19 / TypeScript / Tailwind 4 — UI     |
| `docs/`              | Requirements, architecture, tradeoffs, performance notes |
| `docs/ai/prompts.md` | Running log of AI usage — **append as you go**           |

## Commands

```bash
npm run db:up          # start Postgres (docker compose)
cd api && ./mvnw spring-boot:run
cd web && npm run dev

cd api && ./mvnw verify   # spotless + tests + jacoco
cd web && npm run lint
npm run format            # prettier over web/ + docs/
npm run format:java       # spotless over api/
```

## Non-negotiables

These are the things the assessment actually grades. Do not drift from them.

1. **Money is never a floating-point number.** Store integer minor units
   (`amount_minor BIGINT`) plus an ISO-4217 `currency`. Convert for display only.
2. **Compensation is effective-dated.** A raise is a new `compensation_record`
   row, never an `UPDATE` of the old one. Current row = `effective_to IS NULL`.
3. **Aggregate in SQL, not in Java.** With 10,000 employees, pulling rows into
   the app to compute a median is the failure this dataset size is testing for.
   Use `percentile_cont`, `GROUP BY`, and indexes.
4. **Never return an unbounded list.** All collection endpoints are paginated
   server-side.
5. **Flyway owns the schema.** `ddl-auto` is `validate`. Every schema change is
   a new versioned migration in `api/src/main/resources/db/migration`. Never
   edit a migration that has already been committed — add a new one.
6. **Tests are fast and deterministic.** Fixed seeds, pinned container images,
   no reliance on wall-clock `now()` in assertions.

## Commit style

Conventional commits (`feat:`, `fix:`, `test:`, `docs:`, `chore:`, `perf:`,
`refactor:`), scoped where useful (`feat(api):`, `feat(web):`).

Commit history is a graded artifact for this assessment — commit each meaningful
slice as it lands. Do not squash the history at the end.

## AI usage

Every meaningful prompt goes in `docs/ai/prompts.md` as it happens, including
what was rejected or rewritten. This cannot be reconstructed afterwards.

## Java style

Google Java Format via Spotless (2-space indent). Run `npm run format:java`
before committing Java — the pre-commit hook only covers web/docs, because
booting Maven on every commit is too slow for incremental committing. CI runs
`spotless:check` and will fail on drift.
