package edu.udeo.horarios.domain;

public record TimeRange(int startMinuteOfWeek, int durationMinutes) {
  public TimeRange {
    if (startMinuteOfWeek < 0) {
      throw new IllegalArgumentException("startMinuteOfWeek must be non-negative");
    }
    if (durationMinutes <= 0) {
      throw new IllegalArgumentException("durationMinutes must be positive");
    }
  }

  public int endMinuteOfWeek() {
    return startMinuteOfWeek + durationMinutes;
  }

  public boolean overlaps(TimeRange other) {
    return startMinuteOfWeek < other.endMinuteOfWeek()
        && other.startMinuteOfWeek() < endMinuteOfWeek();
  }
}
