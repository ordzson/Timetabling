package edu.udeo.horarios.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DomainCoreTest {
  @Test
  void invalidDurationsFail() {
    assertThrows(IllegalArgumentException.class, () -> new TimeRange(0, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new SchedulableSession(1, 2, List.of(3L), SessionType.CLASS, -45, false));
  }

  @Test
  void timeRangeOverlapsCoversEdges() {
    TimeRange first = new TimeRange(60, 45);

    assertTrue(first.overlaps(new TimeRange(90, 45)));
    assertTrue(first.overlaps(new TimeRange(30, 45)));
    assertFalse(first.overlaps(new TimeRange(105, 45)));
    assertFalse(first.overlaps(new TimeRange(15, 45)));
  }

  @Test
  void roomDistanceUsesAgreedFormula() {
    assertEquals(22, new RoomCoordinate(3, 1).distanceTo(new RoomCoordinate(1, 3)));
  }

  @Test
  void collectionsAreImmutableCopies() {
    Assignment assignment = new Assignment(1, 2, 3, List.of(4L), new TimeRange(0, 45));

    assertThrows(UnsupportedOperationException.class, () -> assignment.cohortIds().add(5L));
  }
}
