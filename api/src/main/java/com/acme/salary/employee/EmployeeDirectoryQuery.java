package com.acme.salary.employee;

import com.acme.salary.common.PageResponse;
import com.acme.salary.employee.dto.EmployeeSummaryDto;
import com.acme.salary.employee.dto.MoneyDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The directory listing.
 *
 * <p>Native SQL rather than JPA, deliberately. This is a projection joining five tables, computing
 * an FX-normalised figure so results can be sorted by pay across currencies, and paginating over
 * 10,000 rows. Expressed as entities it would load object graphs that are then thrown away.
 *
 * <p>Filters use {@code CAST(:param AS ...) IS NULL OR ...} rather than string-concatenated SQL, so
 * there is one prepared statement and no injection surface.
 */
@Repository
@RequiredArgsConstructor
public class EmployeeDirectoryQuery {

  private final NamedParameterJdbcTemplate jdbc;

  /**
   * Sortable columns, whitelisted.
   *
   * <p>The only defence against injection through a sort parameter is never letting caller input
   * reach the SQL -- the key selects an expression, it does not become one.
   */
  private static final Map<String, String> SORTS =
      Map.of(
          "name", "e.full_name",
          "salary", "base_amount",
          "hireDate", "e.hire_date",
          "level", "l.rank",
          "department", "d.name",
          "country", "ct.name");

  private static final String FROM_AND_WHERE =
      """
      FROM employee e
      JOIN department d ON d.id = e.department_id
      JOIN country ct ON ct.code = e.country_code
      JOIN job_level l ON l.id = e.job_level_id
      LEFT JOIN compensation_record cr
             ON cr.employee_id = e.id AND cr.effective_to IS NULL
      LEFT JOIN currency cu ON cu.code = cr.currency_code
      LEFT JOIN fx_rate fx
             ON fx.currency_code = cr.currency_code
            AND fx.as_of = (SELECT max(as_of) FROM fx_rate)
      WHERE (CAST(:search AS text) IS NULL
             OR e.full_name ILIKE :searchPattern
             OR e.email ILIKE :searchPattern
             OR e.employee_code ILIKE :searchPattern)
        AND (CAST(:departmentId AS bigint) IS NULL OR e.department_id = :departmentId)
        AND (CAST(:countryCode AS text) IS NULL OR e.country_code = :countryCode)
        AND (CAST(:jobLevelId AS bigint) IS NULL OR e.job_level_id = :jobLevelId)
        AND (CAST(:status AS text) IS NULL OR e.employment_status = :status)
      """;

  @Transactional(readOnly = true)
  public PageResponse<EmployeeSummaryDto> search(EmployeeSearchCriteria criteria) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("search", criteria.search())
            .addValue(
                "searchPattern", criteria.search() == null ? null : "%" + criteria.search() + "%")
            .addValue("departmentId", criteria.departmentId())
            .addValue("countryCode", criteria.countryCode())
            .addValue("jobLevelId", criteria.jobLevelId())
            .addValue("status", criteria.status());

    Long total = jdbc.queryForObject("SELECT count(*) " + FROM_AND_WHERE, params, Long.class);
    long totalElements = total == null ? 0 : total;

    // Skip the row query entirely when the filters match nothing.
    if (totalElements == 0) {
      return PageResponse.of(List.of(), criteria.page(), criteria.size(), 0);
    }

    String orderBy = SORTS.get(criteria.sort());
    if (orderBy == null) {
      throw new IllegalArgumentException(
          "Unknown sort '%s'. Supported: %s".formatted(criteria.sort(), SORTS.keySet()));
    }
    String direction = criteria.descending() ? "DESC" : "ASC";

    String sql =
        """
        SELECT e.id, e.employee_code, e.full_name, e.email, e.job_title,
               d.name AS department, ct.name AS country, l.code AS level,
               e.employment_status,
               cr.amount_minor, cr.currency_code, cu.exponent,
               (cr.amount_minor / power(10, cu.exponent) * fx.rate_to_base) AS base_amount
        """
            + FROM_AND_WHERE
            // NULLS LAST keeps terminated employees, who have no current record, from
            // dominating the first page when sorting by pay.
            + " ORDER BY %s %s NULLS LAST, e.id ASC LIMIT :size OFFSET :offset"
                .formatted(orderBy, direction);

    params.addValue("size", criteria.size());
    params.addValue("offset", (long) criteria.page() * criteria.size());

    List<EmployeeSummaryDto> rows =
        jdbc.query(
            sql,
            params,
            (rs, i) -> {
              long amountMinor = rs.getLong("amount_minor");
              MoneyDto salary = null;
              if (!rs.wasNull()) {
                int exponent = rs.getInt("exponent");
                BigDecimal base = rs.getBigDecimal("base_amount");
                salary =
                    new MoneyDto(
                        amountMinor,
                        rs.getString("currency_code"),
                        BigDecimal.valueOf(amountMinor, exponent),
                        base == null ? null : base.setScale(2, java.math.RoundingMode.HALF_UP));
              }
              return new EmployeeSummaryDto(
                  rs.getLong("id"),
                  rs.getString("employee_code"),
                  rs.getString("full_name"),
                  rs.getString("email"),
                  rs.getString("job_title"),
                  rs.getString("department"),
                  rs.getString("country"),
                  rs.getString("level"),
                  rs.getString("employment_status"),
                  salary);
            });

    return PageResponse.of(rows, criteria.page(), criteria.size(), totalElements);
  }
}
