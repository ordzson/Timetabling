package edu.udeo.horarios.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.udeo.horarios.domain.Assignment;
import edu.udeo.horarios.domain.Cohort;
import edu.udeo.horarios.domain.Course;
import edu.udeo.horarios.domain.Room;
import edu.udeo.horarios.domain.RoomCoordinate;
import edu.udeo.horarios.domain.RoomType;
import edu.udeo.horarios.domain.SchedulableSession;
import edu.udeo.horarios.domain.SessionType;
import edu.udeo.horarios.domain.Teacher;
import edu.udeo.horarios.domain.TimeRange;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NeighborhoodRepairerTest {
  private final NeighborhoodRepairer repairer = new NeighborhoodRepairer();

  @Test
  void conflictingEditRepairsOnlyNeighborhood() {
    Schedule base = base();

    RepairResult result =
        repairer.repair(
            base,
            problem(),
            sessions(false),
            new ManualEditCommand(1, 1, null, null, 100L, "req-1"),
            new TimeRange(555, 45),
            Set.of(),
            Set.of(1L));

    assertEquals(RepairStatus.APPLIED_WITH_REPAIR, result.status());
    assertTrue(result.remainingViolations().isEmpty());
    assertEquals(Set.of(1L), result.pinnedSessionIds());
    assertTrue(result.movedSessionIds().contains(2L));
  }

  @Test
  void pinnedConflictIsReportedAndNotMoved() {
    Schedule base = base();

    RepairResult result =
        repairer.repair(
            base,
            problem(),
            sessions(true),
            new ManualEditCommand(1, 1, null, null, 100L, "req-1"),
            new TimeRange(555, 45),
            Set.of(2L),
            Set.of(1L));

    assertEquals(RepairStatus.APPLIED_WITH_REMAINING_CONFLICTS, result.status());
    assertTrue(result.movedSessionIds().isEmpty());
    assertTrue(result.remainingViolations().contains(new RepairViolation("TEACHER_OVERLAP", List.of(1L, 2L))));
    assertTrue(result.remainingViolations().contains(new RepairViolation("ROOM_OVERLAP", List.of(1L, 2L))));
  }

  private static Schedule base() {
    Schedule schedule = new Schedule();
    schedule.addAssignment(new Assignment(1, 1, 1, List.of(1L), new TimeRange(420, 45)));
    schedule.addAssignment(new Assignment(2, 1, 1, List.of(2L), new TimeRange(555, 45)));
    return schedule;
  }

  private static List<SchedulableSession> sessions(boolean secondPinned) {
    return List.of(
        new SchedulableSession(1, 1, List.of(1L), SessionType.CLASS, 45, false),
        new SchedulableSession(2, 1, List.of(2L), SessionType.CLASS, 45, secondPinned));
  }

  private static SchedulingProblem problem() {
    Course course = new Course(1, "MAT", "Matematica", 45, false, false, 1, 1);
    return new SchedulingProblem(
        List.of(
            new Cohort(1, 1, "PEN", 1, "A", "MAT", 20),
            new Cohort(2, 1, "PEN", 1, "B", "MAT", 20)),
        List.of(new CurriculumCourse("PEN", 1, course)),
        List.of(new Teacher(1, "Docente", 0, 0, 4, Set.of(1L))),
        List.of(new Room(1, "A1", 40, RoomType.THEORY, new RoomCoordinate(1, 1))),
        List.of());
  }
}
