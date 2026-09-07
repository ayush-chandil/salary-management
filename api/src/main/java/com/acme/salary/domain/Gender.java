package com.acme.salary.domain;

/**
 * Self-reported. Nullable on {@link Employee}, and used only in size-suppressed aggregates -- never
 * surfaced on an individual record. See docs/requirements.md.
 */
public enum Gender {
  FEMALE,
  MALE,
  OTHER,
  UNDISCLOSED
}
