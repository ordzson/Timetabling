package edu.udeo.horarios.domain;

public record RoomCoordinate(int floor, int number) {
  public RoomCoordinate {
    if (floor < 0) {
      throw new IllegalArgumentException("floor must be non-negative");
    }
    if (number <= 0) {
      throw new IllegalArgumentException("number must be positive");
    }
  }

  public int distanceTo(RoomCoordinate other) {
    return Math.abs(floor - other.floor) * 10 + Math.abs(number - other.number);
  }
}
