package com.acme.salary.seed;

import com.acme.salary.domain.ChangeReason;
import com.acme.salary.domain.EmploymentStatus;
import com.acme.salary.domain.Gender;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates demo employees and their compensation history.
 *
 * <p>Deliberately not a Flyway migration: reference data is meaningless-without, but demo data
 * should never appear in an environment that did not ask for it.
 *
 * <p>Two properties matter here:
 *
 * <ul>
 *   <li><b>Deterministic</b> -- the same random seed produces byte-identical output, so tests can
 *       assert on specific values and a demo is reproducible.
 *   <li><b>Batched</b> -- rows are inserted via JDBC batches of {@value #BATCH_SIZE} rather than
 *       one statement per row. At 10,000 employees and ~30,000 compensation records the difference
 *       is minutes, not milliseconds.
 * </ul>
 *
 * <p>JDBC is used directly rather than JPA: this is bulk loading, where an entity cache and
 * dirty-checking are pure overhead. Identifiers still come from the same sequences the entities
 * use, so the two paths cannot collide.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataSeeder {

  private static final int BATCH_SIZE = 1000;
  private static final LocalDate TODAY = LocalDate.of(2026, 1, 1);

  private final JdbcTemplate jdbc;

  /** Reference rows the generator draws from. */
  private record Ref(
      List<CountryRef> countries,
      List<DeptRef> departments,
      List<LevelRef> levels,
      Map<String, BandRef> bands) {}

  private record CountryRef(String code, String currency, int weight) {}

  private record DeptRef(long id, String code, int weight) {}

  private record LevelRef(long id, String code, String name, int rank, int weight) {}

  private record BandRef(long minMinor, long midMinor, long maxMinor) {}

  @Transactional
  public SeedSummary seed(int employeeCount, long randomSeed, boolean reset) {
    Instant started = Instant.now();

    if (reset) {
      // compensation_record has ON DELETE CASCADE, so this clears both.
      jdbc.update("DELETE FROM employee");
      log.info("Cleared existing employees");
    }

    Ref ref = loadReferenceData();
    Random random = new Random(randomSeed);

    List<Long> employeeIds = nextIds("employee_seq", employeeCount);

    List<Object[]> employeeRows = new ArrayList<>(employeeCount);
    List<Object[]> compRows = new ArrayList<>();
    int belowBand = 0;
    int aboveBand = 0;

    for (int i = 0; i < employeeCount; i++) {
      long id = employeeIds.get(i);
      CountryRef country = weightedPick(ref.countries(), CountryRef::weight, random);
      DeptRef dept = weightedPick(ref.departments(), DeptRef::weight, random);
      LevelRef level = weightedPick(ref.levels(), LevelRef::weight, random);
      BandRef band = ref.bands().get(level.id() + ":" + country.code());

      String first = NameCatalog.FIRST_NAMES[random.nextInt(NameCatalog.FIRST_NAMES.length)];
      String last = NameCatalog.LAST_NAMES[random.nextInt(NameCatalog.LAST_NAMES.length)];
      String code = "ACME%05d".formatted(i + 1);
      String email = "%s.%s.%d@acme.test".formatted(first.toLowerCase(), last.toLowerCase(), i + 1);

      LocalDate hireDate = TODAY.minusDays(30 + random.nextInt(3650));
      EmploymentStatus status = pickStatus(random);
      Gender gender = pickGender(random);
      String title = "%s %s".formatted(level.name(), NameCatalog.roleFor(dept.code()));

      employeeRows.add(
          new Object[] {
            id,
            code,
            first,
            last,
            email,
            gender == null ? null : gender.name(),
            country.code(),
            dept.id(),
            level.id(),
            title,
            hireDate,
            status.name()
          });

      long currentAmount = drawSalary(band, random);
      if (currentAmount < band.minMinor()) {
        belowBand++;
      } else if (currentAmount > band.maxMinor()) {
        aboveBand++;
      }

      compRows.addAll(
          buildHistory(id, currentAmount, country.currency(), hireDate, status, random));
    }

    // Identifiers are allocated once for the whole run, not once per employee --
    // the
    // latter would be 10,000 round trips to the sequence.
    List<Long> compIds = nextIds("compensation_record_seq", compRows.size());
    for (int i = 0; i < compRows.size(); i++) {
      compRows.get(i)[0] = compIds.get(i);
    }

    batchInsertEmployees(employeeRows);
    batchInsertCompensation(compRows);

    SeedSummary summary =
        new SeedSummary(
            employeeRows.size(),
            compRows.size(),
            belowBand,
            aboveBand,
            Duration.between(started, Instant.now()));
    log.info("Seeded {}", summary.describe());
    return summary;
  }

  // ------------------------------------------------------------------ generation

  /**
   * Draws a salary around the band midpoint.
   *
   * <p>Deliberately produces some out-of-band outliers. A dataset where everyone sits neatly inside
   * their band would make the band-breach insight look broken, and gives an HR manager nothing to
   * find.
   */
  private long drawSalary(BandRef band, Random random) {
    double multiplier = 1.0 + random.nextGaussian() * 0.13;
    multiplier = Math.max(0.55, Math.min(1.55, multiplier));
    long amount = Math.round(band.midMinor() * multiplier);
    // Round to a plausible-looking figure rather than an exact fraction of the
    // midpoint.
    return Math.max(1, amount - (amount % 100));
  }

  /**
   * Builds an effective-dated chain ending at {@code currentAmount}, working backwards by undoing
   * raises. Periods are contiguous and non-overlapping, which the database enforces anyway via
   * {@code compensation_no_overlap}.
   *
   * <p>A terminated employee's final record is closed, so they hold no open record and drop out of
   * "current headcount" naturally.
   */
  private List<Object[]> buildHistory(
      long employeeId,
      long currentAmount,
      String currency,
      LocalDate hireDate,
      EmploymentStatus status,
      Random random) {

    int changes = 1 + random.nextInt(5);

    List<LocalDate> dates = new ArrayList<>();
    dates.add(hireDate);
    LocalDate cursor = hireDate;
    for (int i = 1; i < changes; i++) {
      cursor = cursor.plusMonths(9 + random.nextInt(10));
      if (!cursor.isBefore(TODAY)) {
        break;
      }
      dates.add(cursor);
    }

    // Walk amounts backwards from the current figure by undoing 2-12% raises.
    long[] amounts = new long[dates.size()];
    amounts[dates.size() - 1] = currentAmount;
    for (int i = dates.size() - 2; i >= 0; i--) {
      double raise = 1.02 + random.nextDouble() * 0.10;
      long earlier = Math.round(amounts[i + 1] / raise);
      amounts[i] = Math.max(1, earlier - (earlier % 100));
    }

    List<Object[]> rows = new ArrayList<>(dates.size());
    for (int i = 0; i < dates.size(); i++) {
      boolean last = i == dates.size() - 1;
      LocalDate from = dates.get(i);
      LocalDate to = last ? null : dates.get(i + 1);
      if (last && status == EmploymentStatus.TERMINATED) {
        to = from.plusMonths(3 + random.nextInt(9));
        if (!to.isBefore(TODAY)) {
          to = TODAY.minusDays(1);
        }
        // The clamp above can land on the start date when the last raise was recent.
        // compensation_period_ordered requires effective_to > effective_from.
        if (!to.isAfter(from)) {
          to = from.plusDays(1);
        }
      }
      ChangeReason reason = i == 0 ? ChangeReason.INITIAL : pickReason(random);
      // Index 0 is a placeholder; identifiers are assigned in bulk after generation.
      rows.add(new Object[] {null, employeeId, amounts[i], currency, from, to, reason.name()});
    }
    return rows;
  }

  private EmploymentStatus pickStatus(Random random) {
    int roll = random.nextInt(100);
    if (roll < 93) {
      return EmploymentStatus.ACTIVE;
    }
    return roll < 97 ? EmploymentStatus.ON_LEAVE : EmploymentStatus.TERMINATED;
  }

  /** Nullable by design: "unknown" must be representable, and is excluded from breakdowns. */
  private Gender pickGender(Random random) {
    int roll = random.nextInt(100);
    if (roll < 47) {
      return Gender.FEMALE;
    }
    if (roll < 94) {
      return Gender.MALE;
    }
    if (roll < 96) {
      return Gender.OTHER;
    }
    return roll < 98 ? Gender.UNDISCLOSED : null;
  }

  private ChangeReason pickReason(Random random) {
    int roll = random.nextInt(100);
    if (roll < 60) {
      return ChangeReason.ANNUAL_REVIEW;
    }
    if (roll < 82) {
      return ChangeReason.PROMOTION;
    }
    return roll < 96 ? ChangeReason.MARKET_ADJUSTMENT : ChangeReason.ROLE_CHANGE;
  }

  private <T> T weightedPick(
      List<T> items, java.util.function.ToIntFunction<T> weight, Random random) {
    int total = items.stream().mapToInt(weight).sum();
    int roll = random.nextInt(total);
    for (T item : items) {
      roll -= weight.applyAsInt(item);
      if (roll < 0) {
        return item;
      }
    }
    return items.get(items.size() - 1);
  }

  // --------------------------------------------------------------------- writing

  private void batchInsertEmployees(List<Object[]> rows) {
    String sql =
        """
        INSERT INTO employee (
            id, employee_code, first_name, last_name, email, gender,
            country_code, department_id, job_level_id, job_title, hire_date, employment_status)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
    for (int start = 0; start < rows.size(); start += BATCH_SIZE) {
      jdbc.batchUpdate(sql, rows.subList(start, Math.min(start + BATCH_SIZE, rows.size())));
    }
  }

  private void batchInsertCompensation(List<Object[]> rows) {
    String sql =
        """
        INSERT INTO compensation_record (
            id, employee_id, amount_minor, currency_code,
            effective_from, effective_to, change_reason)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
    for (int start = 0; start < rows.size(); start += BATCH_SIZE) {
      jdbc.batchUpdate(sql, rows.subList(start, Math.min(start + BATCH_SIZE, rows.size())));
    }
  }

  /** Pulls a block of identifiers in one round trip rather than one call per row. */
  private List<Long> nextIds(String sequence, int count) {
    return jdbc.queryForList(
        "SELECT nextval('" + sequence + "') FROM generate_series(1, ?)", Long.class, count);
  }

  // --------------------------------------------------------------------- reading

  private Ref loadReferenceData() {
    List<CountryRef> countries =
        jdbc.query(
            "SELECT code, currency_code FROM country ORDER BY code",
            (rs, i) ->
                new CountryRef(rs.getString(1), rs.getString(2), countryWeight(rs.getString(1))));

    List<DeptRef> departments =
        jdbc.query(
            "SELECT id, code FROM department ORDER BY id",
            (rs, i) -> new DeptRef(rs.getLong(1), rs.getString(2), deptWeight(rs.getString(2))));

    List<LevelRef> levels =
        jdbc.query(
            "SELECT id, code, name, rank FROM job_level ORDER BY rank",
            (rs, i) ->
                new LevelRef(
                    rs.getLong(1),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getInt(4),
                    levelWeight(rs.getInt(4))));

    Map<String, BandRef> bands =
        jdbc
            .query(
                "SELECT job_level_id, country_code, min_minor, mid_minor, max_minor FROM salary_band",
                (rs, i) ->
                    Map.entry(
                        rs.getLong(1) + ":" + rs.getString(2),
                        new BandRef(rs.getLong(3), rs.getLong(4), rs.getLong(5))))
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    if (countries.isEmpty() || departments.isEmpty() || levels.isEmpty() || bands.isEmpty()) {
      throw new IllegalStateException(
          "Reference data is missing -- migrations must run before seeding");
    }
    return new Ref(countries, departments, levels, bands);
  }

  /** Headcount is not spread evenly across the org; a flat distribution looks synthetic. */
  private int countryWeight(String code) {
    return switch (code) {
      case "US" -> 30;
      case "IN" -> 25;
      case "GB" -> 12;
      case "DE" -> 10;
      case "ES" -> 7;
      case "BR" -> 6;
      case "SG" -> 5;
      case "JP" -> 5;
      default -> 5;
    };
  }

  private int deptWeight(String code) {

    return switch (code) {
      case "ENG" -> 30;
      case "SALES" -> 15;
      case "CS" -> 12;
      case "OPS" -> 10;
      case "MKT" -> 8;
      case "PROD" -> 7;
      case "DES" -> 6;
      case "FIN" -> 5;
      case "HR" -> 4;
      case "LEGAL" -> 3;
      default -> 5;
    };
  }

  /** A seniority pyramid: far more juniors than directors. */
  private int levelWeight(int rank) {
    return (switch (rank) {
      case 1 -> 28;
      case 2 -> 30;
      case 3 -> 22;
      case 4 -> 12;
      case 5 -> 6;
      default -> 2;
    });
  }
}
