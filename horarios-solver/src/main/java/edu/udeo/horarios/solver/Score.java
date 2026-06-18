package edu.udeo.horarios.solver;

public record Score(
    long totalCost,
    long hardViolations,
    long teacherUnavailableBlocks,
    long nonContiguousCourseBlocks,
    long gapsNotAtEnd,
    long walkingDistance,
    long teacherLoadDeviation)
    implements Comparable<Score> {
  public Score {
    totalCost =
        100_000 * hardViolations
            + 1_000 * teacherUnavailableBlocks
            + 500 * nonContiguousCourseBlocks
            + 100 * gapsNotAtEnd
            + 10 * walkingDistance
            + 5 * teacherLoadDeviation;
  }

  public static Score zero() {
    return new Score(0, 0, 0, 0, 0, 0, 0);
  }

  @Override
  public int compareTo(Score other) {
    int result = Long.compare(hardViolations, other.hardViolations);
    if (result != 0) {
      return result;
    }
    result = Long.compare(teacherUnavailableBlocks, other.teacherUnavailableBlocks);
    if (result != 0) {
      return result;
    }
    result = Long.compare(nonContiguousCourseBlocks, other.nonContiguousCourseBlocks);
    if (result != 0) {
      return result;
    }
    result = Long.compare(gapsNotAtEnd, other.gapsNotAtEnd);
    if (result != 0) {
      return result;
    }
    result = Long.compare(walkingDistance, other.walkingDistance);
    if (result != 0) {
      return result;
    }
    result = Long.compare(teacherLoadDeviation, other.teacherLoadDeviation);
    if (result != 0) {
      return result;
    }
    return Long.compare(totalCost, other.totalCost);
  }
}
