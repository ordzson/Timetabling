package edu.udeo.horarios.solver;

import edu.udeo.horarios.domain.Course;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class DifficultyRanker {
  public List<CandidateSpace> rank(SchedulingProblem problem, List<CandidateSpace> spaces) {
    Map<Long, Course> courses =
        problem.curriculumCourses().stream()
            .map(CurriculumCourse::course)
            .collect(Collectors.toMap(Course::id, Function.identity(), (left, right) -> left));

    return spaces.stream()
        .sorted(
            Comparator.comparingInt((CandidateSpace space) -> space.candidates().size())
                .thenComparingInt(CandidateSpace::possibleTeacherCount)
                .thenComparingInt(CandidateSpace::compatibleRoomCount)
                .thenComparingInt(CandidateSpace::validStartCount)
                .thenComparing(
                    space -> !courses.get(space.session().courseId()).commonArea())
                .thenComparing(
                    space -> !courses.get(space.session().courseId()).requiresLab())
                .thenComparing(
                    Comparator.comparingInt(
                            (CandidateSpace space) -> space.session().cohortIds().size())
                        .reversed())
                .thenComparing(
                    Comparator.comparingInt(
                            (CandidateSpace space) -> space.session().durationMinutes())
                        .reversed())
                .thenComparingLong(space -> space.session().id()))
        .toList();
  }
}
