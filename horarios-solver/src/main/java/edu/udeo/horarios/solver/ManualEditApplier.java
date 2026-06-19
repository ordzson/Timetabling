package edu.udeo.horarios.solver;

import edu.udeo.horarios.domain.Assignment;
import edu.udeo.horarios.domain.TimeRange;
import java.util.Set;

public final class ManualEditApplier {
  public Assignment apply(
      Schedule schedule,
      ManualEditCommand command,
      TimeRange targetTime,
      Set<Long> sessionGroupIds) {
    Assignment current = schedule.assignment(command.sessionId()).orElseThrow();
    if (command.targetTeacherId() != null && sessionGroupIds.size() > 1) {
      throw new IllegalArgumentException("SESSION_GROUP_TEACHER_CHANGE_REQUIRES_GROUP");
    }
    Assignment replacement =
        new Assignment(
            current.sessionId(),
            command.targetTeacherId() == null ? current.teacherId() : command.targetTeacherId(),
            command.targetRoomId() == null ? current.roomId() : command.targetRoomId(),
            current.cohortIds(),
            targetTime == null ? current.time() : targetTime);
    schedule.moveAssignment(command.sessionId(), replacement);
    return replacement;
  }
}
