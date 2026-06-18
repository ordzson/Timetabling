package edu.udeo.horarios.solver;

import edu.udeo.horarios.domain.Cohort;
import edu.udeo.horarios.domain.SchedulableSession;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SessionFactory {
  private final CommonAreaMerger commonAreaMerger;

  public SessionFactory() {
    this(new CommonAreaMerger());
  }

  public SessionFactory(CommonAreaMerger commonAreaMerger) {
    this.commonAreaMerger = commonAreaMerger;
  }

  public List<SchedulableSession> createSessions(SchedulingProblem problem) {
    List<SessionDraft> drafts = new ArrayList<>();
    long nextId = 1;

    for (Cohort cohort : sortedCohorts(problem)) {
      for (CurriculumCourse curriculumCourse : sortedCourses(problem)) {
        if (matches(cohort, curriculumCourse)) {
          drafts.add(new SessionDraft(nextId++, curriculumCourse.course(), cohort));
        }
      }
    }

    return commonAreaMerger.merge(drafts);
  }

  private static boolean matches(Cohort cohort, CurriculumCourse curriculumCourse) {
    return cohort.curriculumCode().equals(curriculumCourse.curriculumCode())
        && cohort.termNumber() == curriculumCourse.termNumber();
  }

  private static List<Cohort> sortedCohorts(SchedulingProblem problem) {
    return problem.cohorts().stream().sorted(Comparator.comparingLong(Cohort::id)).toList();
  }

  private static List<CurriculumCourse> sortedCourses(SchedulingProblem problem) {
    return problem.curriculumCourses().stream()
        .sorted(
            Comparator.comparing(CurriculumCourse::curriculumCode)
                .thenComparingInt(CurriculumCourse::termNumber)
                .thenComparingLong(curriculumCourse -> curriculumCourse.course().id()))
        .toList();
  }
}
