package edu.udeo.horarios.solver;

import edu.udeo.horarios.domain.Assignment;
import java.util.Comparator;
import java.util.Set;

public final class NeighborhoodSelector {
  private static final int MAX_NEIGHBORHOOD = 15;

  public RepairNeighborhood select(Schedule schedule, Assignment edited, Set<Long> sessionGroupIds) {
    RepairNeighborhood.Builder builder = new RepairNeighborhood.Builder();
    builder.add(edited.sessionId(), NeighborhoodCause.DIRECT_CONFLICT);
    schedule.byTeacher(edited.teacherId()).stream()
        .filter(other -> other.sessionId() != edited.sessionId() && nearOrOverlaps(edited, other))
        .sorted(bySession())
        .limit(MAX_NEIGHBORHOOD)
        .forEach(other -> builder.add(other.sessionId(), cause(edited, other, NeighborhoodCause.TEACHER_NEAR)));
    schedule.byRoom(edited.roomId()).stream()
        .filter(other -> other.sessionId() != edited.sessionId() && nearOrOverlaps(edited, other))
        .sorted(bySession())
        .limit(MAX_NEIGHBORHOOD)
        .forEach(other -> builder.add(other.sessionId(), cause(edited, other, NeighborhoodCause.ROOM_NEAR)));
    edited.cohortIds().forEach(
        cohortId ->
            schedule.byCohort(cohortId).stream()
                .filter(other -> other.sessionId() != edited.sessionId() && nearOrOverlaps(edited, other))
                .sorted(bySession())
                .limit(MAX_NEIGHBORHOOD)
                .forEach(other -> builder.add(other.sessionId(), cause(edited, other, NeighborhoodCause.COHORT_NEAR))));
    sessionGroupIds.forEach(sessionId -> builder.add(sessionId, NeighborhoodCause.SESSION_GROUP));
    return builder.build();
  }

  private static NeighborhoodCause cause(
      Assignment edited, Assignment other, NeighborhoodCause nearCause) {
    return edited.time().overlaps(other.time()) ? NeighborhoodCause.DIRECT_CONFLICT : nearCause;
  }

  private static boolean nearOrOverlaps(Assignment left, Assignment right) {
    int gap =
        Math.min(
            Math.abs(left.time().startMinuteOfWeek() - right.time().endMinuteOfWeek()),
            Math.abs(right.time().startMinuteOfWeek() - left.time().endMinuteOfWeek()));
    return left.time().overlaps(right.time()) || gap <= 90;
  }

  private static Comparator<Assignment> bySession() {
    return Comparator.comparingLong(Assignment::sessionId);
  }
}
