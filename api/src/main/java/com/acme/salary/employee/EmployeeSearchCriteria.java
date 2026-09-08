package com.acme.salary.employee;

/**
 * Directory query parameters, normalised.
 *
 * <p>Page size is capped rather than trusted. Without a ceiling, {@code ?size=1000000} is an
 * unbounded response with extra steps -- exactly what "no endpoint returns an unbounded collection"
 * is meant to prevent.
 */
public record EmployeeSearchCriteria(
    String search,
    Long departmentId,
    String countryCode,
    Long jobLevelId,
    String status,
    String sort,
    boolean descending,
    int page,
    int size) {

  public static final int MAX_SIZE = 100;
  public static final int DEFAULT_SIZE = 25;

  public EmployeeSearchCriteria {
    search = blankToNull(search);
    countryCode = blankToNull(countryCode);
    status = blankToNull(status);
    sort = blankToNull(sort) == null ? "name" : sort;
    page = Math.max(0, page);
    size = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
