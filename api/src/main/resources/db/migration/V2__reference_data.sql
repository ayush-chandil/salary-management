-- Reference data: small, stable, and the application is meaningless without it.
-- The 10,000 employees are NOT here -- demo data belongs in the seed command, so
-- an environment that does not want it does not get it.

INSERT INTO currency (code, name, exponent) VALUES
    ('USD', 'US Dollar',         2),
    ('GBP', 'Pound Sterling',    2),
    ('EUR', 'Euro',              2),
    ('INR', 'Indian Rupee',      2),
    ('SGD', 'Singapore Dollar',  2),
    ('BRL', 'Brazilian Real',    2),
    ('JPY', 'Japanese Yen',      0);

INSERT INTO country (code, name, currency_code) VALUES
    ('US', 'United States',  'USD'),
    ('GB', 'United Kingdom', 'GBP'),
    ('DE', 'Germany',        'EUR'),
    ('ES', 'Spain',          'EUR'),
    ('IN', 'India',          'INR'),
    ('SG', 'Singapore',      'SGD'),
    ('BR', 'Brazil',         'BRL'),
    ('JP', 'Japan',          'JPY');

-- A dated snapshot, not a live feed. rate_to_base is the value of one major unit
-- in base-currency (USD) major units.
INSERT INTO fx_rate (currency_code, as_of, rate_to_base) VALUES
    ('USD', DATE '2026-01-01', 1.00000000),
    ('GBP', DATE '2026-01-01', 1.27000000),
    ('EUR', DATE '2026-01-01', 1.08000000),
    ('INR', DATE '2026-01-01', 0.01200000),
    ('SGD', DATE '2026-01-01', 0.74000000),
    ('BRL', DATE '2026-01-01', 0.18000000),
    ('JPY', DATE '2026-01-01', 0.00670000);

INSERT INTO department (id, code, name) VALUES
    (nextval('department_seq'), 'ENG',   'Engineering'),
    (nextval('department_seq'), 'PROD',  'Product'),
    (nextval('department_seq'), 'DES',   'Design'),
    (nextval('department_seq'), 'SALES', 'Sales'),
    (nextval('department_seq'), 'MKT',   'Marketing'),
    (nextval('department_seq'), 'CS',    'Customer Success'),
    (nextval('department_seq'), 'FIN',   'Finance'),
    (nextval('department_seq'), 'HR',    'People'),
    (nextval('department_seq'), 'LEGAL', 'Legal'),
    (nextval('department_seq'), 'OPS',   'Operations');

INSERT INTO job_level (id, code, name, rank) VALUES
    (nextval('job_level_seq'), 'L1', 'Associate',    1),
    (nextval('job_level_seq'), 'L2', 'Professional', 2),
    (nextval('job_level_seq'), 'L3', 'Senior',       3),
    (nextval('job_level_seq'), 'L4', 'Lead',         4),
    (nextval('job_level_seq'), 'L5', 'Principal',    5),
    (nextval('job_level_seq'), 'L6', 'Director',     6);

-- Bands are derived rather than hand-listed: a USD midpoint per level, scaled by
-- a per-country cost factor, converted to local currency at the rate snapshot,
-- then expressed in that currency's minor units. 48 hand-written rows would be
-- 48 chances to misplace a zero.
--
-- The exponent matters: 70,000 USD is 7,000,000 minor units, but the equivalent
-- in JPY is ~10,447,761 minor units, not ~1,044,776,100.
--
-- min/max sit 20% either side of the midpoint.
INSERT INTO salary_band (id, job_level_id, country_code, currency_code, min_minor, mid_minor, max_minor)
SELECT
    nextval('salary_band_seq'),
    d.job_level_id,
    d.country_code,
    d.currency_code,
    ROUND(d.local_mid_minor * 0.80)::BIGINT,
    ROUND(d.local_mid_minor)::BIGINT,
    ROUND(d.local_mid_minor * 1.20)::BIGINT
FROM (
    SELECT
        lvl.id   AS job_level_id,
        c.code   AS country_code,
        cur.code AS currency_code,
        -- USD major -> local major (divide by rate) -> local minor (x 10^exponent)
        (CASE lvl.rank
             WHEN 1 THEN 70000
             WHEN 2 THEN 95000
             WHEN 3 THEN 130000
             WHEN 4 THEN 170000
             WHEN 5 THEN 210000
             WHEN 6 THEN 270000
         END
         * CASE c.code
               WHEN 'US' THEN 1.00
               WHEN 'GB' THEN 0.85
               WHEN 'DE' THEN 0.82
               WHEN 'ES' THEN 0.65
               WHEN 'IN' THEN 0.30
               WHEN 'SG' THEN 0.80
               WHEN 'BR' THEN 0.40
               WHEN 'JP' THEN 0.75
           END
         / fx.rate_to_base
         * POWER(10, cur.exponent)) AS local_mid_minor
    FROM job_level lvl
    CROSS JOIN country c
    JOIN currency cur ON cur.code = c.currency_code
    JOIN fx_rate fx
      ON fx.currency_code = c.currency_code
     AND fx.as_of = DATE '2026-01-01'
) AS d;
