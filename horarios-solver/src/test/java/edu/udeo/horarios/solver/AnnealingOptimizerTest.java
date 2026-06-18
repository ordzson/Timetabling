package edu.udeo.horarios.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import edu.udeo.horarios.domain.TimeSlot;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AnnealingOptimizerTest {
  @Test
  void scoreSeparatesMetrics() {
    Score score = new Score(0, 1, 2, 3, 4, 5, 6);

    assertEquals(103_980, score.totalCost());
    assertEquals(1, score.hardViolations());
    assertEquals(3, score.nonContiguousCourseBlocks());
    assertEquals(5, score.walkingDistance());
  }

  @Test
  void invalidMoveDoesNotMutateSchedule() {
    Fixture fixture = fixture();
    Assignment before = fixture.schedule().assignment(1).orElseThrow();
    MoveProposal conflict =
        new MoveProposal(
            before,
            new Assignment(1, 1, 1, List.of(1L), new TimeRange(60, 60)),
            "");

    MoveEvaluation evaluation = fixture.evaluator().evaluate(fixture.schedule(), conflict);
    boolean applied = new MoveApplier().apply(fixture.schedule(), conflict, evaluation);

    assertFalse(applied);
    assertEquals(before, fixture.schedule().assignment(1).orElseThrow());
  }

  @Test
  void normalModeKeepsHardConstraints() {
    Fixture fixture = fixture();
    Schedule best = fixture.optimizer(9, 200).optimize(fixture.schedule());
    HardConstraintChecker checker = fixture.checker();

    for (Assignment assignment : best.assignments()) {
      Schedule without = best.copy();
      without.removeAssignment(assignment.sessionId());
      assertTrue(checker.canApply(without, assignment));
    }
  }

  @Test
  void fixedSeedProducesFixedResult() {
    Fixture first = fixture();
    Fixture second = fixture();

    String firstFingerprint = fingerprint(first.optimizer(42, 150).optimize(first.schedule()));
    String secondFingerprint = fingerprint(second.optimizer(42, 150).optimize(second.schedule()));

    assertEquals(firstFingerprint, secondFingerprint);
  }

  @Test
  void globalBestDoesNotGetWorse() {
    Fixture fixture = fixture();
    Score before = fixture.scorer().score(fixture.schedule());
    Schedule best = fixture.optimizer(5, 250).optimize(fixture.schedule());

    assertTrue(fixture.scorer().score(best).compareTo(before) <= 0);
  }

  private static String fingerprint(Schedule schedule) {
    return schedule.assignments().stream()
        .sorted(Comparator.comparingLong(Assignment::sessionId))
        .map(
            assignment ->
                assignment.sessionId()
                    + ":"
                    + assignment.teacherId()
                    + ":"
                    + assignment.roomId()
                    + ":"
                    + assignment.time().startMinuteOfWeek())
        .toList()
        .toString();
  }

  private static Fixture fixture() {
    SchedulableSession first = new SchedulableSession(1, 10, List.of(1L), SessionType.CLASS, 60, false);
    SchedulableSession second = new SchedulableSession(2, 11, List.of(1L), SessionType.CLASS, 60, false);
    Course firstCourse = course(10);
    Course secondCourse = course(11);
    SchedulingProblem problem =
        new SchedulingProblem(
            List.of(cohort()),
            List.of(curriculum(firstCourse), curriculum(secondCourse)),
            List.of(teacher(1, firstCourse.id(), secondCourse.id())),
            List.of(room(1, 1), room(2, 50)));
    TimeGrid grid =
        new TimeGrid(List.of(new TimeSlot(0), new TimeSlot(60), new TimeSlot(120)), List.of());
    List<CandidateSpace> spaces =
        List.of(
            new CandidateSpace(
                first,
                List.of(new Candidate(1, 1, new TimeRange(0, 60)), new Candidate(1, 2, new TimeRange(120, 60))),
                1,
                2,
                2,
                ""),
            new CandidateSpace(
                second,
                List.of(new Candidate(1, 2, new TimeRange(60, 60)), new Candidate(1, 1, new TimeRange(120, 60))),
                1,
                2,
                2,
                ""));

    Schedule schedule = new Schedule();
    schedule.addAssignment(spaces.get(0).candidates().get(0).toAssignment(first));
    schedule.addAssignment(spaces.get(1).candidates().get(0).toAssignment(second));

    HardConstraintChecker checker = new HardConstraintChecker(grid, List.of(first, second), problem);
    IncrementalSoftScorer scorer = new IncrementalSoftScorer(spaces, problem);
    MoveEvaluator evaluator = new MoveEvaluator(checker, scorer);
    return new Fixture(schedule, spaces, checker, scorer, evaluator);
  }

  private static CurriculumCourse curriculum(Course course) {
    return new CurriculumCourse("2026", 1, course);
  }

  private static Cohort cohort() {
    return new Cohort(1, 100, "2026", 1, "A", "MAT", 30);
  }

  private static Course course(long id) {
    return new Course(id, "C" + id, "Course " + id, 60, false, false, 1, 1);
  }

  private static Teacher teacher(long id, long... courseIds) {
    return new Teacher(id, "Teacher " + id, 0, 0, 6, asSet(courseIds));
  }

  private static Room room(long id, int number) {
    return new Room(id, "R" + id, 40, RoomType.THEORY, new RoomCoordinate(1, number));
  }

  private static Set<Long> asSet(long... values) {
    return java.util.Arrays.stream(values).boxed().collect(java.util.stream.Collectors.toSet());
  }

  private record Fixture(
      Schedule schedule,
      List<CandidateSpace> spaces,
      HardConstraintChecker checker,
      IncrementalSoftScorer scorer,
      MoveEvaluator evaluator) {
    AnnealingOptimizer optimizer(long seed, int maxIterations) {
      return new AnnealingOptimizer(
          new MoveGenerator(spaces, seed),
          evaluator,
          new MoveApplier(),
          scorer,
          new AnnealingConfig(10.0, 0.98, maxIterations, 4),
          seed);
    }
  }
}
