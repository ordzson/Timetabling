package edu.udeo.horarios.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.udeo.horarios.domain.Cohort;
import edu.udeo.horarios.domain.Course;
import edu.udeo.horarios.domain.Room;
import edu.udeo.horarios.domain.RoomCoordinate;
import edu.udeo.horarios.domain.RoomType;
import edu.udeo.horarios.domain.SchedulableSession;
import edu.udeo.horarios.domain.Teacher;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PrevalidationSessionFactoryTest {
  @Test
  void courseWithoutTeacherIsError() {
    SchedulingProblem problem =
        new SchedulingProblem(
            List.of(cohort(1, "MAT")),
            List.of(new CurriculumCourse("2026", 1, course(10, false, false))),
            List.of(),
            List.of(classroom(1, 60)));

    List<PreValidationIssue> issues = new ProblemPreValidator().validate(problem);

    assertTrue(hasCode(issues, "COURSE_WITHOUT_TEACHER"));
  }

  @Test
  void labWithoutCompatibleRoomIsError() {
    Course labCourse = course(10, true, false);
    SchedulingProblem problem =
        new SchedulingProblem(
            List.of(cohort(1, "MAT")),
            List.of(new CurriculumCourse("2026", 1, labCourse)),
            List.of(teacher(1, labCourse.id())),
            List.of(classroom(1, 60)));

    List<PreValidationIssue> issues = new ProblemPreValidator().validate(problem);

    assertTrue(hasCode(issues, "LAB_WITHOUT_ROOM"));
  }

  @Test
  void mixedRoomIsCompatibleWithLabCourse() {
    Course labCourse = course(10, true, false);
    SchedulingProblem problem =
        new SchedulingProblem(
            List.of(cohort(1, "MAT")),
            List.of(new CurriculumCourse("2026", 1, labCourse)),
            List.of(teacher(1, labCourse.id())),
            List.of(new Room(1, "R1", 60, RoomType.MIXED, new RoomCoordinate(1, 1))));

    List<PreValidationIssue> issues = new ProblemPreValidator().validate(problem);

    assertTrue(issues.stream().noneMatch(issue -> issue.code().equals("LAB_WITHOUT_ROOM")));
  }

  @Test
  void commonAreaCreatesOneSessionWithMultipleCohorts() {
    Course commonCourse = course(10, false, true);
    SchedulingProblem problem =
        new SchedulingProblem(
            List.of(cohort(1, "MAT"), cohort(2, "MAT")),
            List.of(new CurriculumCourse("2026", 1, commonCourse)),
            List.of(teacher(1, commonCourse.id())),
            List.of(classroom(1, 120)));

    List<SchedulableSession> sessions = new SessionFactory().createSessions(problem);

    assertEquals(1, sessions.size());
    assertEquals(List.of(1L, 2L), sessions.getFirst().cohortIds());
  }

  @Test
  void duplicateCurriculumCourseIsError() {
    Course course = course(10, false, false);
    SchedulingProblem problem =
        new SchedulingProblem(
            List.of(cohort(1, "MAT")),
            List.of(
                new CurriculumCourse("2026", 1, course),
                new CurriculumCourse("2026", 1, course)),
            List.of(teacher(1, course.id())),
            List.of(classroom(1, 60)));

    List<PreValidationIssue> issues = new ProblemPreValidator().validate(problem);

    assertTrue(hasCode(issues, "DUPLICATE_CURRICULUM_COURSE"));
  }

  private static boolean hasCode(List<PreValidationIssue> issues, String code) {
    return issues.stream().anyMatch(issue -> issue.code().equals(code));
  }

  private static Cohort cohort(long id, String journeyCode) {
    return new Cohort(id, 100 + id, "2026", 1, "A", journeyCode, 30);
  }

  private static Course course(long id, boolean requiresLab, boolean commonArea) {
    return new Course(id, "C" + id, "Course " + id, 135, requiresLab, commonArea, 3, 3);
  }

  private static Teacher teacher(long id, long... courseIds) {
    return new Teacher(id, "Teacher " + id, 0, 0, 6, asSet(courseIds));
  }

  private static Room classroom(long id, int capacity) {
    return new Room(id, "R" + id, capacity, RoomType.THEORY, new RoomCoordinate(1, (int) id));
  }

  private static Set<Long> asSet(long... values) {
    return java.util.Arrays.stream(values).boxed().collect(java.util.stream.Collectors.toSet());
  }
}
