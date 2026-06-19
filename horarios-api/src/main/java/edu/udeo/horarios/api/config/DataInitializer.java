package edu.udeo.horarios.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class DataInitializer implements CommandLineRunner {
  private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
  private final JdbcClient jdbc;
  private final PasswordEncoder passwordEncoder;

  DataInitializer(JdbcClient jdbc, PasswordEncoder passwordEncoder) {
    this.jdbc = jdbc;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public void run(String... args) {
    long adminCount = jdbc.sql("select count(*) from app_user where role = 'ADMIN'::user_role or role = 'SUPERADMIN'::user_role")
        .query(Long.class)
        .single();

    if (adminCount == 0) {
      String email = "admin@udeo.edu.gt";
      String password = "admin123";
      String encodedPassword = passwordEncoder.encode(password);
      
      jdbc.sql("""
          insert into app_user (email, password_hash, full_name, role)
          values (:email, :passwordHash, :fullName, 'ADMIN'::user_role)
          """)
          .param("email", email)
          .param("passwordHash", encodedPassword)
          .param("fullName", "Admin UdeO")
          .update();
          
      log.info("DATABASE INITIALIZED: Created default admin user with email '{}' and password '{}'", email, password);
    }
  }
}
