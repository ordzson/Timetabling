package edu.udeo.horarios.solver;

public record ManualEditCommand(
    long baseRunId,
    long sessionId,
    Long targetTeacherId,
    Long targetRoomId,
    Long targetTimeBlockId,
    String clientRequestId) {
  public ManualEditCommand {
    if (baseRunId <= 0 || sessionId <= 0) {
      throw new IllegalArgumentException("ids must be positive");
    }
    if (targetTeacherId == null && targetRoomId == null && targetTimeBlockId == null) {
      throw new IllegalArgumentException("at least one target must be present");
    }
    clientRequestId = clientRequestId == null || clientRequestId.isBlank() ? null : clientRequestId;
  }
}
