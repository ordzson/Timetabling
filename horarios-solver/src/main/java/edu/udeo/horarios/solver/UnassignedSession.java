package edu.udeo.horarios.solver;

import edu.udeo.horarios.domain.SchedulableSession;

public record UnassignedSession(SchedulableSession session, String reason, int discardedCandidates) {
  public UnassignedSession {
    if (session == null) {
      throw new IllegalArgumentException("session must not be null");
    }
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("reason must not be blank");
    }
    if (discardedCandidates < 0) {
      throw new IllegalArgumentException("discardedCandidates must be non-negative");
    }
  }
}
