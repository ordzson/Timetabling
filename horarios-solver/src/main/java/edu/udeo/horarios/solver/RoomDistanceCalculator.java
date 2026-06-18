package edu.udeo.horarios.solver;

import edu.udeo.horarios.domain.Assignment;
import edu.udeo.horarios.domain.Room;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class RoomDistanceCalculator {
  private final Map<Long, Room> rooms;

  public RoomDistanceCalculator(List<Room> rooms) {
    this.rooms = rooms.stream().collect(Collectors.toMap(Room::id, Function.identity()));
  }

  public long teacherWalkingDistance(Schedule schedule) {
    return schedule.assignments().stream()
        .map(Assignment::teacherId)
        .distinct()
        .mapToLong(teacherId -> distance(schedule.byTeacher(teacherId)))
        .sum();
  }

  private long distance(List<Assignment> assignments) {
    List<Assignment> sorted =
        assignments.stream()
            .sorted(Comparator.comparingInt(assignment -> assignment.time().startMinuteOfWeek()))
            .toList();
    long distance = 0;
    for (int i = 1; i < sorted.size(); i++) {
      Room left = rooms.get(sorted.get(i - 1).roomId());
      Room right = rooms.get(sorted.get(i).roomId());
      if (left != null && right != null) {
        distance += left.coordinate().distanceTo(right.coordinate());
      }
    }
    return distance;
  }
}
