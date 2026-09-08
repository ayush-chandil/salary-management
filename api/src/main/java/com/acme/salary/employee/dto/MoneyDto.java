package com.acme.salary.employee.dto;

import java.math.BigDecimal;

/**
 * Money on the wire.
 *
 * <p>Carries the raw minor units as well as the formatted figure: clients that do arithmetic should
 * use {@code amountMinor}, and clients that display should use {@code amount}. Sending only a
 * decimal would invite float parsing on the other side.
 *
 * @param annualBase the same amount converted to the base currency, for cross-country comparison
 */
public record MoneyDto(
    long amountMinor, String currency, BigDecimal amount, BigDecimal annualBase) {}
