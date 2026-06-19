package edu.udeo.horarios.solver;

import edu.udeo.horarios.domain.Assignment;
import edu.udeo.horarios.domain.SchedulableSession;
import edu.udeo.horarios.domain.TimeRange;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class NeighborhoodRepairer {
  private final ManualEditApplier applier = new ManualEditApplier();
  private final NeighborhoodSelector selector = new NeighborhoodSelector();

  public RepairResult repair(
      Schedule base,
      SchedulingProblem problem,
      List<SchedulableSession> sessions,
      ManualEditCommand command,
      TimeRange targetTime,
      Set<Long> alreadyPinned,
      Set<Long> sessionGroupIds) {
    long start = System.nanoTime();
    Schedule working = base.copy();
    Assignment original = base.assignment(command.sessionId()).orElseThrow();
    Assignment edited = applier.apply(working, command, targetTime, sessionGroupIds);
    Set<Long> pinned = new LinkedHashSet<>(alreadyPinned == null ? Set.of() : alreadyPinned);
    pinned.add(command.sessionId());

    RepairNeighborhood neighborhood = selector.select(working, edited, sessionGroupIds);
    List<RepairViolation> initialViolations = violationsFor(working, edited);
    if (initialViolations.isEmpty()) {
      return result(RepairStatus.APPLIED_CLEAN, working, pinned, neighborhood, Set.of(), List.of(), start);
    }

    Set<Long> removed =
        neighborhood.sessionIds().stream()
            .filter(sessionId -> sessionId != command.sessionId())
            .filter(sessionId -> !pinned.contains(sessionId))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    Map<Long, Assignment> originals =
        removed.stream()
            .map(sessionId -> Map.entry(sessionId, working.assignment(sessionId).orElseThrow()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    removed.forEach(working::removeAssignment);

    TimeGrid grid = new TimeGridBuilder().build(problem);
    CandidateGenerator generator = new CandidateGenerator(grid);
    HardConstraintChecker checker = new HardConstraintChecker(grid, sessions, problem);
    Map<Long, SchedulableSession> bySession =
        sessions.stream().collect(Collectors.toMap(SchedulableSession::id, Function.identity()));
    for (Long sessionId : removed) {
      SchedulableSession session = bySession.get(sessionId);
      if (session == null) {
        continue;
      }
      generator.generate(problem, session).candidates().stream()
          .map(candidate -> candidate.toAssignment(session))
          .sorted(candidateComparator())
          .filter(candidate -> checker.canApply(working, candidate))
          .findFirst()
          .ifPresent(working::addAssignment);
    }

    Set<Long> moved =
        removed.stream()
            .filter(sessionId -> working.assignment(sessionId).isPresent())
            .filter(sessionId -> !working.assignment(sessionId).orElseThrow().equals(originals.get(sessionId)))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    List<RepairViolation> remaining = violationsFor(working, edited);
    RepairStatus status =
        remaining.isEmpty() ? RepairStatus.APPLIED_WITH_REPAIR : RepairStatus.APPLIED_WITH_REMAINING_CONFLICTS;
    return result(status, working, pinned, neighborhood, moved, remaining, start);
  }

  private RepairResult result(
      RepairStatus status,
      Schedule schedule,
      Set<Long> pinned,
      RepairNeighborhood neighborhood,
      Set<Long> moved,
      List<RepairViolation> remaining,
      long startNanos) {
    long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
    return new RepairResult(status, schedule, pinned, neighborhood, moved, remaining, elapsedMs);
  }

  private static List<RepairViolation> violationsFor(Schedule schedule, Assignment edited) {
    LinkedHashSet<RepairViolation> violations = new LinkedHashSet<>();
    schedule.byTeacher(edited.teacherId()).stream()
        .filter(other -> conflicts(edited, other))
        .forEach(other -> violations.add(new RepairViolation("TEACHER_OVERLAP", List.of(edited.sessionId(), other.sessionId()))));
    schedule.byRoom(edited.roomId()).stream()
        .filter(other -> conflicts(edited, other))
        .forEach(other -> violations.add(new RepairViolation("ROOM_OVERLAP", List.of(edited.sessionId(), other.sessionId()))));
    edited.cohortIds().forEach(
        cohortId ->
            schedule.byCohort(cohortId).stream()
                .filter(other -> conflicts(edited, other))
                .forEach(other -> violations.add(new RepairViolation("COHORT_OVERLAP", List.of(edited.sessionId(), other.sessionId())))));
    return List.copyOf(violations);
  }

  private static boolean conflicts(Assignment edited, Assignment other) {
    return edited.sessionId() != other.sessionId() && edited.time().overlaps(other.time());
  }

  private static Comparator<Assignment> candidateComparator() {
    return Comparator.comparingInt((Assignment assignment) -> assignment.time().startMinuteOfWeek())
        .thenComparingLong(Assignment::teacherId)
        .thenComparingLong(Assignment::roomId);
  }
}
