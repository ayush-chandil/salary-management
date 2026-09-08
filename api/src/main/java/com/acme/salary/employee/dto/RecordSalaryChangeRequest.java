package com.acme.salary.employee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A pay change.
 *
 * <p>{@code effectiveFrom} and {@code changeReason} are both required. The reason is what turns a
 * list of numbers into a history someone can audit a year later.
 *
 * @param amount in major units of the employee's currency, e.g. 78000.00
 */
public record RecordSalaryChangeRequest(
    @NotNull @Positive BigDecimal amount,
    @NotNull LocalDate effectiveFrom,
    @NotBlank String changeReason,
    @Size(max = 500) String note) {}
