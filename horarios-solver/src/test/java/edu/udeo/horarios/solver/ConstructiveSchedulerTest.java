package edu.udeo.horarios.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.udeo.horarios.domain.Assignment;
import edu.udeo.horarios.domain.Cohort;
import edu.udeo.horarios.domain.Course;
import edu.udeo.horarios.domain.Room;
import edu.udeo.horarios.domain.RoomCoordinate;
import edu.udeo.horarios.domain.RoomType;
import edu.udeo.horarios.domain.Teacher;
import edu.udeo.horarios.domain.TimeRange;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConstructiveSchedulerTest {
  @Test
  void teacherRoomAndCohortDoNotOverlap() {
    Course first = course(10, 45, false);
    Course second = course(11, 45, false);
    SchedulingProblem problem =
        new SchedulingProblem(
            List.of(cohort(1, 100)),
            List.of(curriculum(first), curriculum(second)),
            List.of(teacher(1, first.id(), second.id())),
            List.of(room(1, 40, RoomType.THEORY)));

    ScheduleResult result = new ConstructiveScheduler().schedule(problem);

    assertTrue(result.unassignedSessions().isEmpty());
    assertNoOverlaps(result.schedule().byTeacher(1));
    assertNoOverlaps(result.schedule().byRoom(1));
    assertNoOverlaps(result.schedule().byCohort(1));
  }

  @Test
  void teacherDoesNotOverlapAcrossCareers() {
    Course first = course(10, 45, false);
    Course second = course(11, 45, false);
    SchedulingProblem problem =
        new SchedulingProblem(
            List.of(cohort(1, 100), cohort(2, 200)),
            List.of(curriculum(first), curriculum(second)),
            List.of(teacher(1, first.id(), second.id())),
            List.of(room(1, 40, RoomType.THEORY), room(2, 40, RoomType.THEORY)));

    ScheduleResult result = new ConstructiveScheduler().schedule(problem);

    assertTrue(result.unassignedSessions().isEmpty());
    assertNoOverlaps(result.schedule().byTeacher(1));
  }

  @Test
  void fixedBreakBlocksCandidate() {
    Course course = course(10, 45, false);
    SchedulingProblem problem =
        new SchedulingProblem(
            List.of(cohort(1, 100)),
            List.of(curriculum(course)),
            List.of(teacher(1, course.id())),
            List.of(room(1, 40, RoomType.THEORY)),
            List.of(new FixedBreak(new TimeRange(7 * 60, 45))));

    ScheduleResult result = new ConstructiveScheduler().schedule(problem);

    assertEquals(7 * 60 + 45, result.schedule().assignment(1).orElseThrow().time().startMinuteOfWeek());
  }

  @Test
  void sessionWithoutCandidateIsUnassigned() {
    Course course = course(10, 45, false);
    SchedulingProblem problem =
        new SchedulingProblem(
            List.of(cohort(1, 100)),
            List.of(curriculum(course)),
            List.of(teacher(1, course.id())),
            List.of(room(1, 10, RoomType.THEORY)));

    ScheduleResult result = new ConstructiveScheduler().schedule(problem);

    assertTrue(result.schedule().assignments().isEmpty());
    assertEquals("NO_ROOM", result.unassignedSessions().getFirst().reason());
  }

  @Test
  void sameInputProducesSameResult() {
    Course first = course(10, 45, false);
    Course second = course(11, 45, true);
    SchedulingProblem problem =
        new SchedulingProblem(
            List.of(cohort(2, 200), cohort(1, 100)),
            List.of(curriculum(second), curriculum(first)),
            List.of(teacher(2, first.id(), second.id()), teacher(1, first.id(), second.id())),
            List.of(room(2, 120, RoomType.MIXED), room(1, 120, RoomType.MIXED)));

    ScheduleResult firstRun = new ConstructiveScheduler().schedule(problem);
    ScheduleResult secondRun = new ConstructiveScheduler().schedule(problem);

    assertEquals(fingerprint(firstRun.schedule()), fingerprint(secondRun.schedule()));
    assertEquals(firstRun.unassignedSessions(), secondRun.unassignedSessions());
  }

  private static void assertNoOverlaps(List<Assignment> assignments) {
    List<Assignment> sorted =
        assignments.stream().sorted(Comparator.comparingInt(a -> a.time().startMinuteOfWeek())).toList();
    for (int i = 1; i < sorted.size(); i++) {
      assertFalse(sorted.get(i - 1).time().overlaps(sorted.get(i).time()));
    }
  }

  private static String fingerprint(Schedule schedule) {
    return schedule.assignments().stream()
        .sorted(Comparator.comparingLong(Assignment::sessionId))
        .map(
            assignment ->
                assignment.sessionId()
                    + ":"
                    + assignment.teacherId()
                    + ":"
                    + assignment.roomId()
                    + ":"
                    + assignment.time().startMinuteOfWeek())
        .toList()
        .toString();
  }

  private static CurriculumCourse curriculum(Course course) {
    return new CurriculumCourse("2026", 1, course);
  }

  private static Cohort cohort(long id, long careerId) {
    return new Cohort(id, careerId, "2026", 1, "A", "MAT", 30);
  }

  private static Course course(long id, int weeklyMinutes, boolean requiresLab) {
    return new Course(id, "C" + id, "Course " + id, weeklyMinutes, requiresLab, false, 1, 1);
  }

  private static Teacher teacher(long id, long... courseIds) {
    return new Teacher(id, "Teacher " + id, 0, 0, 6, asSet(courseIds));
  }

  private static Room room(long id, int capacity, RoomType type) {
    return new Room(id, "R" + id, capacity, type, new RoomCoordinate(1, (int) id));
  }

  private static Set<Long> asSet(long... values) {
    return java.util.Arrays.stream(values).boxed().collect(java.util.stream.Collectors.toSet());
  }
}
