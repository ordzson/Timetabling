package edu.udeo.horarios.solver;

import edu.udeo.horarios.domain.Cohort;
import edu.udeo.horarios.domain.Course;
import edu.udeo.horarios.domain.SchedulableSession;
import edu.udeo.horarios.domain.SessionType;
import java.util.List;

record SessionDraft(long id, Course course, Cohort cohort) {
  SchedulableSession toSession(long sessionId, long cohortId) {
    return new SchedulableSession(
        sessionId, course.id(), List.of(cohortId), SessionType.CLASS, course.weeklyMinutes(), false);
  }
}
