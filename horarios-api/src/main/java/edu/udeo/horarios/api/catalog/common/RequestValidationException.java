package edu.udeo.horarios.api.catalog.common;

import java.util.List;

public class RequestValidationException extends RuntimeException {
  private final List<FieldErrorDto> fields;

  public RequestValidationException(List<FieldErrorDto> fields) {
    this.fields = fields;
  }

  public List<FieldErrorDto> fields() {
    return fields;
  }
}
