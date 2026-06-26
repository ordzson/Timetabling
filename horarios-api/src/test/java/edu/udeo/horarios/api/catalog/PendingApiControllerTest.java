package edu.udeo.horarios.api.catalog;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PendingApiControllerTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PasswordEncoder passwordEncoder;

  @Test
  void adminPendingCatalogsAreRealEndpoints() throws Exception {
    String token = adminToken();
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    long careerId = insert("insert into career(code,name,active) values (?,?,true) returning id", "CAR-" + suffix, "Carrera");
    long journeyId =
        insert("insert into journey(code,name,block_minutes,start_time,end_time) values (?,?,?,?::time,?::time) returning id", "JOR-" + suffix, "Jornada", 50, "07:00:00", "12:00:00");

    mockMvc
        .perform(
            post("/api/catalog/curricula")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"code":"PEN-%s","careerId":%d,"year":2026,"isActive":true}
                    """.formatted(suffix, careerId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.careerId").value(careerId));

    long curriculumId =
        insert("select id from curriculum where code = ?", "PEN-" + suffix);
    long courseId =
        insert("insert into course(code,name,requires_lab,weekly_blocks_min,weekly_blocks_max) values (?,?,false,1,2) returning id", "CUR-" + suffix, "Curso");
    long teacherId =
        insert("insert into teacher(code,full_name,priority,min_courses,max_courses,active) values (?,?,0,0,4,true) returning id", "DOC-" + suffix, "Docente");
    long roomId =
        insert("insert into room(code,capacity,type,floor,number,active) values (?,?, 'THEORY'::room_type,1,101,true) returning id", "AUL-" + suffix, 30);
    long commonAreaId =
        insert("insert into common_area_rule(code,course_id,journey_id,semester_number,name,active) values (?,?,?,?,?,true) returning id", "COM-" + suffix, courseId, journeyId, 1, "Comun");
    long resourceId = insert("insert into resource(code,name) values (?,?) returning id", "REC-" + suffix, "Proyector");

    jdbc.update("insert into curriculum_course(curriculum_id,course_id,semester_number) values (?,?,1)", curriculumId, courseId);
    jdbc.update("insert into cohort(career_id,curriculum_id,semester_number,section,journey_id,expected_students,active) values (?,?,1,'A',?,30,true)", careerId, curriculumId, journeyId);
    jdbc.update("insert into teacher_course(teacher_id,course_id) values (?,?)", teacherId, courseId);
    jdbc.update("insert into teacher_availability(teacher_id,journey_id,day_of_week,start_block,duration_blocks,preference) values (?,?,?,?,?,0)", teacherId, journeyId, 1, 0, 2);
    jdbc.update("insert into teacher_career_journey(teacher_id,career_id,journey_id,active) values (?,?,?,true)", teacherId, careerId, journeyId);
    jdbc.update("insert into fixed_break(journey_id,day_of_week,start_block,duration_blocks,reason) values (?,?,?,?,?)", journeyId, 1, 2, 1, "Receso");
    jdbc.update("insert into common_area_career(common_area_rule_id,career_id,curriculum_id) values (?,?,?)", commonAreaId, careerId, curriculumId);
    jdbc.update("insert into room_resource(room_id,resource_id) values (?,?)", roomId, resourceId);
    jdbc.update("insert into course_required_resource(course_id,resource_id) values (?,?)", courseId, resourceId);

    for (String resource :
        new String[] {
          "curricula",
          "curriculum-courses",
          "cohorts",
          "teacher-courses",
          "teacher-availability",
          "teacher-career-journeys",
          "fixed-breaks",
          "common-areas",
          "common-area-careers",
          "resources",
          "room-resources",
          "course-required-resources"
        }) {
      mockMvc
          .perform(get("/api/catalog/" + resource).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.items.length()", greaterThanOrEqualTo(1)));
    }
  }

  @Test
  void teacherCanReplaceOwnAvailability() throws Exception {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    long teacherId =
        insert("insert into teacher(code,full_name,priority,min_courses,max_courses,active) values (?,?,0,0,4,true) returning id", "DOC-" + suffix, "Docente");
    long journeyId =
        insert("insert into journey(code,name,block_minutes,start_time,end_time) values (?,?,?,?::time,?::time) returning id", "JOR-" + suffix, "Jornada", 50, "07:00:00", "12:00:00");
    String token = teacherToken(teacherId);

    mockMvc
        .perform(
            put("/api/teacher/availability")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"items":[{"journeyId":%d,"dayOfWeek":2,"startBlock":1,"durationBlocks":3,"preference":5}]}
                    """.formatted(journeyId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.teacherId").value(teacherId))
        .andExpect(jsonPath("$.items[0].journeyId").value(journeyId))
        .andExpect(jsonPath("$.items[0].dayOfWeek").value(2));

    mockMvc
        .perform(get("/api/teacher/availability").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1));
  }

  private String adminToken() throws Exception {
    String email = "admin-" + UUID.randomUUID() + "@udeo.edu.gt";
    jdbc.update(
        "insert into app_user(email,password_hash,full_name,role) values (?,?,?,'ADMIN'::user_role)",
        email,
        passwordEncoder.encode("secret"),
        "Admin");
    return login(email);
  }

  private String teacherToken(long teacherId) throws Exception {
    String email = "teacher-" + UUID.randomUUID() + "@udeo.edu.gt";
    jdbc.update(
        "insert into app_user(email,password_hash,full_name,role,teacher_id) values (?,?,?,'TEACHER'::user_role,?)",
        email,
        passwordEncoder.encode("secret"),
        "Docente",
        teacherId);
    return login(email);
  }

  private String login(String email) throws Exception {
    return mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"secret"}
                    """.formatted(email)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString()
        .replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
  }

  private long insert(String sql, Object... args) {
    Long id = jdbc.queryForObject(sql, Long.class, args);
    return id == null ? 0 : id;
  }
}
