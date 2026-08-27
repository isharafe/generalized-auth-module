package com.example.authorization.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditEventKindMigrationTest {
  @Test
  void backfillsExistingDecisionAndChangeEventsBeforeMakingKindRequired() {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL(
        "jdbc:h2:mem:audit-kind-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");

    flyway(dataSource, MigrationVersion.fromVersion("3")).migrate();

    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.update(
        """
        INSERT INTO AUTH_AUDIT_EVENT (TIMESTAMP, EVENT_TYPE, DECISION)
        VALUES (CURRENT_TIMESTAMP, 'AUTHORIZATION_DENIED', 'DENIED')
        """);
    jdbc.update(
        """
        INSERT INTO AUTH_AUDIT_EVENT (TIMESTAMP, EVENT_TYPE, EVENT_TARGET, AUDIT_ACTION)
        VALUES (CURRENT_TIMESTAMP, 'ROLE_UPDATED', 'ROLE:MANAGER', 'UPDATE')
        """);

    flyway(dataSource, MigrationVersion.LATEST).migrate();

    Map<String, String> kinds =
        jdbc.query(
            "SELECT EVENT_TYPE, EVENT_KIND FROM AUTH_AUDIT_EVENT",
            resultSet -> {
              Map<String, String> values = new java.util.HashMap<>();
              while (resultSet.next())
                values.put(resultSet.getString("EVENT_TYPE"), resultSet.getString("EVENT_KIND"));
              return values;
            });

    assertThat(kinds)
        .containsEntry("AUTHORIZATION_DENIED", "DECISION")
        .containsEntry("ROLE_UPDATED", "CHANGE");
    assertThat(
            jdbc.queryForObject(
                """
                SELECT IS_NULLABLE
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = 'AUTH_AUDIT_EVENT' AND COLUMN_NAME = 'EVENT_KIND'
                """,
                String.class))
        .isEqualTo("NO");
  }

  private Flyway flyway(JdbcDataSource dataSource, MigrationVersion target) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/authorization/migration")
        .table("authorization_flyway_schema_history")
        .target(target)
        .load();
  }
}
