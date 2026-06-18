package edu.udeo.horarios.api.catalog;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "ADMIN")
class CatalogControllerTest {
  @Autowired private MockMvc mockMvc;

  @Test
  void createsAndListsCatalogs() throws Exception {
    String suffix = UUID.randomUUID().toString().substring(0, 8);

    postOk("careers", """
        {"code":"CAR-%s","name":"Carrera %s","active":true}
        """.formatted(suffix, suffix));
    postOk("courses", """
        {"code":"CUR-%s","name":"Curso %s","requiresLab":false,"weeklyBlocksMin":2,"weeklyBlocksMax":4,"preferences":{}}
        """.formatted(suffix, suffix));
    postOk("teachers", """
        {"code":"DOC-%s","fullName":"Docente %s","priority":0,"minCourses":1,"maxCourses":4,"active":true}
        """.formatted(suffix, suffix));
    postOk("rooms", """
        {"code":"AUL-%s","capacity":35,"type":"THEORY","floor":1,"number":101,"active":true}
        """.formatted(suffix));
    postOk("journeys", """
        {"code":"JOR-%s","name":"Jornada %s","blockMinutes":50,"startTime":"07:00:00","endTime":"12:00:00"}
        """.formatted(suffix, suffix));

    mockMvc
        .perform(get("/api/catalog/careers"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()", greaterThanOrEqualTo(1)))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20));
  }

  @Test
  void duplicateCodeReturnsStableError() throws Exception {
    String code = "DUP-" + UUID.randomUUID().toString().substring(0, 8);
    String body = """
        {"code":"%s","name":"Duplicada","active":true}
        """.formatted(code);

    postOk("careers", body);

    mockMvc
        .perform(post("/api/catalog/careers").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));
  }

  @Test
  void invalidRequestReturnsValidationFailed() throws Exception {
    mockMvc
        .perform(
            post("/api/catalog/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"code":"","name":"Curso malo","requiresLab":false,"weeklyBlocksMin":4,"weeklyBlocksMax":2,"preferences":{}}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.details.fields.length()", greaterThanOrEqualTo(1)));
  }

  private void postOk(String resource, String body) throws Exception {
    mockMvc
        .perform(post("/api/catalog/" + resource).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists());
  }
}
