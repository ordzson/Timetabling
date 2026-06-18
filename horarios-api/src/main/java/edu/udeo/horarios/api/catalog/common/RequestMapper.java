package edu.udeo.horarios.api.catalog.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RequestMapper {
  private final ObjectMapper objectMapper;
  private final Validator validator;

  public RequestMapper(ObjectMapper objectMapper, Validator validator) {
    this.objectMapper = objectMapper;
    this.validator = validator;
  }

  public <T> T convert(Object request, Class<T> type) {
    T value;
    try {
      value = objectMapper.convertValue(request, type);
    } catch (IllegalArgumentException ex) {
      throw new RequestValidationException(List.of(new FieldErrorDto("body", "JSON invalido.")));
    }
    List<FieldErrorDto> fields =
        validator.validate(value).stream().map(this::fieldError).toList();
    if (!fields.isEmpty()) {
      throw new RequestValidationException(fields);
    }
    return value;
  }

  private FieldErrorDto fieldError(ConstraintViolation<?> violation) {
    return new FieldErrorDto(violation.getPropertyPath().toString(), violation.getMessage());
  }
}
