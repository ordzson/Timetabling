package edu.udeo.horarios.solver;

public record MoveEvaluation(boolean valid, long scoreDelta, String rejectionReason) {
  public static MoveEvaluation rejected(String reason) {
    return new MoveEvaluation(false, 0, reason);
  }
}
