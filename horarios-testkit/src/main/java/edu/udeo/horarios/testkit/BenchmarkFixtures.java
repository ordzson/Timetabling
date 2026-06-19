package edu.udeo.horarios.testkit;

import edu.udeo.horarios.domain.Cohort;
import edu.udeo.horarios.domain.Course;
import edu.udeo.horarios.domain.Room;
import edu.udeo.horarios.domain.RoomCoordinate;
import edu.udeo.horarios.domain.RoomType;
import edu.udeo.horarios.domain.Teacher;
import edu.udeo.horarios.solver.CurriculumCourse;
import edu.udeo.horarios.solver.SchedulingProblem;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class BenchmarkFixtures {
  private static final String CURRICULUM = "2026";

  private BenchmarkFixtures() {
  }

  public static SchedulingProblem small() {
    return problem("small", 1, 4, 2, 2, 28);
  }

  public static SchedulingProblem medium() {
    return problem("medium", 3, 6, 5, 4, 34);
  }

  public static SchedulingProblem large() {
    return problem("large", 6, 8, 8, 6, 38);
  }

  public static SchedulingProblem infeasibleRoom() {
    Course course = course(1, "infeasible-room", 45, false);
    return new SchedulingProblem(
        List.of(cohort(1, "infeasible-room", 50)),
        List.of(curriculum(course)),
        List.of(teacher(1, "infeasible-room", course.id())),
        List.of(room(1, "too-small", 20, RoomType.THEORY)));
  }

  public static SchedulingProblem infeasibleTeacher() {
    Course course = course(1, "infeasible-teacher", 45, false);
    return new SchedulingProblem(
        List.of(cohort(1, "infeasible-teacher", 30)),
        List.of(curriculum(course)),
        List.of(),
        List.of(room(1, "room", 40, RoomType.THEORY)));
  }

  private static SchedulingProblem problem(
      String prefix, int cohorts, int courses, int teachers, int rooms, int students) {
    List<Cohort> cohortList = new ArrayList<>();
    for (int i = 1; i <= cohorts; i++) {
      cohortList.add(cohort(i, prefix, students + i));
    }

    List<Course> courseList = new ArrayList<>();
    for (int i = 1; i <= courses; i++) {
      courseList.add(course(i, prefix, 45, i % 5 == 0));
    }

    List<CurriculumCourse> curriculum = courseList.stream().map(BenchmarkFixtures::curriculum).toList();
    Set<Long> allCourses = courseList.stream().map(Course::id).collect(Collectors.toUnmodifiableSet());

    List<Teacher> teacherList = new ArrayList<>();
    for (int i = 1; i <= teachers; i++) {
      teacherList.add(new Teacher(i, prefix + "-teacher-" + i, i, 0, courses * cohorts, allCourses));
    }

    List<Room> roomList = new ArrayList<>();
    for (int i = 1; i <= rooms; i++) {
      RoomType type = i == rooms ? RoomType.MIXED : RoomType.THEORY;
      roomList.add(room(i, prefix + "-room-" + i, students + cohorts + 10, type));
    }

    return new SchedulingProblem(cohortList, curriculum, teacherList, roomList);
  }

  private static CurriculumCourse curriculum(Course course) {
    return new CurriculumCourse(CURRICULUM, 1, course);
  }

  private static Cohort cohort(long id, String prefix, int students) {
    return new Cohort(id, id, CURRICULUM, 1, "A", prefix.toUpperCase(), students);
  }

  private static Course course(long id, String prefix, int weeklyMinutes, boolean requiresLab) {
    return new Course(id, prefix + "-C" + id, prefix + " course " + id, weeklyMinutes, requiresLab, false, 1, 1);
  }

  private static Teacher teacher(long id, String prefix, long... courseIds) {
    return new Teacher(
        id,
        prefix + "-teacher-" + id,
        0,
        0,
        courseIds.length,
        java.util.Arrays.stream(courseIds).boxed().collect(Collectors.toUnmodifiableSet()));
  }

  private static Room room(long id, String code, int capacity, RoomType type) {
    return new Room(id, code, capacity, type, new RoomCoordinate(1, (int) id));
  }
}
