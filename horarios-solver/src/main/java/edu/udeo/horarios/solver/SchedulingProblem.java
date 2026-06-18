package edu.udeo.horarios.solver;

import edu.udeo.horarios.domain.Cohort;
import edu.udeo.horarios.domain.Room;
import edu.udeo.horarios.domain.Teacher;
import java.util.List;

public record SchedulingProblem(
    List<Cohort> cohorts,
    List<CurriculumCourse> curriculumCourses,
    List<Teacher> teachers,
    List<Room> rooms,
    List<FixedBreak> fixedBreaks) {
  public SchedulingProblem(
      List<Cohort> cohorts,
      List<CurriculumCourse> curriculumCourses,
      List<Teacher> teachers,
      List<Room> rooms) {
    this(cohorts, curriculumCourses, teachers, rooms, List.of());
  }

  public SchedulingProblem {
    cohorts = List.copyOf(cohorts == null ? List.of() : cohorts);
    curriculumCourses = List.copyOf(curriculumCourses == null ? List.of() : curriculumCourses);
    teachers = List.copyOf(teachers == null ? List.of() : teachers);
    rooms = List.copyOf(rooms == null ? List.of() : rooms);
    fixedBreaks = List.copyOf(fixedBreaks == null ? List.of() : fixedBreaks);
  }
}
