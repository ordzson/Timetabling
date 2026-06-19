package edu.udeo.horarios.solver;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record RepairNeighborhood(Map<Long, NeighborhoodCause> causes) {
  public RepairNeighborhood {
    causes = Map.copyOf(causes == null ? Map.of() : causes);
  }

  public Set<Long> sessionIds() {
    return causes.keySet();
  }

  static RepairNeighborhood empty(long sessionId) {
    return new RepairNeighborhood(Map.of(sessionId, NeighborhoodCause.DIRECT_CONFLICT));
  }

  static final class Builder {
    private final Map<Long, NeighborhoodCause> causes = new LinkedHashMap<>();

    void add(long sessionId, NeighborhoodCause cause) {
      causes.putIfAbsent(sessionId, cause);
    }

    RepairNeighborhood build() {
      return new RepairNeighborhood(causes);
    }
  }
}
