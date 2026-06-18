package edu.udeo.horarios.api.security;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class AppUserRepository {
  private final JdbcClient jdbc;

  AppUserRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  Optional<AppUser> findByEmail(String email) {
    return jdbc
        .sql(
            """
            select id, email, password_hash, full_name, role::text role, teacher_id, cohort_id, active
            from app_user
            where lower(email) = lower(:email)
            """)
        .param("email", email)
        .query(this::map)
        .optional();
  }

  Optional<AppUser> findById(long id) {
    return jdbc
        .sql(
            """
            select id, email, password_hash, full_name, role::text role, teacher_id, cohort_id, active
            from app_user
            where id = :id
            """)
        .param("id", id)
        .query(this::map)
        .optional();
  }

  private AppUser map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
    return new AppUser(
        rs.getLong("id"),
        rs.getString("email"),
        rs.getString("password_hash"),
        rs.getString("full_name"),
        AppRole.valueOf(rs.getString("role")),
        nullableLong(rs, "teacher_id"),
        nullableLong(rs, "cohort_id"),
        rs.getBoolean("active"));
  }

  private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
    long value = rs.getLong(column);
    return rs.wasNull() ? null : value;
  }
}
