package edu.udeo.horarios.solver;

import edu.udeo.horarios.domain.Assignment;
import edu.udeo.horarios.domain.SchedulableSession;
import edu.udeo.horarios.domain.Teacher;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class IncrementalSoftScorer {
  private final Map<Long, SchedulableSession> sessions;
  private final Map<Long, Teacher> teachers;
  private final RoomDistanceCalculator distanceCalculator;

  public IncrementalSoftScorer(List<CandidateSpace> spaces, SchedulingProblem problem) {
    this(
        spaces.stream().map(CandidateSpace::session).toList(),
        problem.teachers(),
        new RoomDistanceCalculator(problem.rooms()));
  }

  public IncrementalSoftScorer(
      List<SchedulableSession> sessions, List<Teacher> teachers, RoomDistanceCalculator distanceCalculator) {
    this.sessions = sessions.stream().collect(Collectors.toMap(SchedulableSession::id, Function.identity()));
    this.teachers = teachers.stream().collect(Collectors.toMap(Teacher::id, Function.identity()));
    this.distanceCalculator = distanceCalculator;
  }

  public Score score(Schedule schedule) {
    return new Score(
        0,
        hardViolations(schedule),
        0,
        nonContiguousCourseBlocks(schedule),
        gapsNotAtEnd(schedule),
        distanceCalculator.teacherWalkingDistance(schedule),
        teacherLoadDeviation(schedule));
  }

  public long delta(Schedule schedule, MoveProposal proposal) {
    Score before = score(schedule);
    Schedule after = schedule.copy();
    if (proposal.current() == null) {
      after.addAssignment(proposal.replacement());
    } else {
      after.moveAssignment(proposal.current().sessionId(), proposal.replacement());
    }
    return score(after).totalCost() - before.totalCost();
  }

  private long hardViolations(Schedule schedule) {
    List<Assignment> assignments = schedule.assignments();
    long violations = 0;
    for (int i = 0; i < assignments.size(); i++) {
      for (int j = i + 1; j < assignments.size(); j++) {
        Assignment left = assignments.get(i);
        Assignment right = assignments.get(j);
        if (!left.time().overlaps(right.time())) {
          continue;
        }
        if (left.teacherId() == right.teacherId() || left.roomId() == right.roomId() || sharesCohort(left, right)) {
          violations++;
        }
      }
    }
    return violations;
  }

  private long nonContiguousCourseBlocks(Schedule schedule) {
    return schedule.assignments().stream()
        .collect(
            Collectors.groupingBy(
                assignment -> courseId(assignment.sessionId()) + ":" + assignment.cohortIds()))
        .values()
        .stream()
        .mapToLong(this::gapsBetween)
        .sum();
  }

  private long gapsNotAtEnd(Schedule schedule) {
    return schedule.assignments().stream()
        .flatMap(assignment -> assignment.cohortIds().stream())
        .distinct()
        .mapToLong(cohortId -> gapsBetween(schedule.byCohort(cohortId)))
        .sum();
  }

  private long teacherLoadDeviation(Schedule schedule) {
    return teachers.values().stream()
        .mapToLong(
            teacher -> {
              int load = schedule.byTeacher(teacher.id()).size();
              if (load < teacher.minCourses()) {
                return teacher.minCourses() - load;
              }
              if (load > teacher.maxCourses()) {
                return load - teacher.maxCourses();
              }
              return 0;
            })
        .sum();
  }

  private long gapsBetween(List<Assignment> assignments) {
    List<Assignment> sorted =
        assignments.stream()
            .sorted(Comparator.comparingInt(assignment -> assignment.time().startMinuteOfWeek()))
            .toList();
    long gaps = 0;
    for (int i = 1; i < sorted.size(); i++) {
      Assignment left = sorted.get(i - 1);
      Assignment right = sorted.get(i);
      if (sameDay(left, right) && left.time().endMinuteOfWeek() < right.time().startMinuteOfWeek()) {
        gaps++;
      }
    }
    return gaps;
  }

  private long courseId(long sessionId) {
    SchedulableSession session = sessions.get(sessionId);
    return session == null ? sessionId : session.courseId();
  }

  private static boolean sharesCohort(Assignment left, Assignment right) {
    Set<Long> cohorts = new HashSet<>(left.cohortIds());
    return right.cohortIds().stream().anyMatch(cohorts::contains);
  }

  private static boolean sameDay(Assignment left, Assignment right) {
    return left.time().startMinuteOfWeek() / 1_440 == right.time().startMinuteOfWeek() / 1_440;
  }
}
