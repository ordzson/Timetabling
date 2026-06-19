package edu.udeo.horarios.api.scheduling;

import java.time.Instant;
import java.util.List;
import java.util.Map;

record GenerationRequest(
    String solverMode,
    Long seed,
    Integer maxIterations,
    Integer timeLimitSeconds,
    Map<String, Number> weights) {
  GenerationRequest {
    solverMode = solverMode == null || solverMode.isBlank() ? "NORMAL" : solverMode;
    weights = weights == null ? Map.of() : Map.copyOf(weights);
  }
}

record ValidationResponse(
    long planId, String status, boolean hasBlockingErrors, List<ValidationIssueResponse> issues) {
}

record ValidationIssueResponse(
    Long id,
    String severity,
    String code,
    String entityType,
    Long entityId,
    String message,
    String suggestedAction,
    Map<String, Object> source) {
}

record GenerationResponse(
    long planId,
    long runId,
    int runNumber,
    String status,
    String planStatus,
    long seed,
    String engineVersion,
    ScoreResponse score,
    int assignedCount,
    int unassignedCount,
    Instant startedAt,
    Instant finishedAt) {
}

record ScoreResponse(
    int total,
    int hardViolations,
    int teacherUnavailableBlocks,
    int nonContiguousCourseBlocks,
    int gapsNotAtEnd,
    int walkingDistance,
    int teacherLoadDeviation) {
  static ScoreResponse hardViolations(int count) {
    return new ScoreResponse(count * 100_000, count, 0, 0, 0, 0, 0);
  }
}

record ResultResponse(
    long planId,
    long runId,
    String planStatus,
    ScoreResponse score,
    List<AssignmentResponse> assignments,
    List<UnassignedResponse> unassigned) {
}

record AssignmentResponse(
    long id,
    long sessionId,
    long courseId,
    String courseCode,
    String courseName,
    Long teacherId,
    String teacherName,
    Long roomId,
    String roomCode,
    List<Long> cohortIds,
    int dayOfWeek,
    int startBlock,
    Integer durationBlocks,
    String status,
    boolean pinned) {
}

record UnassignedResponse(long sessionId, long courseId, String courseCode, String reason) {
}

record ViolationsResponse(List<ViolationResponse> items) {
}

record ViolationResponse(
    long id,
    String severity,
    String code,
    String message,
    List<Map<String, Object>> affectedEntities,
    Number cost) {
}

record ManualEditRequest(
    String clientRequestId,
    long baseRunId,
    long sessionId,
    Long targetTeacherId,
    Long targetRoomId,
    Long targetTimeBlockId) {
}

record ManualEditResponse(
    long manualEditId,
    String status,
    Long resultRunId,
    List<Long> pinnedSessionIds,
    List<Long> neighborhoodSessionIds,
    List<Long> movedSessionIds,
    List<Map<String, Object>> remainingViolations,
    int scoreBefore,
    int scoreAfter,
    long repairTimeMs) {
}

record SubstitutionRequest(
    long assignmentId,
    long substituteTeacherId,
    Instant startsAt,
    Instant endsAt,
    boolean isPermanent,
    String reason) {
}

record SubstitutionResponse(
    long id,
    long assignmentId,
    long originalTeacherId,
    long substituteTeacherId,
    Instant startsAt,
    Instant endsAt,
    boolean isPermanent,
    String reason) {
}

record SubstitutionListResponse(List<SubstitutionResponse> items) {
}
