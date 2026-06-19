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
import edu.udeo.horarios.domain.SessionType;
import edu.udeo.horarios.domain.Teacher;
import edu.udeo.horarios.domain.TimeRange;
import edu.udeo.horarios.solver.CurriculumCourse;
import edu.udeo.horarios.solver.FixedBreak;
import edu.udeo.horarios.solver.ManualEditCommand;
import edu.udeo.horarios.solver.NeighborhoodRepairer;
import edu.udeo.horarios.solver.RepairResult;
import edu.udeo.horarios.solver.RepairViolation;
import edu.udeo.horarios.solver.Schedule;
import edu.udeo.horarios.solver.SchedulingProblem;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ManualEditService {
  private static final String ENGINE_VERSION = "solver-0.1.0";
  private static final int DAY_MINUTES = 24 * 60;

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final NeighborhoodRepairer repairer = new NeighborhoodRepairer();

  ManualEditService(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Transactional
  ManualEditResponse apply(long planId, ManualEditRequest request) {
    if (request.clientRequestId() != null && !request.clientRequestId().isBlank()) {
      ManualEditResponse existing = existing(planId, request.clientRequestId());
      if (existing != null) {
        return existing;
      }
    }
    requireEditable(planId);
    requireRun(planId, request.baseRunId());
    if (!exists("schedule_session", planId, request.sessionId())) {
      throw rejectedInput(planId, request, "session not found");
    }
    if (request.targetTeacherId() != null && !existsById("teacher", request.targetTeacherId())) {
      throw rejectedInput(planId, request, "teacher not found");
    }
    if (request.targetRoomId() != null && !existsById("room", request.targetRoomId())) {
      throw rejectedInput(planId, request, "room not found");
    }
    TimeRange targetTime = request.targetTimeBlockId() == null ? null : targetTime(request);

    ManualEditCommand command =
        new ManualEditCommand(
            request.baseRunId(),
            request.sessionId(),
            request.targetTeacherId(),
            request.targetRoomId(),
            request.targetTimeBlockId(),
            request.clientRequestId());
    BaseSchedule base = baseSchedule(planId, request.baseRunId());
    Set<Long> groupIds = groupSessionIds(planId, request.sessionId());
    RepairResult result;
    try {
      result =
          repairer.repair(
              base.schedule(),
              problem(),
              base.sessions(),
              command,
              targetTime,
              base.pinnedSessionIds(),
              groupIds);
    } catch (IllegalArgumentException ex) {
      throw rejectedInput(planId, request, ex.getMessage());
    }

    long runId = createRun(planId, nextRunNumber(planId), request, result);
    persistAssignments(planId, runId, result.schedule(), base, result.pinnedSessionIds());
    persistViolations(runId, result.remainingViolations());
    int scoreAfter = result.remainingViolations().size() * 100_000;
    finishRun(runId, result.remainingViolations().isEmpty() ? "COMPLETED" : "COMPLETED_WITH_CONFLICTS", scoreAfter);
    long editId = insertManualEdit(planId, request, result, runId, scoreAfter);
    jdbc.update(
        "update schedule_plan set status = ?::plan_status, updated_at = now() where id = ?",
        result.remainingViolations().isEmpty() ? "GENERATED" : "GENERATED_WITH_CONFLICTS",
        planId);
    return response(editId, result, runId, scoreAfter);
  }

  private void requireEditable(long planId) {
    String status =
        jdbc.queryForObject("select status::text from schedule_plan where id = ?", String.class, planId);
    if (status == null) {
      throw new ScheduleApiException("RESOURCE_NOT_FOUND", "El recurso no existe.", HttpStatus.NOT_FOUND);
    }
    if (!Set.of("APPROVED", "GENERATED", "GENERATED_WITH_CONFLICTS").contains(status)) {
      throw new ScheduleApiException(
          "MANUAL_EDIT_REJECTED_BY_STATE", "El plan no permite ediciones manuales.", HttpStatus.CONFLICT);
    }
  }

  private void requireRun(long planId, long runId) {
    Integer count =
        jdbc.queryForObject(
            "select count(*) from schedule_run where id = ? and plan_id = ?", Integer.class, runId, planId);
    if (count == null || count == 0) {
      throw new ScheduleApiException(
          "MANUAL_EDIT_REJECTED_BY_INPUT", "La corrida base no existe.", HttpStatus.BAD_REQUEST);
    }
  }

  private RuntimeException rejectedInput(long planId, ManualEditRequest request, String message) {
    insertRejected(planId, request, "REJECTED_BY_INPUT", message);
    return new ScheduleApiException("MANUAL_EDIT_REJECTED_BY_INPUT", message, HttpStatus.BAD_REQUEST);
  }

  private ManualEditResponse existing(long planId, String clientRequestId) {
    List<ManualEditResponse> responses =
        jdbc.query(
            """
            select id, status::text, result_run_id, pinned_session_ids::text, neighborhood_session_ids::text,
                   moved_session_ids::text, remaining_violations::text, coalesce(score_before,0)::int score_before,
                   coalesce(score_after,0)::int score_after, coalesce(repair_time_ms,0) repair_time_ms
            from manual_edit
            where plan_id = ? and client_request_id = ?
            """,
            (rs, rowNum) ->
                new ManualEditResponse(
                    rs.getLong("id"),
                    rs.getString("status"),
                    nullableLong(rs, "result_run_id"),
                    readLongs(rs.getString("pinned_session_ids")),
                    readLongs(rs.getString("neighborhood_session_ids")),
                    readLongs(rs.getString("moved_session_ids")),
                    readViolations(rs.getString("remaining_violations")),
                    rs.getInt("score_before"),
                    rs.getInt("score_after"),
                    rs.getLong("repair_time_ms")),
            planId,
            clientRequestId);
    return responses.isEmpty() ? null : responses.getFirst();
  }

  private BaseSchedule baseSchedule(long planId, long runId) {
    Map<Long, SessionInfo> infos = sessionInfos(planId);
    Schedule schedule = new Schedule();
    Set<Long> pinned = new java.util.LinkedHashSet<>();
    jdbc.query(
        """
        select a.session_id, a.teacher_id, a.room_id, a.duration_blocks, a.pinned,
               tb.day_of_week, tb.start_time, j.block_minutes
        from schedule_assignment a
        join time_block tb on tb.id = a.start_time_block_id
        join journey j on j.id = tb.journey_id
        where a.plan_id = ? and a.run_id = ? and a.status = 'ASSIGNED'::assignment_status
        order by a.session_id
        """,
        (RowCallbackHandler) rs -> {
          long sessionId = rs.getLong("session_id");
          SessionInfo info = infos.get(sessionId);
          TimeRange time =
              new TimeRange(
                  startMinute(rs.getInt("day_of_week"), rs.getTime("start_time").toLocalTime()),
                  rs.getInt("duration_blocks") * rs.getInt("block_minutes"));
          schedule.addAssignment(
              new Assignment(
                  sessionId,
                  rs.getLong("teacher_id"),
                  rs.getLong("room_id"),
                  info.cohortIds(),
                  time));
          if (rs.getBoolean("pinned")) {
            pinned.add(sessionId);
          }
        },
        planId,
        runId);
    List<SchedulableSession> sessions =
        infos.values().stream()
            .map(info -> new SchedulableSession(info.id(), info.courseId(), info.cohortIds(), SessionType.CLASS, info.durationMinutes(), pinned.contains(info.id())))
            .toList();
    return new BaseSchedule(schedule, sessions, infos, pinned);
  }

  private Map<Long, SessionInfo> sessionInfos(long planId) {
    Map<Long, SessionInfo> infos = new LinkedHashMap<>();
    jdbc.query(
        """
        select s.id, s.course_id, s.duration_blocks, coalesce(min(j.block_minutes),45) block_minutes,
               coalesce(jsonb_agg(sc.cohort_id order by sc.cohort_id) filter (where sc.cohort_id is not null), '[]'::jsonb)::text cohort_ids
        from schedule_session s
        left join schedule_session_cohort sc on sc.session_id = s.id
        left join cohort c on c.id = sc.cohort_id
        left join journey j on j.id = c.journey_id
        where s.plan_id = ?
        group by s.id
        order by s.id
        """,
        (RowCallbackHandler) rs ->
            infos.put(
                rs.getLong("id"),
                new SessionInfo(
                    rs.getLong("id"),
                    rs.getLong("course_id"),
                    readLongs(rs.getString("cohort_ids")),
                    rs.getInt("duration_blocks") * rs.getInt("block_minutes"))),
        planId);
    return infos;
  }

  private SchedulingProblem problem() {
    Map<Long, Set<Long>> teacherCourses = teacherCourses();
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
                        rs.getInt("weekly_blocks_min") * 45,
                        rs.getBoolean("requires_lab"),
                        false,
                        rs.getInt("weekly_blocks_min"),
                        rs.getInt("weekly_blocks_max"))));
    List<Teacher> teachers =
        jdbc.query(
            "select id, full_name, priority, min_courses, max_courses from teacher where active order by id",
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
                        startMinute(
                            rs.getInt("day_of_week"),
                            rs.getTime("start_time").toLocalTime().plusMinutes((long) rs.getInt("start_block") * rs.getInt("block_minutes"))),
                        rs.getInt("duration_blocks") * rs.getInt("block_minutes"))));
    return new SchedulingProblem(cohorts, courses, teachers, rooms, fixedBreaks);
  }

  private Map<Long, Set<Long>> teacherCourses() {
    Map<Long, Set<Long>> result = new HashMap<>();
    jdbc.query(
        "select teacher_id, course_id from teacher_course",
        (RowCallbackHandler) rs -> result.computeIfAbsent(rs.getLong("teacher_id"), ignored -> new java.util.HashSet<>()).add(rs.getLong("course_id")));
    return result.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
  }

  private long createRun(long planId, int runNumber, ManualEditRequest request, RepairResult result) {
    return jdbc.queryForObject(
        """
        insert into schedule_run(plan_id,run_number,solver_mode,seed,engine_version,status,config,input_snapshot)
        values (?,?,'MANUAL_EDIT',? ,?,'RUNNING'::schedule_run_status,?::jsonb,?::jsonb)
        returning id
        """,
        Long.class,
        planId,
        runNumber,
        Objects.hash(request.baseRunId(), request.clientRequestId(), ENGINE_VERSION),
        ENGINE_VERSION,
        toJson(Map.of("maxIterations", 5000)),
        toJson(Map.of("manualEdit", request, "neighborhood", result.neighborhood().causes())));
  }

  private void persistAssignments(
      long planId, long runId, Schedule schedule, BaseSchedule base, Set<Long> pinnedSessionIds) {
    Map<Long, Assignment> bySession =
        schedule.assignments().stream().collect(Collectors.toMap(Assignment::sessionId, Function.identity()));
    for (Assignment assignment : schedule.assignments().stream().sorted(Comparator.comparingLong(Assignment::sessionId)).toList()) {
      long timeBlockId = timeBlockId(assignment.cohortIds().getFirst(), assignment.time().startMinuteOfWeek());
      jdbc.update(
          """
          insert into schedule_assignment(plan_id,run_id,session_id,teacher_id,room_id,start_time_block_id,duration_blocks,status,pinned)
          values (?,?,?,?,?,?,?,'ASSIGNED'::assignment_status,?)
          """,
          planId,
          runId,
          assignment.sessionId(),
          assignment.teacherId(),
          assignment.roomId(),
          timeBlockId,
          Math.max(1, assignment.time().durationMinutes() / 45),
          pinnedSessionIds.contains(assignment.sessionId()));
    }
    base.sessionsById().keySet().stream()
        .filter(sessionId -> !bySession.containsKey(sessionId))
        .forEach(
            sessionId ->
                jdbc.update(
                    "insert into schedule_assignment(plan_id,run_id,session_id,status,unassigned_reason) values (?,?,?,'UNASSIGNED'::assignment_status,'MANUAL_EDIT_REPAIR_FAILED')",
                    planId,
                    runId,
                    sessionId));
  }

  private void persistViolations(long runId, List<RepairViolation> violations) {
    for (RepairViolation violation : violations) {
      jdbc.update(
          """
          insert into schedule_violation(run_id,severity,code,message,affected_entities,cost)
          values (?,'ERROR'::issue_severity,?,?,?::jsonb,100000)
          """,
          runId,
          violation.code(),
          "Conflicto restante tras edicion manual.",
          toJson(violation.sessionIds().stream().map(id -> Map.of("type", "session", "id", id)).toList()));
    }
  }

  private long insertManualEdit(long planId, ManualEditRequest request, RepairResult result, long runId, int scoreAfter) {
    try {
      return jdbc.queryForObject(
          """
          insert into manual_edit(
            plan_id, base_run_id, result_run_id, session_id, requested_by, client_request_id,
            request_payload, target_teacher_id, target_room_id, target_time_block_id, status,
            pinned_session_ids, neighborhood_session_ids, moved_session_ids, remaining_violations,
            repair_metadata, score_before, score_after, repair_time_ms
          )
          values (?,?,?,?,?,?,?::jsonb,?,?,?,?::manual_edit_status,?::jsonb,?::jsonb,?::jsonb,?::jsonb,?::jsonb,?,?,?)
          returning id
          """,
          Long.class,
          planId,
          request.baseRunId(),
          runId,
          request.sessionId(),
          systemUserId(),
          request.clientRequestId(),
          toJson(request),
          request.targetTeacherId(),
          request.targetRoomId(),
          request.targetTimeBlockId(),
          result.status().name(),
          toJson(sorted(result.pinnedSessionIds())),
          toJson(sorted(result.neighborhood().sessionIds())),
          toJson(sorted(result.movedSessionIds())),
          toJson(violationMaps(result.remainingViolations())),
          toJson(Map.of("causes", result.neighborhood().causes())),
          result.status().name().equals("APPLIED_CLEAN") ? 0 : 100_000,
          scoreAfter,
          result.repairTimeMs());
    } catch (DataIntegrityViolationException ex) {
      throw new ScheduleApiException("IDEMPOTENCY_CONFLICT", "La edicion ya fue registrada.", HttpStatus.CONFLICT);
    }
  }

  private void insertRejected(long planId, ManualEditRequest request, String status, String message) {
    try {
      jdbc.update(
          """
          insert into manual_edit(plan_id, base_run_id, session_id, requested_by, client_request_id, request_payload, status, repair_metadata)
          values (?,?,?,?,?,?,?::manual_edit_status,?::jsonb)
          """,
          planId,
          request.baseRunId() == 0 ? null : request.baseRunId(),
          request.sessionId() == 0 ? null : request.sessionId(),
          systemUserId(),
          request.clientRequestId(),
          toJson(request),
          status,
          toJson(Map.of("message", message)));
    } catch (DataIntegrityViolationException ignored) {
      throw new ScheduleApiException("IDEMPOTENCY_CONFLICT", "La edicion ya fue registrada.", HttpStatus.CONFLICT);
    }
  }

  private ManualEditResponse response(long editId, RepairResult result, long runId, int scoreAfter) {
    return new ManualEditResponse(
        editId,
        result.status().name(),
        runId,
        sorted(result.pinnedSessionIds()),
        sorted(result.neighborhood().sessionIds()),
        sorted(result.movedSessionIds()),
        violationMaps(result.remainingViolations()),
        result.status().name().equals("APPLIED_CLEAN") ? 0 : 100_000,
        scoreAfter,
        result.repairTimeMs());
  }

  private void finishRun(long runId, String status, int score) {
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
        toJson(Map.of("manualEdit", true)),
        score,
        toJson(ScoreResponse.hardViolations(score / 100_000)),
        runId);
  }

  private TimeRange targetTime(ManualEditRequest request) {
    return jdbc.queryForObject(
        """
        select tb.day_of_week, tb.start_time, j.block_minutes, a.duration_blocks
        from time_block tb
        join journey j on j.id = tb.journey_id
        join schedule_assignment a on a.session_id = ? and a.run_id = ?
        where tb.id = ?
        """,
        (rs, rowNum) ->
            new TimeRange(
                startMinute(rs.getInt("day_of_week"), rs.getTime("start_time").toLocalTime()),
                rs.getInt("duration_blocks") * rs.getInt("block_minutes")),
        request.sessionId(),
        request.baseRunId(),
        request.targetTimeBlockId());
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
    int blockIndex = Math.max(0, (minuteOfDay - block.startTime().toSecondOfDay() / 60) / block.blockMinutes());
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

  private Set<Long> groupSessionIds(long planId, long sessionId) {
    return Set.copyOf(
        jdbc.queryForList(
            """
            select s2.id
            from schedule_session s
            join schedule_session s2 on s2.session_group_id = s.session_group_id and s2.plan_id = s.plan_id
            where s.plan_id = ? and s.id = ?
            """,
            Long.class,
            planId,
            sessionId));
  }

  private int nextRunNumber(long planId) {
    Integer value = jdbc.queryForObject("select coalesce(max(run_number),0) + 1 from schedule_run where plan_id = ?", Integer.class, planId);
    return Objects.requireNonNullElse(value, 1);
  }

  private boolean exists(String table, long planId, long id) {
    Integer count = jdbc.queryForObject("select count(*) from " + table + " where id = ? and plan_id = ?", Integer.class, id, planId);
    return count != null && count > 0;
  }

  private boolean existsById(String table, long id) {
    Integer count = jdbc.queryForObject("select count(*) from " + table + " where id = ?", Integer.class, id);
    return count != null && count > 0;
  }

  private long systemUserId() {
    String email = SecurityContextHolder.getContext().getAuthentication() == null ? null : SecurityContextHolder.getContext().getAuthentication().getName();
    if (email != null) {
      List<Long> ids = jdbc.queryForList("select id from app_user where email = ?", Long.class, email);
      if (!ids.isEmpty()) {
        return ids.getFirst();
      }
    }
    List<Long> ids = jdbc.queryForList("select id from app_user where email = 'manual-edit@system.local'", Long.class);
    if (!ids.isEmpty()) {
      return ids.getFirst();
    }
    return jdbc.queryForObject(
        "insert into app_user(email,password_hash,full_name,role) values ('manual-edit@system.local','system','Manual Edit System','ADMIN'::user_role) returning id",
        Long.class);
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

  private List<Map<String, Object>> readViolations(String value) {
    try {
      return json.readValue(value, new TypeReference<List<Map<String, Object>>>() {});
    } catch (Exception ex) {
      throw new IllegalStateException("json parse failed", ex);
    }
  }

  private static List<Map<String, Object>> violationMaps(List<RepairViolation> violations) {
    return violations.stream()
        .map(violation -> Map.<String, Object>of("code", violation.code(), "sessionIds", violation.sessionIds()))
        .toList();
  }

  private static List<Long> sorted(Set<Long> values) {
    return values.stream().sorted().toList();
  }

  private static Long nullableLong(ResultSet rs, String column) throws SQLException {
    long value = rs.getLong(column);
    return rs.wasNull() ? null : value;
  }

  private static int startMinute(int dayOfWeek, LocalTime startTime) {
    return (dayOfWeek - 1) * DAY_MINUTES + startTime.toSecondOfDay() / 60;
  }

  private record BaseSchedule(
      Schedule schedule,
      List<SchedulableSession> sessions,
      Map<Long, SessionInfo> sessionsById,
      Set<Long> pinnedSessionIds) {
  }

  private record SessionInfo(long id, long courseId, List<Long> cohortIds, int durationMinutes) {
  }

  private record JourneyBlock(long id, int blockMinutes, LocalTime startTime) {
  }
}
