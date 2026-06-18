package edu.udeo.horarios.domain;

import java.util.List;

public record SchedulableSession(
    long id, long courseId, List<Long> cohortIds, SessionType type, int durationMinutes, boolean pinned) {
  public SchedulableSession {
    if (id <= 0 || courseId <= 0) {
      throw new IllegalArgumentException("ids must be positive");
    }
    cohortIds = List.copyOf(cohortIds == null ? List.of() : cohortIds);
    if (cohortIds.isEmpty()) {
      throw new IllegalArgumentException("cohortIds must not be empty");
    }
    if (type == null) {
      throw new IllegalArgumentException("type must not be null");
    }
    if (durationMinutes <= 0) {
      throw new IllegalArgumentException("durationMinutes must be positive");
    }
  }
}
