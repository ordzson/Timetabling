package edu.udeo.horarios.solver;

import edu.udeo.horarios.domain.TimeRange;
import edu.udeo.horarios.domain.TimeSlot;
import java.util.List;

public record TimeGrid(List<TimeSlot> starts, List<FixedBreak> fixedBreaks) {
  public TimeGrid {
    starts = List.copyOf(starts == null ? List.of() : starts);
    fixedBreaks = List.copyOf(fixedBreaks == null ? List.of() : fixedBreaks);
  }

  public boolean accepts(TimeRange time) {
    return fixedBreaks.stream().noneMatch(fixedBreak -> fixedBreak.time().overlaps(time));
  }
}
