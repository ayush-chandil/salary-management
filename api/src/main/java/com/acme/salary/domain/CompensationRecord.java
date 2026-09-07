package com.acme.salary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One period of pay for one employee.
 *
 * <p>Records are effective-dated and never updated in place: a raise inserts a new row and closes
 * the previous one by setting {@code effectiveTo}. A record with a null {@code effectiveTo} is the
 * current one.
 *
 * <p>Two invariants are enforced by the database rather than here, because a read-then-write check
 * in the service layer would be racy:
 *
 * <ul>
 *   <li>{@code compensation_no_overlap} -- pay periods for one employee cannot overlap
 *   <li>{@code idx_compensation_one_current} -- at most one open record per employee
 * </ul>
 */
@Entity
@Table(name = "compensation_record")
@Getter
@Setter
@NoArgsConstructor
public class CompensationRecord {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "compensation_record_seq")
  @SequenceGenerator(
      name = "compensation_record_seq",
      sequenceName = "compensation_record_seq",
      allocationSize = 50)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "employee_id", nullable = false)
  private Employee employee;

  /**
   * Annual base salary as an integer count of minor units, in {@link #currency}. Never a
   * floating-point type. How many minor units make a major unit depends on the currency's exponent
   * -- JPY has none.
   */
  @Column(name = "amount_minor", nullable = false)
  private long amountMinor;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "currency_code", nullable = false)
  private Currency currency;

  @Column(name = "effective_from", nullable = false)
  private LocalDate effectiveFrom;

  /** Null means this is the current record. */
  @Column(name = "effective_to")
  private LocalDate effectiveTo;

  /** Required, so the history explains itself rather than just recording numbers. */
  @Enumerated(EnumType.STRING)
  @Column(name = "change_reason", nullable = false)
  private ChangeReason changeReason;

  @Column(name = "note")
  private String note;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public boolean isCurrent() {
    return effectiveTo == null;
  }
}
