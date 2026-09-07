package com.acme.salary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The expected pay range for a level in a country, in that country's currency.
 *
 * <p>This is what turns "what do we pay?" into "what should we pay?" -- compa-ratio and band
 * breaches are both derived from it.
 */
@Entity
@Table(name = "salary_band")
@Getter
@Setter
@NoArgsConstructor
public class SalaryBand {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "salary_band_seq")
  @SequenceGenerator(
      name = "salary_band_seq",
      sequenceName = "salary_band_seq",
      allocationSize = 50)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "job_level_id", nullable = false)
  private JobLevel jobLevel;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "country_code", nullable = false)
  private Country country;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "currency_code", nullable = false)
  private Currency currency;

  @Column(name = "min_minor", nullable = false)
  private long minMinor;

  @Column(name = "mid_minor", nullable = false)
  private long midMinor;

  @Column(name = "max_minor", nullable = false)
  private long maxMinor;
}
