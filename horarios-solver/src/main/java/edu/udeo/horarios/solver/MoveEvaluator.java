package edu.udeo.horarios.solver;

public final class MoveEvaluator {
  private final HardConstraintChecker checker;
  private final IncrementalSoftScorer scorer;

  public MoveEvaluator(HardConstraintChecker checker, IncrementalSoftScorer scorer) {
    this.checker = checker;
    this.scorer = scorer;
  }

  public MoveEvaluation evaluate(Schedule schedule, MoveProposal proposal) {
    if (!proposal.applicable()) {
      return MoveEvaluation.rejected(proposal.rejectionReason());
    }

    Schedule candidate = schedule.copy();
    if (proposal.current() != null) {
      candidate.removeAssignment(proposal.current().sessionId());
    }
    if (!checker.canApply(candidate, proposal.replacement())) {
      return MoveEvaluation.rejected("HARD_CONFLICT");
    }
    return new MoveEvaluation(true, scorer.delta(schedule, proposal), "");
  }
}
