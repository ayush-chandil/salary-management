# Performance notes

Measurements, not intentions. The dataset size in the brief is a hint rather than
decoration: 10,000 employees is enough that naive choices become visible.

**Environment.** Postgres 16 (alpine) in Docker on a local x86_64 macOS machine,
default `postgres.conf`, `ANALYZE` run before measuring. Absolute numbers would
differ on other hardware; the ratios are the point.

**Dataset.** 10,000 employees, 25,643 compensation records, of which 9,709 are
current (the other 291 employees are terminated and hold no open record).
Distributed across 8 countries, 10 departments and 6 levels, with weighted rather
than uniform distributions.

## Seeding

| Operation                                    | Rows   | Time      |
| -------------------------------------------- | ------ | --------- |
| Full seed (employees + compensation history) | 35,643 | **7.8 s** |

Inserts go through JDBC batches of 1,000, and identifiers are drawn from the
sequences in two bulk round trips rather than one call per row. The first draft
allocated compensation identifiers inside the per-employee loop — 10,000 separate
round trips — which was caught and fixed before measuring.

## Query baselines

| Query                                                 | Time        |
| ----------------------------------------------------- | ----------- |
| Directory, page 1, sorted, with current pay           | **17.0 ms** |
| Directory, deep page (`OFFSET 9000`)                  | **21.6 ms** |
| Name substring search (`ILIKE '%patel%'`)             | **0.8 ms**  |
| Insights: p25/median/p75 by department, FX-normalised | **70.1 ms** |

All comfortably inside the "well under a second" requirement in
[requirements.md](requirements.md).

## What the indexes are actually doing

**Trigram index on `full_name`** — the clearest win, and the reason it exists:

| Plan                                               | Time    |
| -------------------------------------------------- | ------- |
| Bitmap index scan on `idx_employee_full_name_trgm` | 0.76 ms |
| Sequential scan (index disabled)                   | 9.75 ms |

**~13× faster**, and the gap widens with row count: the sequential scan is linear
in table size while the index scan is not. `LIKE '%…%'` cannot use a B-tree at all,
so without the trigram index there is no alternative to a full scan.

**Partial index on `(employee_id) WHERE effective_to IS NULL`** — the directory
query plan confirms it is used to join current pay, reading 9,709 rows rather than
scanning all 25,643. The ratio grows over time: every raise adds a historical row
and leaves the current-row count unchanged.

## Honest limitations

- **Offset pagination is fine here, and would not stay fine.** A deep page costs
  21.6 ms at 10,000 rows because Postgres still walks the skipped rows. At 10× this
  size that becomes noticeable, and the fix is keyset pagination — deliberately not
  implemented, because it complicates the sort/filter API for a problem this dataset
  does not have.
- **The directory sorts via a sequential scan** on `employee`. Sorting 9,300 rows
  costs little, and an index on `full_name` would only help when no filters narrow
  the set first. Not added on the strength of a guess.
- **Aggregation is the slowest operation, at 70 ms**, which is expected: it reads
  every current record, converts each through its currency exponent and FX rate, and
  computes three percentiles per department. It is still an order of magnitude
  cheaper than transferring 9,709 rows to compute the same numbers in Java — which
  is the alternative this design exists to avoid.
- **These are single-query timings, not load-test results.** No concurrency was
  measured; that would be a different exercise.

## Reproducing

```bash
npm run db:up
cd api && ./mvnw spring-boot:run -Dspring-boot.run.profiles=seed
```

The seed is deterministic — `app.seed.random-seed` fixes the output, so these
numbers can be re-measured against identical data.
