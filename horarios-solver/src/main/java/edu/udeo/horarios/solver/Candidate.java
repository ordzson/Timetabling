package edu.udeo.horarios.solver;

import edu.udeo.horarios.domain.Assignment;
import edu.udeo.horarios.domain.SchedulableSession;
import edu.udeo.horarios.domain.TimeRange;
import java.util.List;

public record Candidate(long teacherId, long roomId, TimeRange time) {
  public Candidate {
    if (teacherId <= 0 || roomId <= 0) {
      throw new IllegalArgumentException("ids must be positive");
    }
    if (time == null) {
      throw new IllegalArgumentException("time must not be null");
    }
  }

  Assignment toAssignment(SchedulableSession session) {
    return new Assignment(session.id(), teacherId, roomId, List.copyOf(session.cohortIds()), time);
  }
}
