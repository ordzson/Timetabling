package edu.udeo.horarios.solver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.udeo.horarios.domain.Cohort;
import edu.udeo.horarios.domain.Course;
import edu.udeo.horarios.domain.Room;
import edu.udeo.horarios.domain.RoomCoordinate;
import edu.udeo.horarios.domain.RoomType;
import edu.udeo.horarios.domain.Teacher;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SolverBenchmarkContractTest {
  @Test
  void constructiveBaselineIsFastEnoughForSmallFixture() {
    Course course = new Course(1, "BENCH", "Benchmark", 45, false, false, 1, 1);
    SchedulingProblem problem =
        new SchedulingProblem(
            List.of(new Cohort(1, 1, "2026", 1, "A", "DAY", 25)),
            List.of(new CurriculumCourse("2026", 1, course)),
            List.of(new Teacher(1, "Teacher", 0, 0, 2, Set.of(course.id()))),
            List.of(new Room(1, "R1", 30, RoomType.THEORY, new RoomCoordinate(1, 1))));

    long started = System.nanoTime();
    ScheduleResult result = new ConstructiveScheduler().schedule(problem);
    long elapsedMillis = java.time.Duration.ofNanos(System.nanoTime() - started).toMillis();

    assertTrue(elapsedMillis < 2_000);
    assertFalse(result.schedule().assignments().isEmpty());
  }
}
