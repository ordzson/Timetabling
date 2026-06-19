package edu.udeo.horarios.api.scheduling;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SubstitutionService {
  private final JdbcTemplate jdbc;

  SubstitutionService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  SubstitutionResponse create(SubstitutionRequest request) {
    if (request.startsAt() == null) {
      throw new ScheduleApiException("VALIDATION_ERROR", "startsAt es requerido.", HttpStatus.BAD_REQUEST);
    }
    if (request.endsAt() != null && request.startsAt().isAfter(request.endsAt())) {
      throw new ScheduleApiException("VALIDATION_ERROR", "endsAt debe ser posterior a startsAt.", HttpStatus.BAD_REQUEST);
    }
    AssignmentSlot slot = assignmentSlot(request.assignmentId());
    if (!"LOCKED".equals(slot.planStatus())) {
      throw new ScheduleApiException("SUBSTITUTION_REJECTED_BY_STATE", "El plan debe estar publicado.", HttpStatus.CONFLICT);
    }
    if (slot.originalTeacherId() == request.substituteTeacherId()) {
      throw new ScheduleApiException("SUBSTITUTE_TEACHER_CONFLICT", "El sustituto ya imparte esta sesion.", HttpStatus.UNPROCESSABLE_ENTITY);
    }
    if (hasOverlap(slot, request.substituteTeacherId())) {
      throw new ScheduleApiException("SUBSTITUTE_TEACHER_CONFLICT", "El docente sustituto tiene solape.", HttpStatus.UNPROCESSABLE_ENTITY);
    }
    Long id =
        jdbc.queryForObject(
            """
            insert into substitution_event(
              assignment_id, original_teacher_id, substitute_teacher_id,
              starts_at, ends_at, is_permanent, reason, created_by
            )
            values (?,?,?,?,?,?,?,?)
            returning id
            """,
            Long.class,
            request.assignmentId(),
            slot.originalTeacherId(),
            request.substituteTeacherId(),
            Timestamp.from(request.startsAt()),
            request.endsAt() == null ? null : Timestamp.from(request.endsAt()),
            request.isPermanent(),
            request.reason(),
            currentUserId());
    return one(id == null ? 0 : id);
  }

  SubstitutionListResponse list(Long planId, Long teacherId, Instant activeAt) {
    List<Object> args = new ArrayList<>();
    StringBuilder where = new StringBuilder(" where true");
    if (planId != null) {
      where.append(" and a.plan_id = ?");
      args.add(planId);
    }
    if (teacherId != null) {
      where.append(" and (se.original_teacher_id = ? or se.substitute_teacher_id = ?)");
      args.add(teacherId);
      args.add(teacherId);
    }
    if (activeAt != null) {
      where.append(" and se.starts_at <= ? and (se.ends_at is null or se.ends_at >= ?)");
      Timestamp at = Timestamp.from(activeAt);
      args.add(at);
      args.add(at);
    }
    return new SubstitutionListResponse(
        jdbc.query(baseSelect() + where + " order by se.starts_at desc, se.id desc", this::map, args.toArray()));
  }

  private AssignmentSlot assignmentSlot(long assignmentId) {
    List<AssignmentSlot> slots =
        jdbc.query(
            """
            select a.id, a.plan_id, sp.status::text plan_status, a.run_id, a.teacher_id,
                   tb.day_of_week, tb.block_index, a.duration_blocks
            from schedule_assignment a
            join schedule_plan sp on sp.id = a.plan_id
            join time_block tb on tb.id = a.start_time_block_id
            where a.id = ? and a.status = 'ASSIGNED'::assignment_status
            """,
            (rs, rowNum) ->
                new AssignmentSlot(
                    rs.getLong("id"),
                    rs.getLong("plan_id"),
                    rs.getString("plan_status"),
                    rs.getLong("run_id"),
                    rs.getLong("teacher_id"),
                    rs.getInt("day_of_week"),
                    rs.getInt("block_index"),
                    rs.getInt("duration_blocks")),
            assignmentId);
    if (slots.isEmpty()) {
      throw new ScheduleApiException("RESOURCE_NOT_FOUND", "El recurso no existe.", HttpStatus.NOT_FOUND);
    }
    return slots.getFirst();
  }

  private boolean hasOverlap(AssignmentSlot slot, long substituteTeacherId) {
    Integer count =
        jdbc.queryForObject(
            """
            select count(*)
            from schedule_assignment other
            join time_block tb on tb.id = other.start_time_block_id
            where other.run_id = ?
              and other.id <> ?
              and other.status = 'ASSIGNED'::assignment_status
              and other.teacher_id = ?
              and tb.day_of_week = ?
              and tb.block_index < ? + ?
              and tb.block_index + other.duration_blocks > ?
            """,
            Integer.class,
            slot.runId(),
            slot.id(),
            substituteTeacherId,
            slot.dayOfWeek(),
            slot.startBlock(),
            slot.durationBlocks(),
            slot.startBlock());
    return count != null && count > 0;
  }

  private SubstitutionResponse one(long id) {
    return jdbc.queryForObject(baseSelect() + " where se.id = ?", this::map, id);
  }

  private String baseSelect() {
    return """
        select se.id, se.assignment_id, se.original_teacher_id, se.substitute_teacher_id,
               se.starts_at, se.ends_at, se.is_permanent, se.reason
        from substitution_event se
        join schedule_assignment a on a.id = se.assignment_id
        """;
  }

  private SubstitutionResponse map(ResultSet rs, int rowNum) throws SQLException {
    Timestamp endsAt = rs.getTimestamp("ends_at");
    return new SubstitutionResponse(
        rs.getLong("id"),
        rs.getLong("assignment_id"),
        rs.getLong("original_teacher_id"),
        rs.getLong("substitute_teacher_id"),
        rs.getTimestamp("starts_at").toInstant(),
        endsAt == null ? null : endsAt.toInstant(),
        rs.getBoolean("is_permanent"),
        rs.getString("reason"));
  }

  private long currentUserId() {
    String email = SecurityContextHolder.getContext().getAuthentication() == null ? null : SecurityContextHolder.getContext().getAuthentication().getName();
    if (email != null) {
      List<Long> ids = jdbc.queryForList("select id from app_user where email = ?", Long.class, email);
      if (!ids.isEmpty()) {
        return ids.getFirst();
      }
    }
    List<Long> ids = jdbc.queryForList("select id from app_user where email = 'substitution@system.local'", Long.class);
    if (!ids.isEmpty()) {
      return ids.getFirst();
    }
    return jdbc.queryForObject(
        "insert into app_user(email,password_hash,full_name,role) values ('substitution@system.local','system','Substitution System','ADMIN'::user_role) returning id",
        Long.class);
  }

  private record AssignmentSlot(
      long id,
      long planId,
      String planStatus,
      long runId,
      long originalTeacherId,
      int dayOfWeek,
      int startBlock,
      int durationBlocks) {
  }
}
