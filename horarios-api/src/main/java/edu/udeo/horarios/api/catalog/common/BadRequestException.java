package edu.udeo.horarios.api.catalog.common;

public class BadRequestException extends RuntimeException {
  public BadRequestException(String message) {
    super(message);
  }
}
