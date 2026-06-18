package edu.udeo.horarios.solver;

import java.util.List;

public record ScheduleResult(Schedule schedule, List<UnassignedSession> unassignedSessions) {
  public ScheduleResult {
    if (schedule == null) {
      throw new IllegalArgumentException("schedule must not be null");
    }
    unassignedSessions =
        List.copyOf(unassignedSessions == null ? List.of() : unassignedSessions);
  }
}
