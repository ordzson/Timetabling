package edu.udeo.horarios.api.catalog.common;

import java.util.List;
import java.util.Map;

public record ErrorResponse(String code, String message, Map<String, List<FieldErrorDto>> details, String requestId) {
  public static ErrorResponse validation(List<FieldErrorDto> fields, String requestId) {
    return new ErrorResponse(
        "VALIDATION_FAILED", "La solicitud tiene campos invalidos.", Map.of("fields", fields), requestId);
  }

  public static ErrorResponse duplicateCode(String requestId) {
    return new ErrorResponse("DUPLICATE_CODE", "El codigo ya existe.", Map.of(), requestId);
  }

  public static ErrorResponse notFound(String requestId) {
    return new ErrorResponse("RESOURCE_NOT_FOUND", "El recurso no existe.", Map.of(), requestId);
  }
}
