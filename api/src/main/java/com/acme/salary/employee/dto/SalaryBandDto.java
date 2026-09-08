package com.acme.salary.employee.dto;

import java.math.BigDecimal;

/**
 * The expected range for this employee's level and country.
 *
 * @param compaRatio actual pay over the band midpoint; 1.0 is exactly at midpoint
 * @param position BELOW, WITHIN or ABOVE
 */
public record SalaryBandDto(
    long minMinor,
    long midMinor,
    long maxMinor,
    String currency,
    BigDecimal compaRatio,
    String position) {}
