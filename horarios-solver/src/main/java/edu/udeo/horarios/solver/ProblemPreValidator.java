package edu.udeo.horarios.solver;

import edu.udeo.horarios.domain.Course;
import edu.udeo.horarios.domain.Room;
import edu.udeo.horarios.domain.RoomType;
import edu.udeo.horarios.domain.SchedulableSession;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ProblemPreValidator {
  private final SessionFactory sessionFactory;

  public ProblemPreValidator() {
    this(new SessionFactory());
  }

  public ProblemPreValidator(SessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  public List<PreValidationIssue> validate(SchedulingProblem problem) {
    List<PreValidationIssue> issues = new ArrayList<>();
    Map<Long, Course> courses =
        problem.curriculumCourses().stream()
            .map(CurriculumCourse::course)
            .collect(Collectors.toMap(Course::id, Function.identity(), (left, right) -> left));

    issues.addAll(duplicateCurriculumCourses(problem));
    issues.addAll(coursesWithoutTeacher(problem, courses.values()));
    issues.addAll(incompatibleRooms(problem, courses));
    return List.copyOf(issues);
  }

  private static List<PreValidationIssue> duplicateCurriculumCourses(SchedulingProblem problem) {
    Set<String> seen = new HashSet<>();
    List<PreValidationIssue> issues = new ArrayList<>();

    for (CurriculumCourse curriculumCourse : problem.curriculumCourses()) {
      String key =
          curriculumCourse.curriculumCode()
              + "|"
              + curriculumCourse.termNumber()
              + "|"
              + curriculumCourse.course().id();
      if (!seen.add(key)) {
        issues.add(
            error(
                "DUPLICATE_CURRICULUM_COURSE",
                "El pensum tiene el curso duplicado.",
                "course",
                curriculumCourse.course().id()));
      }
    }

    return issues;
  }

  private static List<PreValidationIssue> coursesWithoutTeacher(
      SchedulingProblem problem, Iterable<Course> courses) {
    List<PreValidationIssue> issues = new ArrayList<>();

    for (Course course : courses) {
      boolean hasTeacher =
          problem.teachers().stream().anyMatch(teacher -> teacher.courseIds().contains(course.id()));
      if (!hasTeacher) {
        issues.add(
            error(
                "COURSE_WITHOUT_TEACHER",
                "El curso no tiene docente habilitado.",
                "course",
                course.id()));
      }
    }

    return issues;
  }

  private List<PreValidationIssue> incompatibleRooms(
      SchedulingProblem problem, Map<Long, Course> courses) {
    List<PreValidationIssue> issues = new ArrayList<>();

    for (SchedulableSession session : sessionFactory.createSessions(problem)) {
      Course course = courses.get(session.courseId());
      int students =
          problem.cohorts().stream()
              .filter(cohort -> session.cohortIds().contains(cohort.id()))
              .mapToInt(cohort -> cohort.expectedStudents())
              .sum();
      boolean hasRoom =
          problem.rooms().stream().anyMatch(room -> compatible(room, course) && room.capacity() >= students);

      if (!hasRoom) {
        issues.add(
            error(
                course.requiresLab() ? "LAB_WITHOUT_ROOM" : "SESSION_WITHOUT_COMPATIBLE_ROOM",
                "No hay aula compatible para la sesion.",
                "course",
                course.id()));
      }
    }

    return issues;
  }

  private static boolean compatible(Room room, Course course) {
    return !course.requiresLab() || room.type() == RoomType.LAB || room.type() == RoomType.MIXED;
  }

  private static PreValidationIssue error(String code, String message, String type, long id) {
    return new PreValidationIssue(IssueSeverity.ERROR, code, message, type, id);
  }
}
