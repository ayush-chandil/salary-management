package com.acme.salary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A dated exchange-rate snapshot, not a live feed.
 *
 * <p>Keyed by (currency, as_of) so rates are immutable history. Reports use the most recent
 * snapshot and state which one they used; historical amounts are never restated. A live feed would
 * make tests non-deterministic and mean the same report returned different numbers on different
 * days.
 */
@Entity
@Table(name = "fx_rate")
@Getter
@Setter
@NoArgsConstructor
public class FxRate {

  @EmbeddedId private FxRateId id;

  /** Value of one major unit of this currency in base-currency major units. */
  @Column(name = "rate_to_base", nullable = false, precision = 18, scale = 8)
  private BigDecimal rateToBase;

  @Embeddable
  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  public static class FxRateId implements Serializable {

    @Column(name = "currency_code", length = 3, nullable = false)
    private String currencyCode;

    @Column(name = "as_of", nullable = false)
    private LocalDate asOf;

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof FxRateId that)) {
        return false;
      }
      return Objects.equals(currencyCode, that.currencyCode) && Objects.equals(asOf, that.asOf);
    }

    @Override
    public int hashCode() {
      return Objects.hash(currencyCode, asOf);
    }
  }
}
