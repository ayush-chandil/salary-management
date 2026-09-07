package com.acme.salary.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.salary.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The seed underpins every later performance claim, so it is tested on the properties that matter:
 * determinism, referential sanity, and the invariants the schema enforces.
 *
 * <p>Uses a small employee count -- the generator's behaviour does not change with volume, and a
 * fast test gets run.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DataSeederTest {

  private static final int COUNT = 200;
  private static final long SEED = 1234L;

  @Autowired private DataSeeder seeder;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void seedsRequestedNumberOfEmployeesWithHistory() {
    SeedSummary summary = seeder.seed(COUNT, SEED, true);

    assertThat(summary.employees()).isEqualTo(COUNT);
    assertThat(count("employee")).isEqualTo(COUNT);

    // Every employee has at least one compensation record, and most have several.
    assertThat(summary.compensationRecords()).isGreaterThan(COUNT);
    assertThat(count("compensation_record")).isEqualTo(summary.compensationRecords());
  }

  @Test
  void isDeterministic() {
    seeder.seed(COUNT, SEED, true);
    List<String> first = fingerprint();

    seeder.seed(COUNT, SEED, true);
    List<String> second = fingerprint();

    assertThat(second).as("same random seed must produce identical data").isEqualTo(first);
  }

  @Test
  void differentSeedProducesDifferentData() {
    seeder.seed(COUNT, SEED, true);
    List<String> first = fingerprint();

    seeder.seed(COUNT, SEED + 1, true);

    assertThat(fingerprint()).isNotEqualTo(first);
  }

  @Test
  void everyNonTerminatedEmployeeHasExactlyOneCurrentRecord() {
    seeder.seed(COUNT, SEED, true);

    Integer offenders =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM (
                SELECT e.id
                FROM employee e
                LEFT JOIN compensation_record c
                       ON c.employee_id = e.id AND c.effective_to IS NULL
                WHERE e.employment_status <> 'TERMINATED'
                GROUP BY e.id
                HAVING count(c.id) <> 1
            ) bad
            """,
            Integer.class);

    assertThat(offenders).isZero();
  }

  @Test
  void terminatedEmployeesHoldNoOpenRecord() {
    seeder.seed(COUNT, SEED, true);

    Integer open =
        jdbc.queryForObject(
            """
            SELECT count(*)
            FROM employee e
            JOIN compensation_record c ON c.employee_id = e.id
            WHERE e.employment_status = 'TERMINATED' AND c.effective_to IS NULL
            """,
            Integer.class);

    assertThat(open).as("a terminated employee should not still be being paid").isZero();
  }

  @Test
  void producesOutOfBandOutliersForTheInsightsToFind() {
    SeedSummary summary = seeder.seed(COUNT, SEED, true);

    // A dataset where everyone sits inside their band would make the band-breach
    // insight look broken and give an HR manager nothing to act on.
    assertThat(summary.belowBand()).isPositive();
    assertThat(summary.aboveBand()).isPositive();
  }

  @Test
  void amountsAreDenominatedInTheEmployeeCountryCurrency() {
    seeder.seed(COUNT, SEED, true);

    Integer mismatches =
        jdbc.queryForObject(
            """
            SELECT count(*)
            FROM compensation_record c
            JOIN employee e ON e.id = c.employee_id
            JOIN country ct ON ct.code = e.country_code
            WHERE c.currency_code <> ct.currency_code
            """,
            Integer.class);

    assertThat(mismatches).isZero();
  }

  @Test
  void resetClearsPreviousData() {
    seeder.seed(COUNT, SEED, true);
    seeder.seed(COUNT, SEED, true);

    assertThat(count("employee")).as("reset should replace, not accumulate").isEqualTo(COUNT);
  }

  private int count(String table) {
    return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
  }

  /** A stable projection of the generated data, ordered so comparison is meaningful. */
  private List<String> fingerprint() {
    return jdbc.queryForList(
        """
        SELECT e.employee_code || '|' || e.full_name || '|' || e.country_code
               || '|' || e.employment_status || '|' || c.amount_minor
        FROM employee e
        JOIN compensation_record c ON c.employee_id = e.id
        ORDER BY e.employee_code, c.effective_from
        """,
        String.class);
  }
}
