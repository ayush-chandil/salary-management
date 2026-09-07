-- Initial schema for salary management.
--
-- Conventions:
--   * Money is stored as an integer count of minor units alongside an ISO-4217
--     currency code. Never floating point. How many minor units make a major
--     unit is a property of the currency (see currency.exponent) -- USD has 2,
--     JPY has 0. Assuming 2 everywhere is a classic and expensive bug.
--   * Compensation is effective-dated: a raise inserts a row and closes the
--     previous one. effective_to IS NULL means "current".
--   * Identifiers come from sequences with INCREMENT BY 50, matching Hibernate's
--     pooled allocation. IDENTITY columns would be simpler, but Hibernate cannot
--     batch inserts when it must read a generated key back per row, and Phase 3
--     inserts 10,000 employees.
--   * Fixed-width codes use VARCHAR, not CHAR. Postgres documents no performance
--     advantage for CHAR, and its blank padding turns 'USD' into 'USD ' on the
--     way back out.

-- gist exclusion constraints over (bigint, daterange) need btree_gist;
-- substring name search needs trigram indexes.
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ---------------------------------------------------------------- reference

CREATE TABLE currency (
    code     VARCHAR(3) PRIMARY KEY,
    name     TEXT       NOT NULL,
    -- Decimal places: 2 for USD/EUR, 0 for JPY. Drives every conversion between
    -- stored minor units and displayed major units.
    exponent SMALLINT   NOT NULL,
    CONSTRAINT currency_exponent_sane CHECK (exponent BETWEEN 0 AND 4)
);

CREATE TABLE country (
    code          VARCHAR(2) PRIMARY KEY,
    name          TEXT       NOT NULL,
    currency_code VARCHAR(3) NOT NULL REFERENCES currency (code)
);

-- Keyed by (currency, as_of) so rates are snapshots rather than mutable state.
-- Reports use the most recent snapshot and record which one they used.
CREATE TABLE fx_rate (
    currency_code VARCHAR(3)     NOT NULL REFERENCES currency (code),
    as_of         DATE           NOT NULL,
    -- Value of one major unit of this currency in base-currency major units.
    rate_to_base  NUMERIC(18, 8) NOT NULL,
    PRIMARY KEY (currency_code, as_of),
    CONSTRAINT fx_rate_positive CHECK (rate_to_base > 0)
);

CREATE SEQUENCE department_seq INCREMENT BY 50;

CREATE TABLE department (
    id   BIGINT PRIMARY KEY,
    code TEXT   NOT NULL UNIQUE,
    name TEXT   NOT NULL
);

CREATE SEQUENCE job_level_seq INCREMENT BY 50;

CREATE TABLE job_level (
    id   BIGINT  PRIMARY KEY,
    code TEXT    NOT NULL UNIQUE,
    name TEXT    NOT NULL,
    -- Seniority order: sorts levels and derives bands.
    rank INTEGER NOT NULL UNIQUE
);

-- ----------------------------------------------------------------- employee

CREATE SEQUENCE employee_seq INCREMENT BY 50;

CREATE TABLE employee (
    id                BIGINT      PRIMARY KEY,
    employee_code     TEXT        NOT NULL UNIQUE,
    first_name        TEXT        NOT NULL,
    last_name         TEXT        NOT NULL,
    -- Generated so search has a single indexable column that can never drift
    -- out of sync with its parts.
    full_name         TEXT        GENERATED ALWAYS AS (first_name || ' ' || last_name) STORED,
    email             TEXT        NOT NULL UNIQUE,
    -- Nullable, and used only in size-suppressed aggregates. See requirements.md.
    gender            TEXT,
    country_code      VARCHAR(2)  NOT NULL REFERENCES country (code),
    department_id     BIGINT      NOT NULL REFERENCES department (id),
    job_level_id      BIGINT      NOT NULL REFERENCES job_level (id),
    job_title         TEXT        NOT NULL,
    hire_date         DATE        NOT NULL,
    employment_status TEXT        NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT employee_gender_known
        CHECK (gender IS NULL OR gender IN ('FEMALE', 'MALE', 'OTHER', 'UNDISCLOSED')),
    CONSTRAINT employee_status_known
        CHECK (employment_status IN ('ACTIVE', 'ON_LEAVE', 'TERMINATED'))
);

-- Columns the directory filters by.
CREATE INDEX idx_employee_department ON employee (department_id);
CREATE INDEX idx_employee_country    ON employee (country_code);
CREATE INDEX idx_employee_level      ON employee (job_level_id);
CREATE INDEX idx_employee_status     ON employee (employment_status);

-- Substring search without a sequential scan on LIKE '%...%'.
CREATE INDEX idx_employee_full_name_trgm ON employee USING gin (full_name gin_trgm_ops);

-- ------------------------------------------------------------- compensation

CREATE SEQUENCE compensation_record_seq INCREMENT BY 50;

CREATE TABLE compensation_record (
    id             BIGINT      PRIMARY KEY,
    employee_id    BIGINT      NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    amount_minor   BIGINT      NOT NULL,
    currency_code  VARCHAR(3)  NOT NULL REFERENCES currency (code),
    effective_from DATE        NOT NULL,
    effective_to   DATE,
    change_reason  TEXT        NOT NULL,
    note           TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT compensation_amount_positive CHECK (amount_minor > 0),
    CONSTRAINT compensation_period_ordered
        CHECK (effective_to IS NULL OR effective_to > effective_from),
    CONSTRAINT compensation_reason_known CHECK (change_reason IN (
        'INITIAL', 'ANNUAL_REVIEW', 'PROMOTION', 'MARKET_ADJUSTMENT',
        'ROLE_CHANGE', 'CORRECTION'
    ))
);

CREATE INDEX idx_compensation_employee ON compensation_record (employee_id);

-- "Current salary" lookups skip the historical rows, which outnumber current
-- ones several times over.
CREATE UNIQUE INDEX idx_compensation_one_current
    ON compensation_record (employee_id)
    WHERE effective_to IS NULL;

-- Pay periods for one employee can never overlap. Enforced here rather than in
-- the service layer, where the read-then-write window would make it racy.
-- daterange(from, NULL) is unbounded above, so an open record conflicts with
-- anything later until it is closed.
ALTER TABLE compensation_record
    ADD CONSTRAINT compensation_no_overlap
    EXCLUDE USING gist (
        employee_id WITH =,
        daterange(effective_from, effective_to) WITH &&
    );

-- --------------------------------------------------------------------- bands

CREATE SEQUENCE salary_band_seq INCREMENT BY 50;

CREATE TABLE salary_band (
    id            BIGINT     PRIMARY KEY,
    job_level_id  BIGINT     NOT NULL REFERENCES job_level (id),
    country_code  VARCHAR(2) NOT NULL REFERENCES country (code),
    currency_code VARCHAR(3) NOT NULL REFERENCES currency (code),
    min_minor     BIGINT     NOT NULL,
    mid_minor     BIGINT     NOT NULL,
    max_minor     BIGINT     NOT NULL,
    CONSTRAINT salary_band_unique UNIQUE (job_level_id, country_code),
    CONSTRAINT salary_band_ordered
        CHECK (min_minor <= mid_minor AND mid_minor <= max_minor)
);
