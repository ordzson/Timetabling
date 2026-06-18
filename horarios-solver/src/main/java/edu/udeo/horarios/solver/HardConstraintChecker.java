package edu.udeo.horarios.solver;

import edu.udeo.horarios.domain.Assignment;
import edu.udeo.horarios.domain.Cohort;
import edu.udeo.horarios.domain.Course;
import edu.udeo.horarios.domain.Room;
import edu.udeo.horarios.domain.RoomType;
import edu.udeo.horarios.domain.SchedulableSession;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class HardConstraintChecker {
  private final TimeGrid grid;
  private final Map<Long, SchedulableSession> sessions;
  private final Map<Long, Course> courses;
  private final Map<Long, Cohort> cohorts;
  private final Map<Long, Room> rooms;

  public HardConstraintChecker(TimeGrid grid) {
    this(grid, List.of(), null);
  }

  public HardConstraintChecker(
      TimeGrid grid, List<SchedulableSession> sessions, SchedulingProblem problem) {
    this.grid = grid;
    this.sessions = sessions.stream().collect(Collectors.toMap(SchedulableSession::id, Function.identity()));
    this.courses =
        problem == null
            ? Map.of()
            : problem.curriculumCourses().stream()
                .map(CurriculumCourse::course)
                .collect(Collectors.toMap(Course::id, Function.identity(), (left, right) -> left));
    this.cohorts =
        problem == null
            ? Map.of()
            : problem.cohorts().stream().collect(Collectors.toMap(Cohort::id, Function.identity()));
    this.rooms =
        problem == null
            ? Map.of()
            : problem.rooms().stream().collect(Collectors.toMap(Room::id, Function.identity()));
  }

  public boolean canApply(Schedule schedule, Assignment assignment) {
    if (!grid.accepts(assignment.time())) {
      return false;
    }
    if (!staticRulesPass(assignment)) {
      return false;
    }
    if (schedule.byTeacher(assignment.teacherId()).stream()
        .anyMatch(existing -> existing.time().overlaps(assignment.time()))) {
      return false;
    }
    if (schedule.byRoom(assignment.roomId()).stream()
        .anyMatch(existing -> existing.time().overlaps(assignment.time()))) {
      return false;
    }
    return assignment.cohortIds().stream()
        .flatMap(cohortId -> schedule.byCohort(cohortId).stream())
        .noneMatch(existing -> existing.time().overlaps(assignment.time()));
  }

  private boolean staticRulesPass(Assignment assignment) {
    if (sessions.isEmpty()) {
      return true;
    }

    SchedulableSession session = sessions.get(assignment.sessionId());
    Room room = rooms.get(assignment.roomId());
    if (session == null || room == null) {
      return false;
    }

    Course course = courses.get(session.courseId());
    if (course == null || !compatible(room, course)) {
      return false;
    }

    int students =
        session.cohortIds().stream().map(cohorts::get).mapToInt(cohort -> cohort.expectedStudents()).sum();
    return room.capacity() >= students;
  }

  private static boolean compatible(Room room, Course course) {
    return !course.requiresLab() || room.type() == RoomType.LAB || room.type() == RoomType.MIXED;
  }
}
