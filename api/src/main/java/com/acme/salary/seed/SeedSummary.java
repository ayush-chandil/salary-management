package com.acme.salary.seed;

import java.time.Duration;

/** What a seed run produced. Returned so tests can assert on it and the CLI can log it. */
public record SeedSummary(
    int employees, int compensationRecords, int belowBand, int aboveBand, Duration elapsed) {

  public String describe() {
    return "%,d employees and %,d compensation records in %,d ms (%,d below band, %,d above)"
        .formatted(employees, compensationRecords, elapsed.toMillis(), belowBand, aboveBand);
  }
}
