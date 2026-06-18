package edu.udeo.horarios.domain;

public record Course(
    long id,
    String code,
    String name,
    int weeklyMinutes,
    boolean requiresLab,
    boolean commonArea,
    int minBlocks,
    int maxBlocks) {
  public Course {
    requirePositive(id, "id");
    requireText(code, "code");
    requireText(name, "name");
    requirePositive(weeklyMinutes, "weeklyMinutes");
    requirePositive(minBlocks, "minBlocks");
    if (maxBlocks < minBlocks) {
      throw new IllegalArgumentException("maxBlocks must be greater than or equal to minBlocks");
    }
  }

  private static void requirePositive(long value, String name) {
    if (value <= 0) {
      throw new IllegalArgumentException(name + " must be positive");
    }
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
