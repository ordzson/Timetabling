package edu.udeo.horarios.solver;

import edu.udeo.horarios.domain.Assignment;

public record MoveProposal(Assignment current, Assignment replacement, String rejectionReason) {
  public MoveProposal {
    if (replacement == null && (rejectionReason == null || rejectionReason.isBlank())) {
      throw new IllegalArgumentException("replacement or rejectionReason required");
    }
    rejectionReason = rejectionReason == null ? "" : rejectionReason;
  }

  public static MoveProposal rejected(String reason) {
    return new MoveProposal(null, null, reason);
  }

  public boolean applicable() {
    return replacement != null && rejectionReason.isBlank();
  }
}
