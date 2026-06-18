package edu.udeo.horarios.solver;

import edu.udeo.horarios.domain.TimeRange;

public record FixedBreak(TimeRange time) {
  public FixedBreak {
    if (time == null) {
      throw new IllegalArgumentException("time must not be null");
    }
  }
}
