package edu.udeo.horarios.testkit;

import edu.udeo.horarios.domain.SchedulableSession;
import edu.udeo.horarios.solver.AnnealingConfig;
import edu.udeo.horarios.solver.AnnealingOptimizer;
import edu.udeo.horarios.solver.CandidateGenerator;
import edu.udeo.horarios.solver.CandidateSpace;
import edu.udeo.horarios.solver.ConstructiveScheduler;
import edu.udeo.horarios.solver.HardConstraintChecker;
import edu.udeo.horarios.solver.IncrementalSoftScorer;
import edu.udeo.horarios.solver.IssueSeverity;
import edu.udeo.horarios.solver.MoveApplier;
import edu.udeo.horarios.solver.MoveEvaluator;
import edu.udeo.horarios.solver.MoveGenerator;
import edu.udeo.horarios.solver.PreValidationIssue;
import edu.udeo.horarios.solver.ProblemPreValidator;
import edu.udeo.horarios.solver.Schedule;
import edu.udeo.horarios.solver.ScheduleResult;
import edu.udeo.horarios.solver.SchedulingProblem;
import edu.udeo.horarios.solver.Score;
import edu.udeo.horarios.solver.SessionFactory;
import edu.udeo.horarios.solver.TimeGrid;
import edu.udeo.horarios.solver.TimeGridBuilder;
import edu.udeo.horarios.solver.UnassignedSession;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class BenchmarkRunner {
  private final ConstructiveScheduler constructiveScheduler = new ConstructiveScheduler();
  private final ProblemPreValidator preValidator = new ProblemPreValidator();
  private final SessionFactory sessionFactory = new SessionFactory();
  private final TimeGridBuilder gridBuilder = new TimeGridBuilder();

  public List<BenchmarkResult> runDefaultSuite() {
    return List.of(
        run("small", BenchmarkFixtures.small(), options(Duration.ofSeconds(2), 11L, 200)),
        run("medium", BenchmarkFixtures.medium(), options(Duration.ofSeconds(5), 13L, 400)),
        run("large", BenchmarkFixtures.large(), options(Duration.ofSeconds(10), 17L, 600)),
        run("infeasible-room", BenchmarkFixtures.infeasibleRoom(), options(Duration.ofSeconds(2), 19L, 100)),
        run("infeasible-teacher", BenchmarkFixtures.infeasibleTeacher(), options(Duration.ofSeconds(2), 23L, 100)));
  }

  public BenchmarkResult run(String name, SchedulingProblem problem, BenchmarkOptions options) {
    long started = System.nanoTime();
    List<PreValidationIssue> issues = preValidator.validate(problem);
    ScheduleResult constructive = constructiveScheduler.schedule(problem);
    List<CandidateSpace> spaces = candidateSpaces(problem);
    IncrementalSoftScorer scorer = new IncrementalSoftScorer(spaces, problem);
    Score constructiveScore = scorer.score(constructive.schedule());

    Schedule annealed = constructive.schedule();
    if (issues.stream().noneMatch(issue -> issue.severity() == IssueSeverity.ERROR)
        && !constructive.schedule().assignments().isEmpty()
        && withinLimit(started, options.timeLimit())) {
      annealed = anneal(problem, spaces, constructive.schedule(), scorer, options);
    }

    long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
    Score annealedScore = scorer.score(annealed);
    return new BenchmarkResult(
        name,
        options.seed(),
        options.timeLimit(),
        elapsedMillis,
        elapsedMillis <= options.timeLimit().toMillis(),
        constructive.schedule().assignments().size(),
        constructive.unassignedSessions(),
        issues,
        constructiveScore,
        annealedScore);
  }

  private Schedule anneal(
      SchedulingProblem problem,
      List<CandidateSpace> spaces,
      Schedule initial,
      IncrementalSoftScorer scorer,
      BenchmarkOptions options) {
    TimeGrid grid = gridBuilder.build(problem);
    HardConstraintChecker checker = new HardConstraintChecker(grid, sessions(problem), problem);
    MoveEvaluator evaluator = new MoveEvaluator(checker, scorer);
    return new AnnealingOptimizer(
            new MoveGenerator(spaces, options.seed()),
            evaluator,
            new MoveApplier(),
            scorer,
            new AnnealingConfig(10.0, 0.995, options.maxAnnealingIterations(), 8),
            options.seed())
        .optimize(initial);
  }

  private List<CandidateSpace> candidateSpaces(SchedulingProblem problem) {
    CandidateGenerator generator = new CandidateGenerator(gridBuilder.build(problem));
    return sessions(problem).stream().map(session -> generator.generate(problem, session)).toList();
  }

  private List<SchedulableSession> sessions(SchedulingProblem problem) {
    return sessionFactory.createSessions(problem);
  }

  private static BenchmarkOptions options(Duration timeLimit, long seed, int maxAnnealingIterations) {
    return new BenchmarkOptions(timeLimit, seed, maxAnnealingIterations);
  }

  private static boolean withinLimit(long started, Duration timeLimit) {
    return Duration.ofNanos(System.nanoTime() - started).compareTo(timeLimit) <= 0;
  }

  public static Map<String, SchedulingProblem> fixtures() {
    return Map.of(
        "small", BenchmarkFixtures.small(),
        "medium", BenchmarkFixtures.medium(),
        "large", BenchmarkFixtures.large(),
        "infeasible-room", BenchmarkFixtures.infeasibleRoom(),
        "infeasible-teacher", BenchmarkFixtures.infeasibleTeacher());
  }

  public record BenchmarkOptions(Duration timeLimit, long seed, int maxAnnealingIterations) {
    public BenchmarkOptions {
      if (timeLimit == null || timeLimit.isNegative() || timeLimit.isZero()) {
        throw new IllegalArgumentException("timeLimit must be positive");
      }
      if (maxAnnealingIterations < 0) {
        throw new IllegalArgumentException("maxAnnealingIterations must be non-negative");
      }
    }
  }

  public record BenchmarkResult(
      String name,
      long seed,
      Duration timeLimit,
      long elapsedMillis,
      boolean withinTimeLimit,
      int constructiveAssignments,
      List<UnassignedSession> constructiveUnassigned,
      List<PreValidationIssue> preValidationIssues,
      Score constructiveScore,
      Score annealedScore) {
    public long timeLimitSeconds() {
      return timeLimit.toSeconds();
    }

    public boolean infeasibleExplained() {
      return !preValidationIssues.isEmpty() || !constructiveUnassigned.isEmpty();
    }

    public boolean annealingDidNotWorsen() {
      return annealedScore.compareTo(constructiveScore) <= 0;
    }
  }
}
