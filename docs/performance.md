# Performance notes

> **Baselines land in Phase 3**, once the seed script has generated 10,000
> employees and there is something real to measure.

The dataset size in the brief is a hint, not decoration: 10,000 employees with
1–5 compensation records each is enough that naive choices become visible.
This file records the measurements rather than the intentions.

Planned to capture:

| Measurement                                          | Why it matters                                          |
| ---------------------------------------------------- | ------------------------------------------------------- |
| Seed time for 10k employees                          | Batched inserts vs. a row-at-a-time loop                |
| `GET /api/employees` p95, page 1 and deep pages      | Offset pagination degrades on deep pages                |
| Name search latency                                  | Whether the trigram index is doing its job              |
| Insight aggregations (`percentile_cont`, `GROUP BY`) | The queries most likely to be accidentally done in Java |
| `EXPLAIN ANALYZE` for each insight query             | Evidence of index use rather than sequential scans      |

Each entry records the query, the timing, and — where an index or rewrite
changed things — the before and after.
