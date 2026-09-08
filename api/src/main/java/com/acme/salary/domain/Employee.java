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
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.generator.EventType;

/**
 * An employee. Deliberately has no {@code manager_id}: none of the questions in
 * docs/requirements.md need an org hierarchy, and an org chart is a different feature.
 *
 * <p>Current pay lives in {@link CompensationRecord}, not here. There is no {@code salary} column
 * by design -- see docs/architecture.md.
 */
@Entity
@Table(name = "employee")
@Getter
@Setter
@NoArgsConstructor
public class Employee {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "employee_seq")
  @SequenceGenerator(name = "employee_seq", sequenceName = "employee_seq", allocationSize = 50)
  private Long id;

  @Column(name = "employee_code", nullable = false, unique = true)
  private String employeeCode;

  @Column(name = "first_name", nullable = false)
  private String firstName;

  @Column(name = "last_name", nullable = false)
  private String lastName;

  /**
   * Database-generated column, backing the trigram search index.
   *
   * <p>{@code @Generated} makes Hibernate read the value back after a write, at the cost of one
   * extra select per row. That cost is acceptable because the only JPA writes here are single
   * employees -- the 10,000-row seed goes through JDBC batches and never touches this mapping.
   *
   * <p>Without it, renaming an employee and returning the result in the same transaction serves the
   * stale name from the persistence context, because nothing forces a re-read.
   */
  @Generated(event = {EventType.INSERT, EventType.UPDATE})
  @Column(name = "full_name", insertable = false, updatable = false)
  private String fullName;

  @Column(name = "email", nullable = false, unique = true)
  private String email;

  /** Nullable. Used only in size-suppressed aggregates, never shown per-employee. */
  @Enumerated(EnumType.STRING)
  @Column(name = "gender")
  private Gender gender;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "country_code", nullable = false)
  private Country country;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "department_id", nullable = false)
  private Department department;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "job_level_id", nullable = false)
  private JobLevel jobLevel;

  @Column(name = "job_title", nullable = false)
  private String jobTitle;

  @Column(name = "hire_date", nullable = false)
  private LocalDate hireDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "employment_status", nullable = false)
  private EmploymentStatus employmentStatus = EmploymentStatus.ACTIVE;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
