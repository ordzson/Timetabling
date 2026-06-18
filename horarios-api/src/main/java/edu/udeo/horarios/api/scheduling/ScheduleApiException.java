package edu.udeo.horarios.api.scheduling;

import org.springframework.http.HttpStatus;

class ScheduleApiException extends RuntimeException {
  private final String code;
  private final HttpStatus status;

  ScheduleApiException(String code, String message, HttpStatus status) {
    super(message);
    this.code = code;
    this.status = status;
  }

  String code() {
    return code;
  }

  HttpStatus status() {
    return status;
  }
}
