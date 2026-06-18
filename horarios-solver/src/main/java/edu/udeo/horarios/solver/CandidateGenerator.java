package edu.udeo.horarios.solver;

import edu.udeo.horarios.domain.Cohort;
import edu.udeo.horarios.domain.Course;
import edu.udeo.horarios.domain.Room;
import edu.udeo.horarios.domain.RoomType;
import edu.udeo.horarios.domain.SchedulableSession;
import edu.udeo.horarios.domain.Teacher;
import edu.udeo.horarios.domain.TimeRange;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class CandidateGenerator {
  private final TimeGrid grid;

  public CandidateGenerator(TimeGrid grid) {
    this.grid = grid;
  }

  public CandidateSpace generate(SchedulingProblem problem, SchedulableSession session) {
    Map<Long, Course> courses =
        problem.curriculumCourses().stream()
            .map(CurriculumCourse::course)
            .collect(Collectors.toMap(Course::id, Function.identity(), (left, right) -> left));
    Course course = courses.get(session.courseId());
    if (course == null) {
      return new CandidateSpace(session, List.of(), 0, 0, 0, "NO_COURSE");
    }

    int students = students(problem, session);
    List<Teacher> teachers =
        problem.teachers().stream()
            .filter(teacher -> teacher.courseIds().contains(session.courseId()))
            .sorted(Comparator.comparingInt(Teacher::priority).thenComparingLong(Teacher::id))
            .toList();
    List<Room> rooms =
        problem.rooms().stream()
            .filter(room -> compatible(room, course) && room.capacity() >= students)
            .sorted(Comparator.comparingLong(Room::id))
            .toList();
    List<TimeRange> starts =
        grid.starts().stream()
            .map(start -> start.withDuration(session.durationMinutes()))
            .filter(grid::accepts)
            .toList();

    List<Candidate> candidates = new ArrayList<>();
    for (Teacher teacher : teachers) {
      for (Room room : rooms) {
        for (TimeRange start : starts) {
          candidates.add(new Candidate(teacher.id(), room.id(), start));
        }
      }
    }

    return new CandidateSpace(
        session, candidates, teachers.size(), rooms.size(), starts.size(), emptyReason(teachers, rooms, starts));
  }

  private static int students(SchedulingProblem problem, SchedulableSession session) {
    return problem.cohorts().stream()
        .filter(cohort -> session.cohortIds().contains(cohort.id()))
        .mapToInt(Cohort::expectedStudents)
        .sum();
  }

  private static boolean compatible(Room room, Course course) {
    return !course.requiresLab() || room.type() == RoomType.LAB || room.type() == RoomType.MIXED;
  }

  private static String emptyReason(List<Teacher> teachers, List<Room> rooms, List<TimeRange> starts) {
    if (teachers.isEmpty()) {
      return "NO_TEACHER";
    }
    if (rooms.isEmpty()) {
      return "NO_ROOM";
    }
    if (starts.isEmpty()) {
      return "NO_TIME";
    }
    return "";
  }
}
