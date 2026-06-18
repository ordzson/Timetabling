package edu.udeo.horarios.domain;

public record Room(long id, String code, int capacity, RoomType type, RoomCoordinate coordinate) {
  public Room {
    if (id <= 0) {
      throw new IllegalArgumentException("id must be positive");
    }
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("code must not be blank");
    }
    if (capacity <= 0) {
      throw new IllegalArgumentException("capacity must be positive");
    }
    if (type == null) {
      throw new IllegalArgumentException("type must not be null");
    }
    if (coordinate == null) {
      throw new IllegalArgumentException("coordinate must not be null");
    }
  }
}
