package edu.udeo.horarios.api.reporting;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Iterator;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "ADMIN")
class ReportControllerTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void exportsPdfAndXlsxFromPersistedRun() throws Exception {
    long planId = seed(true, false);
    String generation =
        mockMvc
            .perform(
                post("/api/schedule-plans/" + planId + "/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"seed\":12345,\"weights\":{\"gaps\":2}}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long runId = Long.parseLong(generation.replaceAll(".*\"runId\":(\\d+).*", "$1"));

    byte[] pdf =
        mockMvc
            .perform(get("/api/reports/schedule-plans/" + planId + ".pdf").param("runId", String.valueOf(runId)))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertTrue(new String(pdf, 0, 4).startsWith("%PDF"));

    byte[] xlsx =
        mockMvc
            .perform(get("/api/reports/schedule-plans/" + planId + ".xlsx").param("runId", String.valueOf(runId)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
      assertTrue(workbook.getSheet("cohort").getPhysicalNumberOfRows() > 1);
      assertTrue(workbook.getSheet("teacher").getPhysicalNumberOfRows() > 1);
      assertTrue(workbook.getSheet("room").getPhysicalNumberOfRows() > 1);
      assertTrue(metadataContains(workbook.getSheet("metadata"), "seed", "12345"));
      assertTrue(metadataContains(workbook.getSheet("metadata"), "engineVersion", "solver-0.1.0"));
      assertTrue(metadataContains(workbook.getSheet("metadata"), "weights/config", "gaps"));
    }
  }

  @Test
  void conflictsSheetIncludesUnassignedSessions() throws Exception {
    long planId = seed(true, true);
    String generation =
        mockMvc
            .perform(
                post("/api/schedule-plans/" + planId + "/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"seed\":12345}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long runId = Long.parseLong(generation.replaceAll(".*\"runId\":(\\d+).*", "$1"));

    byte[] xlsx =
        mockMvc
            .perform(get("/api/reports/schedule-plans/" + planId + ".xlsx").param("runId", String.valueOf(runId)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
      assertTrue(sheetContains(workbook.getSheet("conflicts"), "NO_TIME"));
    }
  }

  private boolean metadataContains(org.apache.poi.ss.usermodel.Sheet sheet, String key, String value) {
    for (Row row : sheet) {
      if (row.getCell(0) != null
          && key.equals(row.getCell(0).getStringCellValue())
          && row.getCell(1).getStringCellValue().contains(value)) {
        return true;
      }
    }
    return false;
  }

  private boolean sheetContains(org.apache.poi.ss.usermodel.Sheet sheet, String value) {
    for (Row row : sheet) {
      Iterator<org.apache.poi.ss.usermodel.Cell> cells = row.cellIterator();
      while (cells.hasNext()) {
        if (cells.next().toString().contains(value)) {
          return true;
        }
      }
    }
    return false;
  }

  private long seed(boolean teacherCourse, boolean blockAllTime) {
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
        insert("insert into curriculum(career_id,code,year,is_active) values (?,?,2026,true) returning id", careerId, "PEN-" + suffix);
    long courseId =
        insert(
            "insert into course(code,name,requires_lab,weekly_blocks_min,weekly_blocks_max) values (?,?,false,1,1) returning id",
            "CUR-" + suffix,
            "Curso " + suffix);
    jdbc.update("insert into curriculum_course(curriculum_id,course_id,semester_number) values (?,?,1)", curriculumId, courseId);
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
    jdbc.update("insert into room(code,capacity,type,floor,number,active) values (?,?, 'THEORY'::room_type,1,101,true)", "AUL-" + suffix, 35);
    return insert(
        "insert into schedule_plan(code,name,start_date,end_date) values (?,?,?,?) returning id",
        "PLAN-" + suffix,
        "Plan " + suffix,
        LocalDate.now(),
        LocalDate.now().plusDays(1));
  }

  private long insert(String sql, Object... args) {
    Long id = jdbc.queryForObject(sql, Long.class, args);
    return id == null ? 0 : id;
  }
}
