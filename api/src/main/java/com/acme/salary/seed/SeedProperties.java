package com.acme.salary.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param employees how many employees to generate
 * @param randomSeed fixed so a run is reproducible; change it for different demo data
 * @param reset delete existing employees first, rather than adding to them
 */
@ConfigurationProperties(prefix = "app.seed")
public record SeedProperties(int employees, long randomSeed, boolean reset) {

  public SeedProperties {
    if (employees <= 0) {
      throw new IllegalArgumentException("app.seed.employees must be positive");
    }
  }
}
