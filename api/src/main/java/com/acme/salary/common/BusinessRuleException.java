package com.acme.salary.common;

/**
 * A request that is well-formed but conflicts with the state of the data -- maps to 409.
 *
 * <p>Distinct from a validation failure (400): "effectiveFrom must not be null" is the caller
 * sending nonsense, whereas "this employee already has a salary record from that date" depends on
 * what is currently stored.
 */
public class BusinessRuleException extends RuntimeException {

  public BusinessRuleException(String message) {
    super(message);
  }
}
