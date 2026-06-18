package edu.udeo.horarios.solver;

import edu.udeo.horarios.domain.Assignment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class Schedule {
  private final Map<Long, Assignment> bySession = new HashMap<>();
  private final Map<Long, List<Assignment>> byTeacher = new HashMap<>();
  private final Map<Long, List<Assignment>> byRoom = new HashMap<>();
  private final Map<Long, List<Assignment>> byCohort = new HashMap<>();

  public void addAssignment(Assignment assignment) {
    if (bySession.containsKey(assignment.sessionId())) {
      throw new IllegalArgumentException("session already assigned");
    }
    put(assignment);
  }

  public void removeAssignment(long sessionId) {
    Assignment assignment = bySession.remove(sessionId);
    if (assignment == null) {
      return;
    }
    removeFromIndexes(assignment);
  }

  public void moveAssignment(long sessionId, Assignment assignment) {
    if (sessionId != assignment.sessionId()) {
      throw new IllegalArgumentException("session id mismatch");
    }
    if (!bySession.containsKey(sessionId)) {
      throw new IllegalArgumentException("session not assigned");
    }

    Assignment original = bySession.remove(sessionId);
    removeFromIndexes(original);
    put(assignment);
  }

  public Optional<Assignment> assignment(long sessionId) {
    return Optional.ofNullable(bySession.get(sessionId));
  }

  public List<Assignment> assignments() {
    return List.copyOf(bySession.values());
  }

  public List<Assignment> byTeacher(long teacherId) {
    return copy(byTeacher, teacherId);
  }

  public List<Assignment> byRoom(long roomId) {
    return copy(byRoom, roomId);
  }

  public List<Assignment> byCohort(long cohortId) {
    return copy(byCohort, cohortId);
  }

  private void put(Assignment assignment) {
    bySession.put(assignment.sessionId(), assignment);
    byTeacher.computeIfAbsent(assignment.teacherId(), ignored -> new ArrayList<>()).add(assignment);
    byRoom.computeIfAbsent(assignment.roomId(), ignored -> new ArrayList<>()).add(assignment);
    for (long cohortId : assignment.cohortIds()) {
      byCohort.computeIfAbsent(cohortId, ignored -> new ArrayList<>()).add(assignment);
    }
  }

  private void removeFromIndexes(Assignment assignment) {
    removeIndexed(byTeacher, assignment.teacherId(), assignment);
    removeIndexed(byRoom, assignment.roomId(), assignment);
    for (long cohortId : assignment.cohortIds()) {
      removeIndexed(byCohort, cohortId, assignment);
    }
  }

  private static void removeIndexed(
      Map<Long, List<Assignment>> index, long key, Assignment assignment) {
    List<Assignment> assignments = index.get(key);
    if (assignments == null) {
      return;
    }
    assignments.remove(assignment);
    if (assignments.isEmpty()) {
      index.remove(key);
    }
  }

  private static List<Assignment> copy(Map<Long, List<Assignment>> index, long key) {
    return List.copyOf(index.getOrDefault(key, List.of()));
  }
}
