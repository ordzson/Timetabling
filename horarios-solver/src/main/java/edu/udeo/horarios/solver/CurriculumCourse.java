package edu.udeo.horarios.solver;

import edu.udeo.horarios.domain.Course;

public record CurriculumCourse(String curriculumCode, int termNumber, Course course) {
  public CurriculumCourse {
    if (curriculumCode == null || curriculumCode.isBlank()) {
      throw new IllegalArgumentException("curriculumCode must not be blank");
    }
    if (termNumber <= 0) {
      throw new IllegalArgumentException("termNumber must be positive");
    }
    if (course == null) {
      throw new IllegalArgumentException("course must not be null");
    }
  }
}
