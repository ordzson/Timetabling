package edu.udeo.horarios.solver;

import edu.udeo.horarios.domain.SchedulableSession;
import java.util.List;

public record CandidateSpace(
    SchedulableSession session,
    List<Candidate> candidates,
    int possibleTeacherCount,
    int compatibleRoomCount,
    int validStartCount,
    String emptyReason) {
  public CandidateSpace {
    if (session == null) {
      throw new IllegalArgumentException("session must not be null");
    }
    candidates = List.copyOf(candidates == null ? List.of() : candidates);
    emptyReason = emptyReason == null ? "" : emptyReason;
  }
}
