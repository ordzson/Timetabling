package edu.udeo.horarios.solver;

import java.util.List;

public record RepairViolation(String code, List<Long> sessionIds) {
  public RepairViolation {
    sessionIds = List.copyOf(sessionIds == null ? List.of() : sessionIds);
  }
}
