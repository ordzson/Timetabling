package edu.udeo.horarios.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.udeo.horarios.domain.Assignment;
import edu.udeo.horarios.domain.TimeRange;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduleTest {
  @Test
  void rejectsDuplicateSession() {
    Schedule schedule = new Schedule();
    Assignment assignment = assignment(1, 10, 20, 30);

    schedule.addAssignment(assignment);

    assertThrows(IllegalArgumentException.class, () -> schedule.addAssignment(assignment));
  }

  @Test
  void removeAssignmentCleansAllIndexes() {
    Schedule schedule = new Schedule();
    Assignment assignment = assignment(1, 10, 20, 30, 31);

    schedule.addAssignment(assignment);
    schedule.removeAssignment(1);

    assertTrue(schedule.assignment(1).isEmpty());
    assertTrue(schedule.byTeacher(10).isEmpty());
    assertTrue(schedule.byRoom(20).isEmpty());
    assertTrue(schedule.byCohort(30).isEmpty());
    assertTrue(schedule.byCohort(31).isEmpty());
  }

  @Test
  void moveAssignmentUpdatesIndexes() {
    Schedule schedule = new Schedule();

    schedule.addAssignment(assignment(1, 10, 20, 30));
    Assignment moved = assignment(1, 11, 21, 31);
    schedule.moveAssignment(1, moved);

    assertEquals(List.of(moved), schedule.byTeacher(11));
    assertEquals(List.of(moved), schedule.byRoom(21));
    assertEquals(List.of(moved), schedule.byCohort(31));
    assertTrue(schedule.byTeacher(10).isEmpty());
    assertTrue(schedule.byRoom(20).isEmpty());
    assertTrue(schedule.byCohort(30).isEmpty());
  }

  @Test
  void moveAssignmentDoesNotMutateWhenInvalid() {
    Schedule schedule = new Schedule();
    Assignment original = assignment(1, 10, 20, 30);

    schedule.addAssignment(original);
    assertThrows(
        IllegalArgumentException.class, () -> schedule.moveAssignment(2, assignment(1, 11, 21, 31)));

    assertEquals(List.of(original), schedule.byTeacher(10));
    assertEquals(List.of(original), schedule.byRoom(20));
    assertEquals(List.of(original), schedule.byCohort(30));
  }

  @Test
  void viewsAreImmutable() {
    Schedule schedule = new Schedule();
    schedule.addAssignment(assignment(1, 10, 20, 30));

    assertThrows(UnsupportedOperationException.class, () -> schedule.byTeacher(10).clear());
    assertThrows(UnsupportedOperationException.class, () -> schedule.assignments().clear());
  }

  private static Assignment assignment(long sessionId, long teacherId, long roomId, long... cohorts) {
    return new Assignment(sessionId, teacherId, roomId, asList(cohorts), new TimeRange(480, 90));
  }

  private static List<Long> asList(long... values) {
    return java.util.Arrays.stream(values).boxed().toList();
  }
}
