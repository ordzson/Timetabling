package edu.udeo.horarios.api.security;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class AuthControllerTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PasswordEncoder passwordEncoder;

  @Test
  void loginValidReturnsJwtAndMe() throws Exception {
    String email = "admin-" + UUID.randomUUID() + "@udeo.edu.gt";
    insertAdmin(email, "secret");

    String token =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"email":"%s","password":"secret"}
                        """.formatted(email)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken", not(blankOrNullString())))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.user.email").value(email))
            .andExpect(jsonPath("$.user.role").value("ADMIN"))
            .andReturn()
            .getResponse()
            .getContentAsString()
            .replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");

    mockMvc
        .perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(email));
  }

  @Test
  void loginInvalidReturns401() throws Exception {
    String email = "admin-" + UUID.randomUUID() + "@udeo.edu.gt";
    insertAdmin(email, "secret");

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"bad"}
                    """.formatted(email)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
  }

  @Test
  void insufficientRoleReturns403() throws Exception {
    String email = "teacher-" + UUID.randomUUID() + "@udeo.edu.gt";
    long teacherId =
        insert(
            "insert into teacher(code,full_name,priority,min_courses,max_courses,active) values (?,?,0,0,4,true) returning id",
            "DOC-" + UUID.randomUUID().toString().substring(0, 8),
            "Docente");
    jdbc.update(
        "insert into app_user(email,password_hash,full_name,role,teacher_id) values (?,?,?,'TEACHER'::user_role,?)",
        email,
        passwordEncoder.encode("secret"),
        "Docente",
        teacherId);

    String token = login(email);

    mockMvc
        .perform(get("/api/catalog/careers").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  private void insertAdmin(String email, String password) {
    jdbc.update(
        "insert into app_user(email,password_hash,full_name,role) values (?,?,?,'ADMIN'::user_role)",
        email,
        passwordEncoder.encode(password),
        "Admin");
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
