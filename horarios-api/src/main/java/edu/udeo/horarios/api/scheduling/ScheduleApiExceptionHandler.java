package edu.udeo.horarios.api.scheduling;

import edu.udeo.horarios.api.catalog.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ScheduleApiExceptionHandler {
  @ExceptionHandler(ScheduleApiException.class)
  ResponseEntity<ErrorResponse> schedule(ScheduleApiException ex, HttpServletRequest request) {
    return ResponseEntity.status(ex.status())
        .body(new ErrorResponse(ex.code(), ex.getMessage(), Map.of(), requestId(request)));
  }

  private String requestId(HttpServletRequest request) {
    Object existing = request.getAttribute("requestId");
    return existing instanceof String id ? id : request.getRequestId();
  }
}
