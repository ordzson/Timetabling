package edu.udeo.horarios.domain;

public record Cohort(
    long id,
    long careerId,
    String curriculumCode,
    int termNumber,
    String section,
    String journeyCode,
    int expectedStudents) {
  public Cohort {
    if (id <= 0 || careerId <= 0) {
      throw new IllegalArgumentException("ids must be positive");
    }
    if (curriculumCode == null || curriculumCode.isBlank()) {
      throw new IllegalArgumentException("curriculumCode must not be blank");
    }
    if (termNumber <= 0) {
      throw new IllegalArgumentException("termNumber must be positive");
    }
    if (section == null || section.isBlank()) {
      throw new IllegalArgumentException("section must not be blank");
    }
    if (journeyCode == null || journeyCode.isBlank()) {
      throw new IllegalArgumentException("journeyCode must not be blank");
    }
    if (expectedStudents < 0) {
      throw new IllegalArgumentException("expectedStudents must be non-negative");
    }
  }
}
