package com.acme.salary.employee.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A new employee, including their starting pay.
 *
 * <p>Starting salary is required rather than optional: an employee with no compensation record
 * would be invisible to every insight and would break the "exactly one current record" invariant
 * the rest of the system relies on.
 *
 * @param startingSalary in major units of the country's currency, e.g. 70000.00
 */
public record CreateEmployeeRequest(
    @NotBlank @Size(max = 100) String firstName,
    @NotBlank @Size(max = 100) String lastName,
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(min = 2, max = 2) String countryCode,
    @NotNull Long departmentId,
    @NotNull Long jobLevelId,
    @NotBlank @Size(max = 150) String jobTitle,
    @NotNull @PastOrPresent LocalDate hireDate,
    String gender,
    @NotNull @Positive BigDecimal startingSalary) {}
