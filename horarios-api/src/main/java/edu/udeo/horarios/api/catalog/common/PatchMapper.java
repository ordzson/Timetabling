package edu.udeo.horarios.api.catalog.common;

import java.time.LocalTime;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PatchMapper {
  @SuppressWarnings("unchecked")
  public Map<String, Object> map(Object request, Set<String> allowedFields) {
    if (!(request instanceof Map<?, ?> raw)) {
      throw invalid("body");
    }
    for (Object key : raw.keySet()) {
      if (!(key instanceof String field) || !allowedFields.contains(field)) {
        throw invalid(String.valueOf(key));
      }
    }
    return (Map<String, Object>) raw;
  }

  public String string(Map<String, Object> map, String field) {
    Object value = map.get(field);
    if (!(value instanceof String text) || text.isBlank()) {
      throw invalid(field);
    }
    return text;
  }

  public int integer(Map<String, Object> map, String field) {
    Object value = map.get(field);
    if (!(value instanceof Number number)) {
      throw invalid(field);
    }
    return number.intValue();
  }

  public boolean bool(Map<String, Object> map, String field) {
    Object value = map.get(field);
    if (!(value instanceof Boolean bool)) {
      throw invalid(field);
    }
    return bool;
  }

  public LocalTime time(Map<String, Object> map, String field) {
    try {
      return LocalTime.parse(string(map, field));
    } catch (RuntimeException ex) {
      throw invalid(field);
    }
  }

  public <E extends Enum<E>> E enumValue(Map<String, Object> map, String field, Class<E> type) {
    try {
      return Enum.valueOf(type, string(map, field));
    } catch (RuntimeException ex) {
      throw invalid(field);
    }
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> object(Map<String, Object> map, String field) {
    Object value = map.get(field);
    if (!(value instanceof Map<?, ?> object)) {
      throw invalid(field);
    }
    return (Map<String, Object>) object;
  }

  public RequestValidationException invalid(String field) {
    return new RequestValidationException(
        java.util.List.of(new FieldErrorDto(field, "Campo invalido.")));
  }
}
