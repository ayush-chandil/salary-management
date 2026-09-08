package com.acme.salary.common;

import java.util.List;

/**
 * A page of results.
 *
 * <p>Every collection endpoint returns this rather than a bare array -- an unbounded list over
 * 10,000 employees is the failure this dataset size exists to test for.
 */
public record PageResponse<T>(
    List<T> content, int page, int size, long totalElements, int totalPages) {

  public static <T> PageResponse<T> of(List<T> content, int page, int size, long total) {
    int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
    return new PageResponse<>(content, page, size, total, totalPages);
  }
}
