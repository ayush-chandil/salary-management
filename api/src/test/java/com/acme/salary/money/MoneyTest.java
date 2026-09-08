package com.acme.salary.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests -- no Spring context, no database, milliseconds to run.
 *
 * <p>This is the arithmetic every salary figure in the system passes through, so it is tested at
 * the boundaries rather than the happy path alone.
 */
class MoneyTest {

  @Nested
  @DisplayName("major-unit conversion")
  class ToMajor {

    @Test
    void twoDecimalCurrency() {
      assertThat(new Money(7_000_000L, "USD", 2).toMajor())
          .isEqualByComparingTo(new BigDecimal("70000.00"));
    }

    @Test
    void zeroDecimalCurrencyIsNotDividedByAHundred() {
      // The bug this guards against: treating JPY as if it had cents makes every
      // yen figure a hundred times too small on the way out (or too large on the way in).
      assertThat(new Money(7_835_821L, "JPY", 0).toMajor())
          .isEqualByComparingTo(new BigDecimal("7835821"));
    }

    @Test
    void keepsTrailingPrecision() {
      assertThat(new Money(1L, "USD", 2).toMajor()).isEqualByComparingTo(new BigDecimal("0.01"));
    }
  }

  @Nested
  @DisplayName("construction from major units")
  class OfMajor {

    @Test
    void roundTripsThroughMinorUnits() {
      Money money = Money.ofMajor(new BigDecimal("70000.00"), "USD", 2);

      assertThat(money.amountMinor()).isEqualTo(7_000_000L);
      assertThat(money.toMajor()).isEqualByComparingTo(new BigDecimal("70000.00"));
    }

    @Test
    void acceptsFewerDecimalsThanTheCurrencyAllows() {
      assertThat(Money.ofMajor(new BigDecimal("70000"), "USD", 2).amountMinor())
          .isEqualTo(7_000_000L);
    }

    @Test
    void rejectsPrecisionTheCurrencyCannotRepresent() {
      // Silently rounding here would lose a real amount of money and hide a caller bug.
      assertThatThrownBy(() -> Money.ofMajor(new BigDecimal("100.5"), "JPY", 0))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("JPY cannot represent");
    }

    @Test
    void rejectsSubMinorUnitPrecision() {
      assertThatThrownBy(() -> Money.ofMajor(new BigDecimal("10.005"), "USD", 2))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("base-currency conversion")
  class ToBase {

    @Test
    void convertsYenToDollars() {
      BigDecimal usd = new Money(7_835_821L, "JPY", 0).toBase(new BigDecimal("0.00670000"));

      // 7,835,821 JPY x 0.0067 = 52,500.00 USD
      assertThat(usd).isEqualByComparingTo(new BigDecimal("52500.00"));
    }

    @Test
    void convertsEurosToDollars() {
      BigDecimal usd = new Money(5_314_800L, "EUR", 2).toBase(new BigDecimal("1.08000000"));

      assertThat(usd).isEqualByComparingTo(new BigDecimal("57399.84"));
    }

    @Test
    void baseCurrencyIsUnchangedAtRateOne() {
      assertThat(new Money(7_000_000L, "USD", 2).toBase(BigDecimal.ONE))
          .isEqualByComparingTo(new BigDecimal("70000.00"));
    }

    @Test
    void alwaysReturnsTwoDecimalPlaces() {
      assertThat(new Money(1_234_567L, "INR", 2).toBase(new BigDecimal("0.01200000")).scale())
          .isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("compa-ratio")
  class CompaRatio {

    @Test
    void midpointIsExactlyOne() {
      assertThat(Money.compaRatio(7_000_000L, 7_000_000L)).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void belowMidpointIsUnderOne() {
      assertThat(Money.compaRatio(6_300_000L, 7_000_000L))
          .isEqualByComparingTo(new BigDecimal("0.9000"));
    }

    @Test
    void aboveMidpointIsOverOne() {
      assertThat(Money.compaRatio(8_400_000L, 7_000_000L))
          .isEqualByComparingTo(new BigDecimal("1.2000"));
    }

    @Test
    void rejectsNonPositiveMidpoint() {
      assertThatThrownBy(() -> Money.compaRatio(7_000_000L, 0L))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("invariants")
  class Invariants {

    @Test
    void rejectsNegativeAmounts() {
      assertThatThrownBy(() -> new Money(-1L, "USD", 2))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMalformedCurrencyCode() {
      assertThatThrownBy(() -> new Money(100L, "DOLLAR", 2))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsImplausibleExponent() {
      assertThatThrownBy(() -> new Money(100L, "USD", 9))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
