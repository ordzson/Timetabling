package edu.udeo.horarios.solver;

public record PreValidationIssue(
    IssueSeverity severity, String code, String message, String entityType, long entityId) {
  public PreValidationIssue {
    if (severity == null) {
      throw new IllegalArgumentException("severity must not be null");
    }
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("code must not be blank");
    }
    if (message == null || message.isBlank()) {
      throw new IllegalArgumentException("message must not be blank");
    }
    if (entityType == null || entityType.isBlank()) {
      throw new IllegalArgumentException("entityType must not be blank");
    }
    if (entityId <= 0) {
      throw new IllegalArgumentException("entityId must be positive");
    }
  }
}
