package com.acme.salary.employee.dto;

/** One row of the directory. Deliberately narrow -- the detail endpoint carries the rest. */
public record EmployeeSummaryDto(
    long id,
    String employeeCode,
    String fullName,
    String email,
    String jobTitle,
    String department,
    String country,
    String level,
    String employmentStatus,
    MoneyDto currentSalary) {}
