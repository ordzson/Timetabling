package edu.udeo.horarios.solver;

public final class MoveApplier {
  public boolean apply(Schedule schedule, MoveProposal proposal, MoveEvaluation evaluation) {
    if (!evaluation.valid()) {
      return false;
    }
    if (proposal.current() == null) {
      schedule.addAssignment(proposal.replacement());
    } else {
      schedule.moveAssignment(proposal.current().sessionId(), proposal.replacement());
    }
    return true;
  }
}
