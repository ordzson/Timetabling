package edu.udeo.horarios.api.importing;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AcademicImportControllerTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void validCsvImportsAcademicData() throws Exception {
    String suffix = UUID.randomUUID().toString().substring(0, 8);

    mockMvc
        .perform(
            multipart("/api/imports/academic-data")
                .file(file(csv(suffix, false)))
                .param("mode", "IMPORT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IMPORTED"))
        .andExpect(jsonPath("$.errorCount").value(0))
        .andExpect(jsonPath("$.summary.rowsRead").value(13));

    assertEquals(1, count("career", "CAR-" + suffix));
    assertEquals(1, count("course", "CUR-" + suffix));
    assertEquals(1, count("teacher", "DOC-" + suffix));
    assertEquals(1, count("room", "AUL-" + suffix));
    assertEquals(1, count("journey", "JOR-" + suffix));
  }

  @Test
  void invalidCsvKeepsCatalogsUnchangedAndStoresRowErrors() throws Exception {
    String suffix = UUID.randomUUID().toString().substring(0, 8);

    String response =
        mockMvc
            .perform(
                multipart("/api/imports/academic-data")
                    .file(file(csv(suffix, true)))
                    .param("mode", "IMPORT"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("INVALID"))
            .andExpect(jsonPath("$.errorCount", greaterThanOrEqualTo(1)))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertEquals(0, count("career", "CAR-" + suffix));
    String batchId = response.replaceAll(".*\"importBatchId\":(\\d+).*", "$1");

    mockMvc
        .perform(get("/api/imports/" + batchId + "/errors"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].sheetName").value("teacher_courses"))
        .andExpect(jsonPath("$.items[0].rowNumber").exists())
        .andExpect(jsonPath("$.items[0].columnName").value("course_code"))
        .andExpect(jsonPath("$.items[0].rawValue").value("CUR-MISSING"))
        .andExpect(jsonPath("$.items[0].code").value("RESOURCE_NOT_FOUND"));
  }

  private MockMultipartFile file(String content) {
    return new MockMultipartFile(
        "file", "academic.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
  }

  private String csv(String suffix, boolean invalidFk) {
    String course = invalidFk ? "CUR-MISSING" : "CUR-" + suffix;
    List<String> headers =
        List.of(
            "sheet",
            "code",
            "name",
            "active",
            "career_code",
            "year",
            "is_active",
            "requires_lab",
            "weekly_blocks_min",
            "weekly_blocks_max",
            "curriculum_code",
            "course_code",
            "semester_number",
            "section",
            "journey_code",
            "expected_students",
            "full_name",
            "priority",
            "min_courses",
            "max_courses",
            "teacher_code",
            "preference",
            "day_of_week",
            "start_block",
            "duration_blocks",
            "capacity",
            "type",
            "floor",
            "number",
            "block_minutes",
            "start_time",
            "end_time",
            "reason",
            "common_area_code");
    StringBuilder csv = new StringBuilder(String.join(",", headers)).append('\n');
    append(csv, headers, row("sheet", "careers", "code", "CAR-" + suffix, "name", "Carrera " + suffix, "active", "true"));
    append(csv, headers, row("sheet", "curricula", "code", "PEN-" + suffix, "career_code", "CAR-" + suffix, "year", "2026", "is_active", "true"));
    append(csv, headers, row("sheet", "courses", "code", "CUR-" + suffix, "name", "Curso " + suffix, "requires_lab", "false", "weekly_blocks_min", "2", "weekly_blocks_max", "4"));
    append(csv, headers, row("sheet", "curriculum_courses", "career_code", "CAR-" + suffix, "curriculum_code", "PEN-" + suffix, "course_code", "CUR-" + suffix, "semester_number", "1"));
    append(csv, headers, row("sheet", "cohorts", "career_code", "CAR-" + suffix, "curriculum_code", "PEN-" + suffix, "semester_number", "1", "section", "A", "journey_code", "JOR-" + suffix, "expected_students", "30", "active", "true"));
    append(csv, headers, row("sheet", "teachers", "code", "DOC-" + suffix, "full_name", "Docente " + suffix, "priority", "0", "min_courses", "1", "max_courses", "4", "active", "true"));
    append(csv, headers, row("sheet", "teacher_courses", "teacher_code", "DOC-" + suffix, "course_code", course, "preference", "0"));
    append(csv, headers, row("sheet", "teacher_availability", "teacher_code", "DOC-" + suffix, "journey_code", "JOR-" + suffix, "day_of_week", "1", "start_block", "0", "duration_blocks", "2", "preference", "0"));
    append(csv, headers, row("sheet", "rooms", "code", "AUL-" + suffix, "capacity", "35", "type", "THEORY", "floor", "1", "number", "101", "active", "true"));
    append(csv, headers, row("sheet", "journeys", "code", "JOR-" + suffix, "name", "Jornada " + suffix, "block_minutes", "50", "start_time", "07:00", "end_time", "12:00"));
    append(csv, headers, row("sheet", "fixed_breaks", "journey_code", "JOR-" + suffix, "day_of_week", "1", "start_block", "1", "duration_blocks", "1", "reason", "Receso"));
    append(csv, headers, row("sheet", "common_areas", "code", "COM-" + suffix, "course_code", "CUR-" + suffix, "journey_code", "JOR-" + suffix, "semester_number", "1", "active", "true"));
    append(csv, headers, row("sheet", "common_area_careers", "common_area_code", "COM-" + suffix, "career_code", "CAR-" + suffix, "curriculum_code", "PEN-" + suffix));
    return csv.toString();
  }

  private Map<String, String> row(String... pairs) {
    Map<String, String> row = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      row.put(pairs[i], pairs[i + 1]);
    }
    return row;
  }

  private void append(StringBuilder csv, List<String> headers, Map<String, String> row) {
    csv.append(headers.stream().map(header -> row.getOrDefault(header, "")).reduce((a, b) -> a + "," + b).orElse(""))
        .append('\n');
  }

  private int count(String table, String code) {
    Integer count = jdbc.queryForObject("select count(*) from " + table + " where code = ?", Integer.class, code);
    return count == null ? 0 : count;
  }
}
