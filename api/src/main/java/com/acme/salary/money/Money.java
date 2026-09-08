package com.acme.salary.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * An amount of money, held as an integer count of minor units.
 *
 * <p>The whole point of this type is that {@code amountMinor} alone is meaningless: 7,000,000 is
 * $70,000.00 in USD and ¥7,000,000 in JPY. The exponent travels with the amount so the two can
 * never be confused, and conversion to a human-readable figure happens in exactly one place.
 *
 * @param amountMinor count of the currency's smallest unit
 * @param currency ISO-4217 code
 * @param exponent decimal places for that currency: 2 for USD, 0 for JPY
 */
public record Money(long amountMinor, String currency, int exponent) {

  public Money {
    if (amountMinor < 0) {
      throw new IllegalArgumentException("amountMinor must not be negative: " + amountMinor);
    }
    if (currency == null || currency.length() != 3) {
      throw new IllegalArgumentException("currency must be a 3-letter ISO-4217 code: " + currency);
    }
    if (exponent < 0 || exponent > 4) {
      throw new IllegalArgumentException("exponent out of range: " + exponent);
    }
  }

  /** The amount as people write it: 7_000_000 minor USD becomes {@code 70000.00}. */
  public BigDecimal toMajor() {
    return BigDecimal.valueOf(amountMinor, exponent);
  }

  /**
   * Builds from a major-unit figure, rejecting anything with more precision than the currency can
   * represent. {@code 100.5} JPY is not a rounding problem to be silently absorbed -- it is a bug
   * in the caller.
   */
  public static Money ofMajor(BigDecimal major, String currency, int exponent) {
    BigDecimal scaled;
    try {
      scaled = major.setScale(exponent, RoundingMode.UNNECESSARY);
    } catch (ArithmeticException e) {
      throw new IllegalArgumentException(
          "%s cannot represent %s -- it has %d decimal place(s)"
              .formatted(currency, major, exponent));
    }
    return new Money(scaled.unscaledValue().longValueExact(), currency, exponent);
  }

  /**
   * Converts to the base currency for comparison.
   *
   * <p>Deliberately returns major units rather than another {@code Money}: the result is a
   * comparison figure, and rounding it back into base-currency minor units would imply a precision
   * the exchange rate does not have.
   *
   * @param rateToBase value of one major unit of this currency in base-currency major units
   */
  public BigDecimal toBase(BigDecimal rateToBase) {
    return toMajor().multiply(rateToBase).setScale(2, RoundingMode.HALF_UP);
  }

  /** Position within a salary band, where 1.0 is exactly the midpoint. */
  public static BigDecimal compaRatio(long amountMinor, long bandMidMinor) {
    if (bandMidMinor <= 0) {
      throw new IllegalArgumentException("band midpoint must be positive: " + bandMidMinor);
    }
    return BigDecimal.valueOf(amountMinor)
        .divide(BigDecimal.valueOf(bandMidMinor), 4, RoundingMode.HALF_UP);
  }
}
