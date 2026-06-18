package edu.udeo.horarios.api.scheduling;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.udeo.horarios.domain.Assignment;
import edu.udeo.horarios.domain.Cohort;
import edu.udeo.horarios.domain.Course;
import edu.udeo.horarios.domain.Room;
import edu.udeo.horarios.domain.RoomCoordinate;
import edu.udeo.horarios.domain.RoomType;
import edu.udeo.horarios.domain.SchedulableSession;
import edu.udeo.horarios.domain.Teacher;
import edu.udeo.horarios.domain.TimeRange;
import edu.udeo.horarios.solver.ConstructiveScheduler;
import edu.udeo.horarios.solver.CurriculumCourse;
import edu.udeo.horarios.solver.FixedBreak;
import edu.udeo.horarios.solver.PreValidationIssue;
import edu.udeo.horarios.solver.ProblemPreValidator;
import edu.udeo.horarios.solver.ScheduleResult;
import edu.udeo.horarios.solver.SchedulingProblem;
import edu.udeo.horarios.solver.SessionFactory;
import edu.udeo.horarios.solver.UnassignedSession;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ScheduleGenerationService {
  private static final String ENGINE_VERSION = "solver-0.1.0";
  private static final int DAY_MINUTES = 24 * 60;

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final ProblemPreValidator validator = new ProblemPreValidator();
  private final ConstructiveScheduler scheduler = new ConstructiveScheduler();
  private final SessionFactory sessionFactory = new SessionFactory();

  ScheduleGenerationService(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Transactional
  ValidationResponse validate(long planId) {
    requirePlan(planId);
    ProblemData data = problem(planId);
    List<PreValidationIssue> issues = validator.validate(data.problem());
    jdbc.update("delete from pre_validation_issue where plan_id = ?", planId);
    List<ValidationIssueResponse> saved = issues.stream().map(issue -> saveIssue(planId, issue)).toList();
    String status = issues.stream().anyMatch(issue -> issue.severity().name().equals("ERROR")) ? "INVALID_INPUT" : validStatus(planId);
    jdbc.update("update schedule_plan set status = ?::plan_status, updated_at = now() where id = ?", status, planId);
    return new ValidationResponse(planId, status, status.equals("INVALID_INPUT"), saved);
  }

  @Transactional
  GenerationResponse generate(long planId, GenerationRequest request) {
    String previousStatus = requirePlan(planId);
    ProblemData data = problem(planId);
    List<PreValidationIssue> issues = validator.validate(data.problem());
    if (issues.stream().anyMatch(issue -> issue.severity().name().equals("ERROR"))) {
      jdbc.update("delete from pre_validation_issue where plan_id = ?", planId);
      issues.forEach(issue -> saveIssue(planId, issue));
      jdbc.update("update schedule_plan set status = 'INVALID_INPUT'::plan_status, updated_at = now() where id = ?", planId);
      throw new ScheduleApiException("IMPORT_HAS_ERRORS", "El plan tiene errores de entrada.", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    long seed = request.seed() == null ? UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE : request.seed();
    int runNumber = nextRunNumber(planId);
    long runId = 0;
    try {
      jdbc.update("update schedule_plan set status = 'GENERATING'::plan_status, updated_at = now() where id = ?", planId);
      runId = createRun(planId, runNumber, request, seed, snapshot(data.problem()));
      ScheduleResult result = scheduler.schedule(data.problem());
      Map<Long, Long> sessions = ensureSessions(planId, data, result);
      persistAssignments(planId, runId, data, sessions, result);
      int unassigned = result.unassignedSessions().size();
      String runStatus = unassigned == 0 ? "COMPLETED" : "COMPLETED_WITH_CONFLICTS";
      String planStatus = unassigned == 0 ? "GENERATED" : "GENERATED_WITH_CONFLICTS";
      ScoreResponse score = ScoreResponse.hardViolations(unassigned);
      finishRun(runId, runStatus, score, outputSnapshot(result, sessions));
      jdbc.update("update schedule_plan set status = ?::plan_status, updated_at = now() where id = ?", planStatus, planId);
      RunTimes times = runTimes(runId);
      return new GenerationResponse(
          planId,
          runId,
          runNumber,
          runStatus,
          planStatus,
          seed,
          ENGINE_VERSION,
          score,
          result.schedule().assignments().size(),
          unassigned,
          times.startedAt(),
          times.finishedAt());
    } catch (RuntimeException ex) {
      if (runId != 0) {
        jdbc.update(
            """
            update schedule_run
            set status = 'FAILED'::schedule_run_status,
                finished_at = now(),
                output_snapshot = ?::jsonb
            where id = ?
            """,
            toJson(Map.of("error", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage())),
            runId);
      }
      jdbc.update("update schedule_plan set status = ?::plan_status, updated_at = now() where id = ?", previousStatus, planId);
      throw new ScheduleApiException("SOLVER_FAILED", "El solver fallo.", HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  ResultResponse result(long planId, Long requestedRunId) {
    long runId = requestedRunId == null ? latestRun(planId) : requestedRunId;
    String planStatus = requirePlan(planId);
    ScoreResponse score = score(runId);
    List<AssignmentResponse> assigned =
        jdbc.query(
            """
            select a.id, a.session_id, s.course_id, c.code course_code, c.name course_name,
                   a.teacher_id, t.full_name teacher_name, a.room_id, r.code room_code,
                   coalesce(jsonb_agg(sc.cohort_id order by sc.cohort_id) filter (where sc.cohort_id is not null), '[]'::jsonb)::text cohort_ids,
                   tb.day_of_week, tb.block_index, a.duration_blocks, a.status::text, a.pinned
            from schedule_assignment a
            join schedule_session s on s.id = a.session_id
            join course c on c.id = s.course_id
            left join teacher t on t.id = a.teacher_id
            left join room r on r.id = a.room_id
            left join time_block tb on tb.id = a.start_time_block_id
            left join schedule_session_cohort sc on sc.session_id = s.id
            where a.plan_id = ? and a.run_id = ? and a.status = 'ASSIGNED'::assignment_status
            group by a.id, s.course_id, c.code, c.name, t.full_name, r.code, tb.day_of_week, tb.block_index
            order by tb.day_of_week, tb.block_index, c.code
            """,
            (rs, rowNum) -> assignment(rs),
            planId,
            runId);
    List<UnassignedResponse> unassigned =
        jdbc.query(
            """
            select a.session_id, s.course_id, c.code course_code, a.unassigned_reason
            from schedule_assignment a
            join schedule_session s on s.id = a.session_id
            join course c on c.id = s.course_id
            where a.plan_id = ? and a.run_id = ? and a.status = 'UNASSIGNED'::assignment_status
            order by c.code, a.session_id
            """,
            (rs, rowNum) ->
                new UnassignedResponse(
                    rs.getLong("session_id"),
                    rs.getLong("course_id"),
                    rs.getString("course_code"),
                    rs.getString("unassigned_reason")),
            planId,
            runId);
    return new ResultResponse(planId, runId, planStatus, score, assigned, unassigned);
  }

  ViolationsResponse violations(long planId, Long requestedRunId, String severity) {
    long runId = requestedRunId == null ? latestRun(planId) : requestedRunId;
    List<Object> args = new java.util.ArrayList<>(List.of(runId));
    String filter = "";
    if (severity != null && !severity.isBlank()) {
      filter = " and severity = ?::issue_severity";
      args.add(severity);
    }
    return new ViolationsResponse(
        jdbc.query(
            """
            select id, severity::text, code, message, affected_entities::text, cost
            from schedule_violation
            where run_id = ?
            """
                + filter
                + " order by id",
            (rs, rowNum) ->
                new ViolationResponse(
                    rs.getLong("id"),
                    rs.getString("severity"),
                    rs.getString("code"),
                    rs.getString("message"),
                    readList(rs.getString("affected_entities")),
                    rs.getBigDecimal("cost")),
            args.toArray()));
  }

  private ProblemData problem(long planId) {
    int blockMinutes = blockMinutes();
    List<Cohort> cohorts =
        jdbc.query(
            """
            select co.id, co.career_id, cu.code curriculum_code, co.semester_number,
                   co.section, j.code journey_code, co.expected_students
            from cohort co
            join curriculum cu on cu.id = co.curriculum_id
            join journey j on j.id = co.journey_id
            where co.active
            order by co.id
            """,
            (rs, rowNum) ->
                new Cohort(
                    rs.getLong("id"),
                    rs.getLong("career_id"),
                    rs.getString("curriculum_code"),
                    rs.getInt("semester_number"),
                    rs.getString("section"),
                    rs.getString("journey_code"),
                    rs.getInt("expected_students")));
    Set<Long> commonCourseIds =
        Set.copyOf(jdbc.queryForList("select distinct course_id from common_area_rule where active", Long.class));
    List<CurriculumCourse> courses =
        jdbc.query(
            """
            select cu.code curriculum_code, cc.semester_number, c.id, c.code, c.name,
                   c.requires_lab, c.weekly_blocks_min, c.weekly_blocks_max
            from curriculum_course cc
            join curriculum cu on cu.id = cc.curriculum_id
            join course c on c.id = cc.course_id
            where cu.is_active
            order by cu.code, cc.semester_number, c.id
            """,
            (rs, rowNum) ->
                new CurriculumCourse(
                    rs.getString("curriculum_code"),
                    rs.getInt("semester_number"),
                    new Course(
                        rs.getLong("id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getInt("weekly_blocks_min") * blockMinutes,
                        rs.getBoolean("requires_lab"),
                        commonCourseIds.contains(rs.getLong("id")),
                        rs.getInt("weekly_blocks_min"),
                        rs.getInt("weekly_blocks_max"))));
    Map<Long, Set<Long>> teacherCourses = teacherCourses();
    List<Teacher> teachers =
        jdbc.query(
            """
            select id, full_name, priority, min_courses, max_courses
            from teacher
            where active
            order by id
            """,
            (rs, rowNum) ->
                new Teacher(
                    rs.getLong("id"),
                    rs.getString("full_name"),
                    rs.getInt("priority"),
                    rs.getInt("min_courses"),
                    rs.getInt("max_courses"),
                    teacherCourses.getOrDefault(rs.getLong("id"), Set.of())));
    List<Room> rooms =
        jdbc.query(
            "select id, code, capacity, type::text, floor, number from room where active order by id",
            (rs, rowNum) ->
                new Room(
                    rs.getLong("id"),
                    rs.getString("code"),
                    rs.getInt("capacity"),
                    RoomType.valueOf(rs.getString("type")),
                    new RoomCoordinate(rs.getInt("floor"), rs.getInt("number"))));
    List<FixedBreak> fixedBreaks =
        jdbc.query(
            """
            select fb.day_of_week, fb.start_block, fb.duration_blocks, j.block_minutes, j.start_time
            from fixed_break fb
            join journey j on j.id = fb.journey_id
            order by fb.id
            """,
            (rs, rowNum) ->
                new FixedBreak(
                    new TimeRange(
                        startMinute(rs.getInt("day_of_week"), rs.getTime("start_time").toLocalTime(), rs.getInt("start_block"), rs.getInt("block_minutes")),
                        rs.getInt("duration_blocks") * rs.getInt("block_minutes"))));
    return new ProblemData(planId, blockMinutes, new SchedulingProblem(cohorts, courses, teachers, rooms, fixedBreaks));
  }

  private Map<Long, Set<Long>> teacherCourses() {
    Map<Long, Set<Long>> result = new HashMap<>();
    jdbc.query(
        "select teacher_id, course_id from teacher_course",
        (RowCallbackHandler)
            rs -> result.computeIfAbsent(rs.getLong("teacher_id"), ignored -> new java.util.HashSet<>()).add(rs.getLong("course_id")));
    return result.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
  }

  private Map<Long, Long> ensureSessions(long planId, ProblemData data, ScheduleResult result) {
    Map<Long, Long> ids = new LinkedHashMap<>();
    Map<Long, SchedulableSession> allSessions = new LinkedHashMap<>();
    sessionFactory.createSessions(data.problem()).forEach(session -> allSessions.put(session.id(), session));
    result.unassignedSessions().forEach(unassigned -> allSessions.put(unassigned.session().id(), unassigned.session()));
    for (SchedulableSession session : allSessions.values()) {
      long groupId =
          jdbc.queryForObject(
              "insert into schedule_session_group(plan_id, course_id, continuity_teacher) values (?,?,true) returning id",
              Long.class,
              planId,
              session.courseId());
      long sessionId =
          jdbc.queryForObject(
              """
              insert into schedule_session(plan_id, session_group_id, course_id, duration_blocks, weekly_index, is_common_area)
              values (?,?,?,?,1,false) returning id
              """,
              Long.class,
              planId,
              groupId,
              session.courseId(),
              Math.max(1, session.durationMinutes() / data.blockMinutes()));
      for (Long cohortId : session.cohortIds()) {
        jdbc.update("insert into schedule_session_cohort(session_id, cohort_id) values (?,?)", sessionId, cohortId);
      }
      ids.put(session.id(), sessionId);
    }
    return ids;
  }

  private void persistAssignments(
      long planId, long runId, ProblemData data, Map<Long, Long> sessionIds, ScheduleResult result) {
    for (Assignment assignment : result.schedule().assignments()) {
      long dbSessionId = sessionIds.get(assignment.sessionId());
      long timeBlockId = timeBlockId(assignment.cohortIds().getFirst(), assignment.time().startMinuteOfWeek());
      jdbc.update(
          """
          insert into schedule_assignment(plan_id,run_id,session_id,teacher_id,room_id,start_time_block_id,duration_blocks,status,pinned)
          values (?,?,?,?,?,?,?,'ASSIGNED'::assignment_status,false)
          """,
          planId,
          runId,
          dbSessionId,
          assignment.teacherId(),
          assignment.roomId(),
          timeBlockId,
          Math.max(1, assignment.time().durationMinutes() / data.blockMinutes()));
    }
    for (UnassignedSession unassigned : result.unassignedSessions()) {
      long dbSessionId = sessionIds.get(unassigned.session().id());
      jdbc.update(
          """
          insert into schedule_assignment(plan_id,run_id,session_id,status,unassigned_reason)
          values (?,?,?,'UNASSIGNED'::assignment_status,?)
          """,
          planId,
          runId,
          dbSessionId,
          unassigned.reason());
      jdbc.update(
          """
          insert into schedule_violation(run_id,severity,code,message,affected_entities,cost)
          values (?,'ERROR'::issue_severity,?,?,?::jsonb,100000)
          """,
          runId,
          unassigned.reason(),
          "No se pudo asignar la sesion.",
          toJson(List.of(Map.of("type", "session", "id", dbSessionId))));
    }
  }

  private long timeBlockId(long cohortId, int startMinuteOfWeek) {
    JourneyBlock block =
        jdbc.queryForObject(
            """
            select j.id, j.block_minutes, j.start_time
            from cohort c
            join journey j on j.id = c.journey_id
            where c.id = ?
            """,
            (rs, rowNum) -> new JourneyBlock(rs.getLong("id"), rs.getInt("block_minutes"), rs.getTime("start_time").toLocalTime()),
            cohortId);
    int dayOfWeek = startMinuteOfWeek / DAY_MINUTES + 1;
    int minuteOfDay = startMinuteOfWeek % DAY_MINUTES;
    int start = block.startTime().toSecondOfDay() / 60;
    int blockIndex = Math.max(0, (minuteOfDay - start) / block.blockMinutes());
    return jdbc.queryForObject(
        """
        insert into time_block(journey_id, day_of_week, block_index, start_time, end_time)
        values (?,?,?,?,?)
        on conflict (journey_id, day_of_week, block_index) do update set start_time = excluded.start_time
        returning id
        """,
        Long.class,
        block.id(),
        dayOfWeek,
        blockIndex,
        LocalTime.of(minuteOfDay / 60, minuteOfDay % 60),
        LocalTime.of((minuteOfDay + block.blockMinutes()) / 60, (minuteOfDay + block.blockMinutes()) % 60));
  }

  private ValidationIssueResponse saveIssue(long planId, PreValidationIssue issue) {
    Long id =
        jdbc.queryForObject(
            """
            insert into pre_validation_issue(plan_id,severity,code,entity_type,entity_id,message,suggested_action,source)
            values (?,?::issue_severity,?,?,?,?,?, '{}'::jsonb)
            returning id
            """,
            Long.class,
            planId,
            issue.severity().name(),
            issue.code(),
            issue.entityType(),
            issue.entityId(),
            issue.message(),
            suggestedAction(issue.code()));
    return new ValidationIssueResponse(
        id, issue.severity().name(), issue.code(), issue.entityType(), issue.entityId(), issue.message(), suggestedAction(issue.code()), Map.of());
  }

  private String requirePlan(long planId) {
    List<String> statuses = jdbc.queryForList("select status::text from schedule_plan where id = ?", String.class, planId);
    if (statuses.isEmpty()) {
      throw new ScheduleApiException("RESOURCE_NOT_FOUND", "El recurso no existe.", HttpStatus.NOT_FOUND);
    }
    return statuses.getFirst();
  }

  private String validStatus(long planId) {
    String current = requirePlan(planId);
    return current.equals("INVALID_INPUT") ? "DRAFT" : current;
  }

  private int nextRunNumber(long planId) {
    Integer value = jdbc.queryForObject("select coalesce(max(run_number),0) + 1 from schedule_run where plan_id = ?", Integer.class, planId);
    return Objects.requireNonNullElse(value, 1);
  }

  private long createRun(long planId, int runNumber, GenerationRequest request, long seed, Map<String, Object> input) {
    return jdbc.queryForObject(
        """
        insert into schedule_run(plan_id,run_number,solver_mode,seed,engine_version,status,config,input_snapshot)
        values (?,?,?,? ,?,'RUNNING'::schedule_run_status,?::jsonb,?::jsonb)
        returning id
        """,
        Long.class,
        planId,
        runNumber,
        request.solverMode(),
        seed,
        ENGINE_VERSION,
        toJson(request),
        toJson(input));
  }

  private void finishRun(long runId, String status, ScoreResponse score, Map<String, Object> output) {
    jdbc.update(
        """
        update schedule_run
        set status = ?::schedule_run_status,
            finished_at = now(),
            output_snapshot = ?::jsonb,
            score_total = ?,
            score_breakdown = ?::jsonb
        where id = ?
        """,
        status,
        toJson(output),
        score.total(),
        toJson(score),
        runId);
  }

  private long latestRun(long planId) {
    List<Long> ids =
        jdbc.queryForList(
            """
            select id
            from schedule_run
            where plan_id = ? and status in ('COMPLETED'::schedule_run_status, 'COMPLETED_WITH_CONFLICTS'::schedule_run_status)
            order by finished_at desc nulls last, id desc
            limit 1
            """,
            Long.class,
            planId);
    if (ids.isEmpty()) {
      throw new ScheduleApiException("RESOURCE_NOT_FOUND", "El recurso no existe.", HttpStatus.NOT_FOUND);
    }
    return ids.getFirst();
  }

  private ScoreResponse score(long runId) {
    return jdbc.queryForObject(
        "select coalesce((score_breakdown->>'total')::int,0), coalesce((score_breakdown->>'hardViolations')::int,0) from schedule_run where id = ?",
        (rs, rowNum) -> ScoreResponse.hardViolations(rs.getInt(2)),
        runId);
  }

  private RunTimes runTimes(long runId) {
    return jdbc.queryForObject(
        "select started_at, finished_at from schedule_run where id = ?",
        (rs, rowNum) -> new RunTimes(instant(rs, "started_at"), instant(rs, "finished_at")),
        runId);
  }

  private int blockMinutes() {
    Integer value = jdbc.queryForObject("select coalesce(min(block_minutes),45) from journey", Integer.class);
    return Objects.requireNonNullElse(value, 45);
  }

  private static int startMinute(int dayOfWeek, LocalTime startTime, int startBlock, int blockMinutes) {
    return (dayOfWeek - 1) * DAY_MINUTES + startTime.toSecondOfDay() / 60 + startBlock * blockMinutes;
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    Timestamp value = rs.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }

  private AssignmentResponse assignment(ResultSet rs) throws SQLException {
    return new AssignmentResponse(
        rs.getLong("id"),
        rs.getLong("session_id"),
        rs.getLong("course_id"),
        rs.getString("course_code"),
        rs.getString("course_name"),
        nullableLong(rs, "teacher_id"),
        rs.getString("teacher_name"),
        nullableLong(rs, "room_id"),
        rs.getString("room_code"),
        readLongs(rs.getString("cohort_ids")),
        rs.getInt("day_of_week"),
        rs.getInt("block_index"),
        nullableInt(rs, "duration_blocks"),
        rs.getString("status"),
        rs.getBoolean("pinned"));
  }

  private static Long nullableLong(ResultSet rs, String column) throws SQLException {
    long value = rs.getLong(column);
    return rs.wasNull() ? null : value;
  }

  private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
    int value = rs.getInt(column);
    return rs.wasNull() ? null : value;
  }

  private Map<String, Object> snapshot(SchedulingProblem problem) {
    return Map.of(
        "cohorts", problem.cohorts().size(),
        "curriculumCourses", problem.curriculumCourses().size(),
        "teachers", problem.teachers().size(),
        "rooms", problem.rooms().size(),
        "fixedBreaks", problem.fixedBreaks().size());
  }

  private Map<String, Object> outputSnapshot(ScheduleResult result, Map<Long, Long> sessions) {
    return Map.of(
        "assignedCount", result.schedule().assignments().size(),
        "unassignedCount", result.unassignedSessions().size(),
        "sessionMap", sessions);
  }

  private String suggestedAction(String code) {
    return switch (code) {
      case "COURSE_WITHOUT_TEACHER" -> "Asignar al menos un docente al curso.";
      case "LAB_WITHOUT_ROOM", "SESSION_WITHOUT_COMPATIBLE_ROOM" -> "Crear o habilitar un aula compatible.";
      case "DUPLICATE_CURRICULUM_COURSE" -> "Eliminar el curso duplicado del pensum.";
      default -> "Corregir la entrada.";
    };
  }

  private String toJson(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception ex) {
      throw new IllegalStateException("json serialization failed", ex);
    }
  }

  private List<Long> readLongs(String value) {
    try {
      return json.readValue(value, new TypeReference<List<Long>>() {});
    } catch (Exception ex) {
      throw new IllegalStateException("json parse failed", ex);
    }
  }

  private List<Map<String, Object>> readList(String value) {
    try {
      return json.readValue(value, new TypeReference<List<Map<String, Object>>>() {});
    } catch (Exception ex) {
      throw new IllegalStateException("json parse failed", ex);
    }
  }

  private record ProblemData(long planId, int blockMinutes, SchedulingProblem problem) {
  }

  private record JourneyBlock(long id, int blockMinutes, LocalTime startTime) {
  }

  private record RunTimes(Instant startedAt, Instant finishedAt) {
  }
}
