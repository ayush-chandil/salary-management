package com.acme.salary.common;

import java.net.URI;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Single place where exceptions become HTTP responses, in RFC 9457 problem-detail form.
 *
 * <p>Handling errors per-controller drifts: one endpoint returns a string, another a map, a third
 * leaks a stack trace. A client can rely on one shape here.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final URI NOT_FOUND = URI.create("urn:acme:salary:not-found");
  private static final URI VALIDATION = URI.create("urn:acme:salary:validation-failed");
  private static final URI CONFLICT = URI.create("urn:acme:salary:conflict");

  @ExceptionHandler(ResourceNotFoundException.class)
  ProblemDetail onNotFound(ResourceNotFoundException e) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    problem.setTitle("Resource not found");
    problem.setType(NOT_FOUND);
    return problem;
  }

  @ExceptionHandler(BusinessRuleException.class)
  ProblemDetail onBusinessRule(BusinessRuleException e) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    problem.setTitle("Conflicts with current data");
    problem.setType(CONFLICT);
    return problem;
  }

  /** Bean Validation failures, reported field by field so a form can highlight them. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail onValidation(MethodArgumentNotValidException e) {
    Map<String, String> errors = new TreeMap<>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(field -> errors.put(field.getField(), field.getDefaultMessage()));

    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
    problem.setTitle("Validation failed");
    problem.setType(VALIDATION);
    problem.setProperty("errors", errors);
    return problem;
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail onIllegalArgument(IllegalArgumentException e) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    problem.setTitle("Invalid request");
    problem.setType(VALIDATION);
    return problem;
  }

  /**
   * The database enforces the compensation invariants, so a race that slips past the service-layer
   * check still surfaces here as a 409 rather than a 500.
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  ProblemDetail onDataIntegrity(DataIntegrityViolationException e) {
    String detail = describeConstraint(e);
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
    problem.setTitle("Conflicts with current data");
    problem.setType(CONFLICT);
    return problem;
  }

  private String describeConstraint(DataIntegrityViolationException e) {
    String message = e.getMostSpecificCause().getMessage();
    if (message == null) {
      return "The request conflicts with existing data";
    }
    if (message.contains("compensation_no_overlap")) {
      return "That period overlaps an existing compensation record for this employee";
    }
    if (message.contains("idx_compensation_one_current")) {
      return "This employee already has a current compensation record";
    }
    if (message.contains("employee_email_key")) {
      return "That email address is already in use";
    }
    if (message.contains("employee_employee_code_key")) {
      return "That employee code is already in use";
    }
    return "The request conflicts with existing data";
  }
}
