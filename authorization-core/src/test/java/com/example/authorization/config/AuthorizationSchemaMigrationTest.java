package com.example.authorization.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class AuthorizationSchemaMigrationTest {
  @Test
  void createsTheCurrentSchemaFromVersionedMigrations() {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL(
        "jdbc:h2:mem:authorization-schema-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");

    Flyway flyway =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/authorization/migration")
            .table("authorization_flyway_schema_history")
            .load();

    flyway.migrate();

    assertThat(flyway.info().applied()).hasSize(2);
    assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");

    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    List<String> auditColumns =
        jdbc.queryForList(
            """
            SELECT COLUMN_NAME
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_NAME = 'AUTH_AUDIT_EVENT'
            """,
            String.class);

    assertThat(auditColumns)
        .contains(
            "EVENT_KIND",
            "EVENT_TARGET",
            "AUDIT_ACTION",
            "CORRELATION_ID",
            "DETAILS_JSON",
            "REQUEST_METHOD",
            "REQUEST_PATH");

    assertThat(
            jdbc.queryForObject(
                """
                SELECT IS_NULLABLE
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = 'AUTH_AUDIT_EVENT' AND COLUMN_NAME = 'EVENT_KIND'
                """,
                String.class))
        .isEqualTo("NO");

    assertThat(
            jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME IN ('AUTH_PERMISSION', 'AUTH_RESOURCE_RULE')
                  AND COLUMN_NAME = 'HTTP_METHOD'
                """,
                Integer.class))
        .isZero();

    jdbc.update(
        """
        INSERT INTO AUTH_AUDIT_EVENT (TIMESTAMP, EVENT_KIND, EVENT_TYPE, DECISION)
        VALUES (CURRENT_TIMESTAMP, 'DECISION', 'AUTHORIZATION_DENIED', 'DENIED')
        """);
    jdbc.update(
        """
        INSERT INTO AUTH_AUDIT_EVENT (
          TIMESTAMP,
          EVENT_KIND,
          EVENT_TYPE,
          EVENT_TARGET,
          AUDIT_ACTION
        )
        VALUES (CURRENT_TIMESTAMP, 'CHANGE', 'ROLE_UPDATED', 'ROLE:MANAGER', 'UPDATE')
        """);

    assertThat(
            jdbc.queryForList(
                "SELECT EVENT_KIND FROM AUTH_AUDIT_EVENT ORDER BY ID", String.class))
        .containsExactly("DECISION", "CHANGE");

    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO AUTH_AUDIT_EVENT (TIMESTAMP, EVENT_TYPE)
                    VALUES (CURRENT_TIMESTAMP, 'INVALID_WITHOUT_KIND')
                    """))
        .isInstanceOf(DataIntegrityViolationException.class);

    List<String> identityEventColumns =
        jdbc.queryForList(
            """
            SELECT COLUMN_NAME
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_NAME = 'AUTH_IDENTITY_CHANGE_EVENT'
            """,
            String.class);
    assertThat(identityEventColumns)
        .contains(
            "SOURCE_SYSTEM",
            "EXTERNAL_EVENT_ID",
            "EVENT_TYPE",
            "EXTERNAL_ISSUER",
            "EXTERNAL_SUBJECT",
            "STATUS",
            "ATTEMPTS",
            "PROCESSING_STARTED_AT",
            "PROCESSED_AT",
            "LAST_ERROR");
  }
}
