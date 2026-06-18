package edu.udeo.horarios.domain;

import java.util.Set;

public record Teacher(long id, String name, int priority, int minCourses, int maxCourses, Set<Long> courseIds) {
  public Teacher {
    if (id <= 0) {
      throw new IllegalArgumentException("id must be positive");
    }
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    if (priority < 0) {
      throw new IllegalArgumentException("priority must be non-negative");
    }
    if (minCourses < 0) {
      throw new IllegalArgumentException("minCourses must be non-negative");
    }
    if (maxCourses < minCourses) {
      throw new IllegalArgumentException("maxCourses must be greater than or equal to minCourses");
    }
    courseIds = Set.copyOf(courseIds == null ? Set.of() : courseIds);
  }
}
