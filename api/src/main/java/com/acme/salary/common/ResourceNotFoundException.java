package com.acme.salary.common;

/** Maps to 404. */
public class ResourceNotFoundException extends RuntimeException {

  public ResourceNotFoundException(String what, Object id) {
    super("%s %s not found".formatted(what, id));
  }
}
