package com.acme.salary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "job_level")
@Getter
@Setter
@NoArgsConstructor
public class JobLevel {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "job_level_seq")
  @SequenceGenerator(name = "job_level_seq", sequenceName = "job_level_seq", allocationSize = 50)
  private Long id;

  @Column(name = "code", nullable = false, unique = true)
  private String code;

  @Column(name = "name", nullable = false)
  private String name;

  /** Seniority order. Sorts levels and derives bands. */
  @Column(name = "rank", nullable = false, unique = true)
  private int rank;
}
