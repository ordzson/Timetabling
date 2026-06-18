package edu.udeo.horarios.api.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class DatabaseMigrationTest {
  private static final String DB_URL =
      System.getenv().getOrDefault("HORARIOS_DB_URL", "jdbc:postgresql://localhost:5432/horarios");
  private static final String DB_USER =
      System.getenv().getOrDefault("HORARIOS_DB_USER", "usuario_horarios");
  private static final String DB_PASSWORD =
      System.getenv().getOrDefault("HORARIOS_DB_PASSWORD", "1234");

  @Test
  void initialMigrationAppliesOnCleanPostgresSchema() throws Exception {
    String schema = "t02_" + UUID.randomUUID().toString().replace("-", "");

    try {
      Flyway.configure()
          .dataSource(DB_URL, DB_USER, DB_PASSWORD)
          .locations("classpath:db/migration")
          .schemas(schema)
          .defaultSchema(schema)
          .load()
          .migrate();

      try (var connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
        assertEquals(1, count(connection, schema, "BASE TABLE", "schedule_plan"));
        assertEquals(1, count(connection, schema, "VIEW", "exam_plan"));
        assertTrue(manualEditCompositeFkCount(connection, schema) >= 3);
      }
    } finally {
      try (var connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
          var statement = connection.createStatement()) {
        statement.execute("drop schema if exists " + schema + " cascade");
      }
    }
  }

  private static int count(
      java.sql.Connection connection, String schema, String tableType, String tableName)
      throws Exception {
    try (var statement =
        connection.prepareStatement(
            """
            select count(*)
            from information_schema.tables
            where table_schema = ?
              and table_type = ?
              and table_name = ?
            """)) {
      statement.setString(1, schema);
      statement.setString(2, tableType);
      statement.setString(3, tableName);
      try (var result = statement.executeQuery()) {
        result.next();
        return result.getInt(1);
      }
    }
  }

  private static int manualEditCompositeFkCount(java.sql.Connection connection, String schema)
      throws Exception {
    try (var statement =
        connection.prepareStatement(
            """
            select count(*)
            from pg_constraint c
            join pg_class table_class on table_class.oid = c.conrelid
            join pg_namespace table_schema on table_schema.oid = table_class.relnamespace
            where table_schema.nspname = ?
              and table_class.relname = 'manual_edit'
              and c.contype = 'f'
              and cardinality(c.conkey) = 2
            """)) {
      statement.setString(1, schema);
      try (var result = statement.executeQuery()) {
        result.next();
        return result.getInt(1);
      }
    }
  }
}
