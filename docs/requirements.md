# Requirements & scope

_Written before implementation. Decisions here are deliberate; the reasoning matters
more than the feature count._

## Goal

Give ACME's HR Manager a single place to maintain compensation for ~10,000 employees
across multiple countries, and — more importantly — to **answer questions about how
the organisation pays people** without exporting anything to a spreadsheet.

The spreadsheets ACME uses today are not failing at storage. They are failing at
_asking questions_: "are we paying our Berlin engineers consistently?", "who is below
their band?", "what does a 4% uplift to Level 3 cost?". Those questions are the
product.

## Who this is for

One persona: the **HR Manager**. Not employees, not line managers, not payroll.
A single, trusted, authenticated operator. This narrows the product sharply and is
the single biggest scope-reducing decision in this document.

## The questions the system must answer

These drive the feature list, rather than the other way round:

1. What is this employee paid now, and how did that change over time, and why?
2. What does a group cost us — by department, country, or level?
3. How is pay distributed within a group? Median, spread, outliers.
4. Who sits outside their salary band, high or low?
5. Are comparable people paid comparably?

## In scope

| #   | Capability                                                                                                              | Why it earns its place                                                                                             |
| --- | ----------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| 1   | **Employee directory** — server-side search, filter, sort, pagination over 10k                                          | The everyday surface. At 10k rows this is also where performance is proven.                                        |
| 2   | **Employee detail** — profile plus full compensation history                                                            | Answers Q1. History is free once compensation is effective-dated.                                                  |
| 3   | **Record a salary change** — new effective-dated record with an effective date and a reason                             | The one write operation that matters. The reason field is what makes history auditable rather than merely present. |
| 4   | **Compensation insights** — headcount and cost, distribution and percentiles by department/country/level, band outliers | Answers Q2–Q5. This is the difference between a CRUD app and the product the brief describes.                      |
| 5   | **Multi-currency, normalised** — amounts stored in local currency, compared in a base currency                          | Aggregating across countries is meaningless without it.                                                            |
| 6   | **Salary bands** per level and country                                                                                  | Turns "what do we pay?" into "what _should_ we pay?" — enables compa-ratio and outlier detection.                  |

## Out of scope — and why

Each of these is a deliberate cut, not an oversight.

- **Authentication, roles and permissions.** One persona means one role. Real RBAC is
  well-understood, adds schema and UI surface, and would demonstrate nothing that the
  rest of the system doesn't. The API assumes a trusted HR-manager caller.
- **Payroll processing** — payslips, tax, deductions, disbursement. This is a
  compensation _record_ system, not a payroll engine. Payroll is a regulated domain
  where a half-built version is worse than none.
- **Approval workflows** for salary changes. Real, but it is workflow modelling, not
  compensation modelling, and it would consume the time budget that Q2–Q5 deserve.
- **Live FX rates.** Rates come from a snapshotted table with an `as_of` date. A live
  feed would make every test non-deterministic and every historical report
  irreproducible — the wrong trade for a system whose job is answering questions
  consistently.
- **Employee self-service and manager views.** Different personas, different product.
- **Bulk CSV import.** Tempting, given the spreadsheet origin story, and the closest
  thing to a regret. Cut because the seed script already proves we can load 10k
  records, so import would demonstrate the same capability twice.
- **Internationalisation of the UI.** Multi-country data, single-language interface.
- **Soft deletes and employee offboarding lifecycle.** Employment status is a field;
  the full joiner-mover-leaver lifecycle is not modelled.

## Assumptions

- Compensation means **annual base salary only** — no bonus, equity, or benefits.
  Adding components later is an extra table, not a redesign.
- One active compensation record per employee at a time. No parallel contracts.
- Currency is a property of the employee's country. Base currency for comparison is
  **USD**, configurable.
- Salary bands exist for every level/country combination in use.
- Historical amounts are **not** restated when FX rates change; a rate has an
  `as_of` date and reports state which rate they used.

## On sensitive attributes

Question 5 — "are comparable people paid comparably?" — invites gender pay-gap
analysis, which needs a gender attribute. This is a genuine trade-off, so the position
is explicit:

**Included**, as a nullable, self-reported field, with two constraints:

- It is used **only in aggregates**, never surfaced on the employee record in the UI.
- Any breakdown suppresses groups below a **minimum size threshold**, so a cohort can
  never be small enough to identify an individual.

The reasoning: pay equity is one of the most valuable questions an HR manager can ask
of this data, and omitting the field would make the system unable to answer it at all.
Collecting it without the aggregation safeguards would be worse than not collecting it.
If the field is absent or unknown for an employee, they are simply excluded from that
one breakdown.

## Non-functional requirements

- **Directory and insight queries respond in well under a second at 10k employees.**
  Aggregation happens in SQL; no endpoint returns an unbounded collection.
- **Money is never floating point.** Integer minor units plus an ISO-4217 currency.
- **Schema is owned by migrations**, never by an ORM's auto-DDL.
- **Tests are fast and deterministic** — fixed seeds, pinned container images.

## How we will know it works

- The HR Manager can go from the directory to any employee's pay history in two clicks.
- Every insight is answerable without exporting data.
- A salary change is recorded with a date and a reason, and never overwrites history.
- Seeding 10,000 employees and querying them is demonstrably fast, with numbers
  recorded in [performance.md](performance.md).
