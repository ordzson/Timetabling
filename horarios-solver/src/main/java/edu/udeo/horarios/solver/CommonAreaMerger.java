package edu.udeo.horarios.solver;

import edu.udeo.horarios.domain.SchedulableSession;
import edu.udeo.horarios.domain.SessionType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CommonAreaMerger {
  public List<SchedulableSession> merge(List<SessionDraft> drafts) {
    Map<Key, List<SessionDraft>> common = new HashMap<>();
    List<SchedulableSession> sessions = new ArrayList<>();

    for (SessionDraft draft : drafts) {
      if (draft.course().commonArea()) {
        common.computeIfAbsent(Key.from(draft), ignored -> new ArrayList<>()).add(draft);
      } else {
        sessions.add(draft.toSession(draft.id(), draft.cohort().id()));
      }
    }

    common.values().stream()
        .sorted(Comparator.comparingLong(group -> group.getFirst().id()))
        .forEach(group -> sessions.add(merged(group)));

    return sessions.stream().sorted(Comparator.comparingLong(SchedulableSession::id)).toList();
  }

  private static SchedulableSession merged(List<SessionDraft> group) {
    SessionDraft first = group.getFirst();
    List<Long> cohortIds =
        group.stream().map(draft -> draft.cohort().id()).distinct().sorted().toList();
    return new SchedulableSession(
        first.id(), first.course().id(), cohortIds, SessionType.CLASS, first.course().weeklyMinutes(), false);
  }

  private record Key(long courseId, String curriculumCode, int termNumber, String journeyCode) {
    static Key from(SessionDraft draft) {
      return new Key(
          draft.course().id(),
          draft.cohort().curriculumCode(),
          draft.cohort().termNumber(),
          draft.cohort().journeyCode());
    }
  }
}
