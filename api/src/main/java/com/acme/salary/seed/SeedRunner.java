package com.acme.salary.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Runs the seed and stops, under the {@code seed} profile only.
 *
 * <pre>
 * ./mvnw spring-boot:run -Dspring-boot.run.profiles=seed
 * </pre>
 *
 * <p>Gated on a profile rather than running at every startup, so booting the API never silently
 * mutates data.
 */
@Component
@Profile("seed")
@RequiredArgsConstructor
@Slf4j
public class SeedRunner implements ApplicationRunner {

  private final DataSeeder seeder;
  private final SeedProperties properties;

  @Override
  public void run(ApplicationArguments args) {
    log.info(
        "Seeding {} employees (randomSeed={}, reset={})",
        properties.employees(),
        properties.randomSeed(),
        properties.reset());
    SeedSummary summary =
        seeder.seed(properties.employees(), properties.randomSeed(), properties.reset());
    log.info("Seed complete: {}", summary.describe());
  }
}
