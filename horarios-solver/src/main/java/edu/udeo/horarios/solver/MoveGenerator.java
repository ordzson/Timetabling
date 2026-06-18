package edu.udeo.horarios.solver;

import edu.udeo.horarios.domain.Assignment;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;

public final class MoveGenerator {
  private final List<CandidateSpace> spaces;
  private final SplittableRandom random;

  public MoveGenerator(List<CandidateSpace> spaces, long seed) {
    this.spaces =
        spaces.stream()
            .filter(space -> !space.session().pinned())
            .sorted(Comparator.comparingLong(space -> space.session().id()))
            .toList();
    this.random = new SplittableRandom(seed);
  }

  public MoveProposal next(Schedule schedule) {
    List<CandidateSpace> assigned =
        spaces.stream().filter(space -> schedule.assignment(space.session().id()).isPresent()).toList();
    if (assigned.isEmpty()) {
      return MoveProposal.rejected("NO_ASSIGNED_SESSION");
    }

    CandidateSpace space = assigned.get(random.nextInt(assigned.size()));
    Assignment current = schedule.assignment(space.session().id()).orElseThrow();
    List<Candidate> candidates =
        space.candidates().stream()
            .filter(candidate -> !samePlacement(current, candidate))
            .sorted(candidateComparator())
            .toList();
    if (candidates.isEmpty()) {
      return MoveProposal.rejected("NO_ALTERNATIVE_CANDIDATE");
    }

    Candidate candidate = candidates.get(random.nextInt(candidates.size()));
    return new MoveProposal(current, candidate.toAssignment(space.session()), "");
  }

  private static boolean samePlacement(Assignment current, Candidate candidate) {
    return current.teacherId() == candidate.teacherId()
        && current.roomId() == candidate.roomId()
        && current.time().equals(candidate.time());
  }

  private static Comparator<Candidate> candidateComparator() {
    return Comparator.comparingInt((Candidate candidate) -> candidate.time().startMinuteOfWeek())
        .thenComparingLong(Candidate::teacherId)
        .thenComparingLong(Candidate::roomId);
  }
}
