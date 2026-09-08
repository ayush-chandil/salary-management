package com.acme.salary.employee.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Editable employee attributes.
 *
 * <p>Salary is absent by design -- pay changes go through the compensation endpoint so that every
 * change carries an effective date and a reason. Allowing a salary edit here would let history be
 * overwritten silently, which is the exact failure this data model exists to prevent.
 */
public record UpdateEmployeeRequest(
    @NotBlank @Size(max = 100) String firstName,
    @NotBlank @Size(max = 100) String lastName,
    @NotBlank @Email @Size(max = 255) String email,
    @NotNull Long departmentId,
    @NotNull Long jobLevelId,
    @NotBlank @Size(max = 150) String jobTitle,
    @NotBlank String employmentStatus) {}
