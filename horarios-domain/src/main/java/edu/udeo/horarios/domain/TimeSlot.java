package edu.udeo.horarios.domain;

public record TimeSlot(int startMinuteOfWeek) {
  public TimeSlot {
    if (startMinuteOfWeek < 0) {
      throw new IllegalArgumentException("startMinuteOfWeek must be non-negative");
    }
  }

  public TimeRange withDuration(int durationMinutes) {
    return new TimeRange(startMinuteOfWeek, durationMinutes);
  }
}
