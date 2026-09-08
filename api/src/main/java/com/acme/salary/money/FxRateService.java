package com.acme.salary.money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the current exchange-rate snapshot.
 *
 * <p>"Current" means the most recent {@code as_of} in the table, not today's market. Rates are
 * dated history, so a report run twice against unchanged data returns identical numbers.
 */
@Service
@RequiredArgsConstructor
public class FxRateService {

  private final JdbcTemplate jdbc;

  /** All rates from the latest snapshot, keyed by currency. Seven rows -- cheap to load whole. */
  @Transactional(readOnly = true)
  public Snapshot latestSnapshot() {
    LocalDate asOf = jdbc.queryForObject("SELECT max(as_of) FROM fx_rate", LocalDate.class);
    if (asOf == null) {
      throw new IllegalStateException("No FX rates loaded -- migrations must run first");
    }

    Map<String, BigDecimal> rates = new HashMap<>();
    jdbc.query(
        "SELECT currency_code, rate_to_base FROM fx_rate WHERE as_of = ?",
        rs -> {
          rates.put(rs.getString(1), rs.getBigDecimal(2));
        },
        asOf);

    return new Snapshot(asOf, Map.copyOf(rates));
  }

  /**
   * @param asOf which snapshot these rates came from -- reported alongside any figure derived from
   *     them, so a number can always be traced back to its rate
   */
  public record Snapshot(LocalDate asOf, Map<String, BigDecimal> rates) {

    public BigDecimal rateFor(String currency) {
      BigDecimal rate = rates.get(currency);
      if (rate == null) {
        throw new IllegalStateException("No exchange rate for " + currency + " as of " + asOf);
      }
      return rate;
    }
  }
}
