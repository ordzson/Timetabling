package edu.udeo.horarios.solver;

import java.util.List;
import java.util.Set;

public record RepairResult(
    RepairStatus status,
    Schedule schedule,
    Set<Long> pinnedSessionIds,
    RepairNeighborhood neighborhood,
    Set<Long> movedSessionIds,
    List<RepairViolation> remainingViolations,
    long repairTimeMs) {
  public RepairResult {
    pinnedSessionIds = Set.copyOf(pinnedSessionIds == null ? Set.of() : pinnedSessionIds);
    movedSessionIds = Set.copyOf(movedSessionIds == null ? Set.of() : movedSessionIds);
    remainingViolations = List.copyOf(remainingViolations == null ? List.of() : remainingViolations);
  }
}
