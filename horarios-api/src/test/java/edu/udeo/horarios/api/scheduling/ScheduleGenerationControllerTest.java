package edu.udeo.horarios.api.scheduling;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "ADMIN")
class ScheduleGenerationControllerTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void schedulePlansCanBeCreatedAndListed() throws Exception {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    mockMvc
        .perform(
            post("/api/schedule-plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"code":"PLAN-%s","name":"Plan %s","scheduleType":"CLASSES","startDate":"2026-01-15","endDate":"2026-06-15","config":{"defaultBlockMinutes":45}}
                    """
                        .formatted(suffix, suffix)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value("PLAN-" + suffix))
        .andExpect(jsonPath("$.status").value("DRAFT"));

    mockMvc
        .perform(get("/api/schedule-plans").param("q", "PLAN-" + suffix))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].code").value("PLAN-" + suffix))
        .andExpect(jsonPath("$.totalItems").value(1));
  }

  @Test
  void invalidPlanValidationStoresInputErrors() throws Exception {
    Seed seed = seed(false, 35, false);

    mockMvc
        .perform(post("/api/schedule-plans/" + seed.planId + "/validate"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.hasBlockingErrors").value(true))
        .andExpect(jsonPath("$.issues[0].code").value("COURSE_WITHOUT_TEACHER"));

    assertEquals("INVALID_INPUT", planStatus(seed.planId));
  }

  @Test
  void validPlanGeneratesAndReturnsAssignments() throws Exception {
    Seed seed = seed(true, 35, false);

    String response =
        mockMvc
            .perform(
                post("/api/schedule-plans/" + seed.planId + "/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"solverMode\":\"NORMAL\",\"seed\":12345}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.planStatus").value("GENERATED"))
            .andExpect(jsonPath("$.assignedCount").value(1))
            .andExpect(jsonPath("$.unassignedCount").value(0))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String runId = response.replaceAll(".*\"runId\":(\\d+).*", "$1");

    mockMvc
        .perform(get("/api/schedule-plans/" + seed.planId + "/result").param("runId", runId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assignments[0].courseCode").value(seed.courseCode))
        .andExpect(jsonPath("$.assignments[0].teacherName").value("Docente " + seed.suffix))
        .andExpect(jsonPath("$.unassigned.length()").value(0));
  }

  @Test
  void generatedPlanCanBeApprovedAndLocked() throws Exception {
    Seed seed = seed(true, 35, false);

    String response =
        mockMvc
            .perform(
                post("/api/schedule-plans/" + seed.planId + "/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"seed\":12345}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long runId = Long.parseLong(response.replaceAll(".*\"runId\":(\\d+).*", "$1"));

    mockMvc
        .perform(
            post("/api/schedule-plans/" + seed.planId + "/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"runId\":" + runId + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.runId").value(runId));
    assertEquals("APPROVED", planStatus(seed.planId));

    mockMvc
        .perform(post("/api/schedule-plans/" + seed.planId + "/lock").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("LOCKED"))
        .andExpect(jsonPath("$.runId").value(runId));
    assertEquals("LOCKED", planStatus(seed.planId));
  }

  @Test
  void unassignedSessionsAreReturnedAsViolations() throws Exception {
    Seed seed = seed(true, 35, true);

    String response =
        mockMvc
            .perform(
                post("/api/schedule-plans/" + seed.planId + "/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"seed\":12345}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED_WITH_CONFLICTS"))
            .andExpect(jsonPath("$.planStatus").value("GENERATED_WITH_CONFLICTS"))
            .andExpect(jsonPath("$.unassignedCount", greaterThanOrEqualTo(1)))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String runId = response.replaceAll(".*\"runId\":(\\d+).*", "$1");

    mockMvc
        .perform(get("/api/schedule-plans/" + seed.planId + "/result").param("runId", runId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unassigned[0].reason").value("NO_TIME"));
    mockMvc
        .perform(get("/api/schedule-plans/" + seed.planId + "/violations").param("runId", runId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].code").value("NO_TIME"));
  }

  @Test
  void manualEditCreatesRunAndRetriesByClientRequestId() throws Exception {
    Seed seed = seed(true, 35, false);
    long targetRoomId =
        insert(
            "insert into room(code,capacity,type,floor,number,active) values (?,?, 'THEORY'::room_type,1,102,true) returning id",
            "AUL2-" + seed.suffix,
            35);

    String generation =
        mockMvc
            .perform(
                post("/api/schedule-plans/" + seed.planId + "/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"seed\":12345}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long runId = Long.parseLong(generation.replaceAll(".*\"runId\":(\\d+).*", "$1"));
    long sessionId =
        jdbc.queryForObject(
            "select session_id from schedule_assignment where run_id = ? and status = 'ASSIGNED'::assignment_status",
            Long.class,
            runId);

    String body =
        """
        {"clientRequestId":"edit-1","baseRunId":%d,"sessionId":%d,"targetRoomId":%d}
        """
            .formatted(runId, sessionId, targetRoomId);

    String first =
        mockMvc
            .perform(
                post("/api/schedule-plans/" + seed.planId + "/manual-edits")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPLIED_CLEAN"))
            .andExpect(jsonPath("$.resultRunId").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();

    mockMvc
        .perform(
            post("/api/schedule-plans/" + seed.planId + "/manual-edits")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.manualEditId").value(Long.parseLong(first.replaceAll(".*\"manualEditId\":(\\d+).*", "$1"))));

    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from manual_edit where plan_id = ? and client_request_id = 'edit-1'",
            Integer.class,
            seed.planId));
  }

  @Test
  void substitutionOverlaysResultAndRejectsTeacherOverlap() throws Exception {
    Seed seed = seed(true, 35, false);
    long substituteTeacherId =
        insert(
            "insert into teacher(code,full_name,priority,min_courses,max_courses,active) values (?,?,0,0,4,true) returning id",
            "SUB-" + seed.suffix,
            "Sustituto " + seed.suffix);

    String generation =
        mockMvc
            .perform(
                post("/api/schedule-plans/" + seed.planId + "/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"seed\":12345}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long runId = Long.parseLong(generation.replaceAll(".*\"runId\":(\\d+).*", "$1"));
    long assignmentId =
        jdbc.queryForObject(
            "select id from schedule_assignment where run_id = ? and status = 'ASSIGNED'::assignment_status",
            Long.class,
            runId);
    long originalTeacherId =
        jdbc.queryForObject("select teacher_id from schedule_assignment where id = ?", Long.class, assignmentId);
    jdbc.update("update schedule_plan set status = 'LOCKED'::plan_status where id = ?", seed.planId);

    mockMvc
        .perform(
            post("/api/substitutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"assignmentId":%d,"substituteTeacherId":%d,"startsAt":"2026-01-01T00:00:00Z","isPermanent":true,"reason":"Temporal"}
                    """
                        .formatted(assignmentId, substituteTeacherId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.originalTeacherId").value(originalTeacherId))
        .andExpect(jsonPath("$.substituteTeacherId").value(substituteTeacherId));

    mockMvc
        .perform(get("/api/schedule-plans/" + seed.planId + "/result").param("runId", String.valueOf(runId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assignments[0].teacherId").value(substituteTeacherId))
        .andExpect(jsonPath("$.assignments[0].teacherName").value("Sustituto " + seed.suffix));

    assertEquals(
        originalTeacherId,
        jdbc.queryForObject("select teacher_id from schedule_assignment where id = ?", Long.class, assignmentId));

    long sessionId =
        insert(
            """
            insert into schedule_session(plan_id,session_group_id,course_id,duration_blocks,weekly_index,is_common_area)
            select plan_id, session_group_id, course_id, duration_blocks, 2, is_common_area
            from schedule_session
            where id = (select session_id from schedule_assignment where id = ?)
            returning id
            """,
            assignmentId);
    jdbc.update(
        """
        insert into schedule_assignment(plan_id,run_id,session_id,teacher_id,room_id,start_time_block_id,duration_blocks,status)
        select plan_id,run_id,?, ?, room_id,start_time_block_id,duration_blocks,'ASSIGNED'::assignment_status
        from schedule_assignment where id = ?
        """,
        sessionId,
        substituteTeacherId,
        assignmentId);
    long otherTeacherId =
        insert(
            "insert into teacher(code,full_name,priority,min_courses,max_courses,active) values (?,?,0,0,4,true) returning id",
            "OTH-" + seed.suffix,
            "Otro " + seed.suffix);

    mockMvc
        .perform(
            post("/api/substitutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"assignmentId":%d,"substituteTeacherId":%d,"startsAt":"2026-01-01T00:00:00Z","isPermanent":true}
                    """
                        .formatted(assignmentId, otherTeacherId)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/substitutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"assignmentId":%d,"substituteTeacherId":%d,"startsAt":"2026-01-01T00:00:00Z","isPermanent":true}
                    """
                        .formatted(assignmentId, substituteTeacherId)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("SUBSTITUTE_TEACHER_CONFLICT"));
  }

  private Seed seed(boolean teacherCourse, int roomCapacity, boolean blockAllTime) {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    long careerId =
        insert("insert into career(code,name,active) values (?,?,true) returning id", "CAR-" + suffix, "Carrera " + suffix);
    long journeyId =
        insert(
            "insert into journey(code,name,block_minutes,start_time,end_time) values (?,?,?,?,?) returning id",
            "JOR-" + suffix,
            "Jornada " + suffix,
            45,
            Time.valueOf(LocalTime.of(7, 0)),
            Time.valueOf(LocalTime.of(12, 0)));
    long curriculumId =
        insert(
            "insert into curriculum(career_id,code,year,is_active) values (?,?,2026,true) returning id",
            careerId,
            "PEN-" + suffix);
    long courseId =
        insert(
            "insert into course(code,name,requires_lab,weekly_blocks_min,weekly_blocks_max) values (?,?,false,1,1) returning id",
            "CUR-" + suffix,
            "Curso " + suffix);
    jdbc.update(
        "insert into curriculum_course(curriculum_id,course_id,semester_number) values (?,?,1)",
        curriculumId,
        courseId);
    jdbc.update(
        "insert into cohort(career_id,curriculum_id,semester_number,section,journey_id,expected_students,active) values (?,?,1,'A',?,30,true)",
        careerId,
        curriculumId,
        journeyId);
    if (blockAllTime) {
      for (int day = 1; day <= 5; day++) {
        jdbc.update(
            "insert into fixed_break(journey_id,day_of_week,start_block,duration_blocks,reason) values (?,?,?,?,?)",
            journeyId,
            day,
            0,
            20,
            "Bloqueado");
      }
    }
    long teacherId =
        insert(
            "insert into teacher(code,full_name,priority,min_courses,max_courses,active) values (?,?,0,0,4,true) returning id",
            "DOC-" + suffix,
            "Docente " + suffix);
    if (teacherCourse) {
      jdbc.update("insert into teacher_course(teacher_id,course_id) values (?,?)", teacherId, courseId);
    }
    jdbc.update(
        "insert into room(code,capacity,type,floor,number,active) values (?,?, 'THEORY'::room_type,1,101,true)",
        "AUL-" + suffix,
        roomCapacity);
    long planId =
        insert(
            "insert into schedule_plan(code,name,start_date,end_date) values (?,?,?,?) returning id",
            "PLAN-" + suffix,
            "Plan " + suffix,
            LocalDate.now(),
            LocalDate.now().plusDays(1));
    return new Seed(suffix, planId, "CUR-" + suffix);
  }

  private long insert(String sql, Object... args) {
    Long id = jdbc.queryForObject(sql, Long.class, args);
    return id == null ? 0 : id;
  }

  private String planStatus(long planId) {
    return jdbc.queryForObject("select status::text from schedule_plan where id = ?", String.class, planId);
  }

  private record Seed(String suffix, long planId, String courseCode) {
  }
}
