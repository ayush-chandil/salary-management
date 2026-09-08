package com.acme.salary.employee.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * A single employee with their full pay history.
 *
 * <p>Note the absence of {@code gender}: it is collected for aggregate pay-equity analysis and is
 * deliberately never surfaced on an individual record. See docs/requirements.md.
 */
public record EmployeeDetailDto(
    long id,
    String employeeCode,
    String firstName,
    String lastName,
    String fullName,
    String email,
    String jobTitle,
    String department,
    String country,
    String level,
    LocalDate hireDate,
    String employmentStatus,
    MoneyDto currentSalary,
    SalaryBandDto band,
    List<CompensationRecordDto> history) {}
