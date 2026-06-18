package edu.udeo.horarios.solver;

import edu.udeo.horarios.domain.Assignment;
import edu.udeo.horarios.domain.SchedulableSession;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ConstructiveScheduler {
  private final SessionFactory sessionFactory;
  private final TimeGridBuilder timeGridBuilder;
  private final DifficultyRanker difficultyRanker;

  public ConstructiveScheduler() {
    this(new SessionFactory(), new TimeGridBuilder(), new DifficultyRanker());
  }

  public ConstructiveScheduler(
      SessionFactory sessionFactory, TimeGridBuilder timeGridBuilder, DifficultyRanker difficultyRanker) {
    this.sessionFactory = sessionFactory;
    this.timeGridBuilder = timeGridBuilder;
    this.difficultyRanker = difficultyRanker;
  }

  public ScheduleResult schedule(SchedulingProblem problem) {
    TimeGrid grid = timeGridBuilder.build(problem);
    CandidateGenerator generator = new CandidateGenerator(grid);
    List<SchedulableSession> sessions = sessionFactory.createSessions(problem);
    HardConstraintChecker checker = new HardConstraintChecker(grid, sessions, problem);
    List<CandidateSpace> spaces =
        sessions.stream()
            .map(session -> generator.generate(problem, session))
            .toList();

    Schedule schedule = new Schedule();
    List<UnassignedSession> unassigned = new ArrayList<>();

    for (CandidateSpace space : difficultyRanker.rank(problem, spaces)) {
      Candidate candidate =
          space.candidates().stream()
              .sorted(candidateComparator())
              .filter(option -> checker.canApply(schedule, option.toAssignment(space.session())))
              .findFirst()
              .orElse(null);

      if (candidate == null) {
        unassigned.add(unassigned(space));
      } else {
        schedule.addAssignment(candidate.toAssignment(space.session()));
      }
    }

    return new ScheduleResult(schedule, unassigned);
  }

  private static Comparator<Candidate> candidateComparator() {
    return Comparator.comparingInt((Candidate candidate) -> candidate.time().startMinuteOfWeek())
        .thenComparingLong(Candidate::teacherId)
        .thenComparingLong(Candidate::roomId);
  }

  private static UnassignedSession unassigned(CandidateSpace space) {
    String reason = space.emptyReason().isBlank() ? "HARD_CONFLICT" : space.emptyReason();
    SchedulableSession session = space.session();
    return new UnassignedSession(session, reason, space.candidates().size());
  }
}
