package com.acme.salary.domain;

/** Why a compensation record was created. Required, so history explains itself. */
public enum ChangeReason {
  INITIAL,
  ANNUAL_REVIEW,
  PROMOTION,
  MARKET_ADJUSTMENT,
  ROLE_CHANGE,
  CORRECTION
}
