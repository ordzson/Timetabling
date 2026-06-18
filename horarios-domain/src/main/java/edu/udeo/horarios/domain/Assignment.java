package edu.udeo.horarios.domain;

import java.util.List;

public record Assignment(
    long sessionId, long teacherId, long roomId, List<Long> cohortIds, TimeRange time) {
  public Assignment {
    if (sessionId <= 0 || teacherId <= 0 || roomId <= 0) {
      throw new IllegalArgumentException("ids must be positive");
    }
    cohortIds = List.copyOf(cohortIds == null ? List.of() : cohortIds);
    if (cohortIds.isEmpty()) {
      throw new IllegalArgumentException("cohortIds must not be empty");
    }
    if (time == null) {
      throw new IllegalArgumentException("time must not be null");
    }
  }
}
