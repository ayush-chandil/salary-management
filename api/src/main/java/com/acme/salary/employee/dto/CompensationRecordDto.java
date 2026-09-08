package com.acme.salary.employee.dto;

import java.time.LocalDate;

/**
 * One period of pay.
 *
 * @param effectiveTo null means this is the current record
 * @param changePercent movement from the previous record, null for the first
 */
public record CompensationRecordDto(
    long id,
    MoneyDto amount,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String changeReason,
    String note,
    java.math.BigDecimal changePercent) {}
