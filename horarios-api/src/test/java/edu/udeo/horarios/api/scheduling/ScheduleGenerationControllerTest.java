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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ScheduleGenerationControllerTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;

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
