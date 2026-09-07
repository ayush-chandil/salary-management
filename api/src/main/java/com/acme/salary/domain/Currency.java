package com.acme.salary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** ISO-4217 currency. Not java.util.Currency -- we need the exponent persisted. */
@Entity
@Table(name = "currency")
@Getter
@Setter
@NoArgsConstructor
public class Currency {

  @Id
  @Column(name = "code", length = 3)
  private String code;

  @Column(name = "name", nullable = false)
  private String name;

  /**
   * Decimal places: 2 for USD, 0 for JPY. Every conversion between stored minor units and displayed
   * major units depends on this.
   */
  @Column(name = "exponent", nullable = false)
  private short exponent;
}
