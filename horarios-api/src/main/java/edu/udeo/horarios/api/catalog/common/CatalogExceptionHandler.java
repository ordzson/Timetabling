package edu.udeo.horarios.api.catalog.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class CatalogExceptionHandler {
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
    List<FieldErrorDto> fields =
        ex.getBindingResult().getFieldErrors().stream().map(this::fieldError).toList();
    return ResponseEntity.badRequest().body(ErrorResponse.validation(fields, requestId(request)));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ErrorResponse> unreadable(HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(ErrorResponse.validation(List.of(new FieldErrorDto("body", "JSON invalido.")), requestId(request)));
  }

  @ExceptionHandler(RequestValidationException.class)
  ResponseEntity<ErrorResponse> validation(RequestValidationException ex, HttpServletRequest request) {
    return ResponseEntity.badRequest().body(ErrorResponse.validation(ex.fields(), requestId(request)));
  }

  @ExceptionHandler(BadRequestException.class)
  ResponseEntity<ErrorResponse> badRequest(BadRequestException ex, HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(ErrorResponse.validation(List.of(new FieldErrorDto("request", ex.getMessage())), requestId(request)));
  }

  @ExceptionHandler(NotFoundException.class)
  ResponseEntity<ErrorResponse> notFound(HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.notFound(requestId(request)));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ErrorResponse> duplicate(HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.duplicateCode(requestId(request)));
  }

  private FieldErrorDto fieldError(FieldError error) {
    return new FieldErrorDto(error.getField(), error.getDefaultMessage());
  }

  private String requestId(HttpServletRequest request) {
    Object existing = request.getAttribute("requestId");
    if (existing instanceof String id) {
      return id;
    }
    return request.getRequestId();
  }
}
