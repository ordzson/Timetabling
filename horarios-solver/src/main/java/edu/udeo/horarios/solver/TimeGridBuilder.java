package edu.udeo.horarios.solver;

import edu.udeo.horarios.domain.TimeSlot;
import java.util.ArrayList;
import java.util.List;

public final class TimeGridBuilder {
  private static final int DAYS = 5;
  private static final int DAY_MINUTES = 24 * 60;
  private static final int DEFAULT_DAY_START = 7 * 60;
  private static final int DEFAULT_DAY_END = 22 * 60;
  private static final int DEFAULT_BLOCK_MINUTES = 45;

  public TimeGrid build(SchedulingProblem problem) {
    return build(problem, DEFAULT_BLOCK_MINUTES, DEFAULT_DAY_START, DEFAULT_DAY_END);
  }

  public TimeGrid build(
      SchedulingProblem problem, int blockMinutes, int dayStartMinute, int dayEndMinute) {
    if (blockMinutes <= 0 || dayStartMinute < 0 || dayEndMinute <= dayStartMinute) {
      throw new IllegalArgumentException("invalid grid bounds");
    }

    List<TimeSlot> starts = new ArrayList<>();
    for (int day = 0; day < DAYS; day++) {
      int base = day * DAY_MINUTES;
      for (int minute = dayStartMinute; minute + blockMinutes <= dayEndMinute; minute += blockMinutes) {
        starts.add(new TimeSlot(base + minute));
      }
    }
    return new TimeGrid(starts, problem.fixedBreaks());
  }
}
