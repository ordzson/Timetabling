package edu.udeo.horarios.api.catalog.common;

public class NotFoundException extends RuntimeException {
  public NotFoundException(String message) {
    super(message);
  }
}
