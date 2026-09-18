package io.github.isharafe.authorization.config;

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

    assertThat(flyway.info().applied()).hasSize(3);
    assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("3");

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

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO AUTH_PERMISSION (CODE, NAME, RESOURCE_TYPE, RESOURCE_PATTERN, ENABLED, VERSION) VALUES ('UI:VIEW', 'Invalid type', 'URL', 'GET:/view', TRUE, 0)"))
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

    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM AUTH_SYNC_STATE WHERE SYNC_KEY = 'GLOBAL_SEED_INITIALIZATION'",
                Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'AUTH_CACHE_INVALIDATION'",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void upgradesAVersionOneSchemaWithoutLosingExistingAuthorizationData() {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL(
        "jdbc:h2:mem:authorization-upgrade-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");

    Flyway versionOne =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/authorization/migration")
            .table("authorization_flyway_schema_history")
            .target("1")
            .load();
    versionOne.migrate();
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.update(
        "INSERT INTO AUTH_ROLE (CODE, NAME, ENABLED, VERSION) VALUES ('UPGRADE_ROLE', 'Before upgrade', TRUE, 0)");
    jdbc.update(
        "INSERT INTO AUTH_PERMISSION_GROUP (CODE, NAME, ENABLED, VERSION) VALUES ('UPGRADE_GROUP', 'Before upgrade', TRUE, 0)");
    jdbc.update(
        "INSERT INTO AUTH_PERMISSION (CODE, NAME, RESOURCE_TYPE, RESOURCE_PATTERN, ENABLED, VERSION) VALUES ('URL:EMPLOYEE_VIEW', 'Before upgrade', 'URL', 'GET:/employees/**', TRUE, 0)");
    jdbc.update(
        "INSERT INTO AUTH_PERMISSION_GROUP_PERMISSION (PERMISSION_GROUP_ID, PERMISSION_ID) SELECT G.ID, P.ID FROM AUTH_PERMISSION_GROUP G, AUTH_PERMISSION P WHERE G.CODE = 'UPGRADE_GROUP' AND P.CODE = 'URL:EMPLOYEE_VIEW'");
    jdbc.update(
        "INSERT INTO AUTH_AUDIT_EVENT (TIMESTAMP, EVENT_KIND, EVENT_TYPE, DECISION, PERMISSION_CODE) VALUES (CURRENT_TIMESTAMP, 'DECISION', 'AUTHORIZATION_GRANTED', 'GRANTED', 'URL:EMPLOYEE_VIEW')");

    Flyway current =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/authorization/migration")
            .table("authorization_flyway_schema_history")
            .load();
    current.migrate();

    assertThat(current.info().current().getVersion().getVersion()).isEqualTo("3");
    assertThat(
            jdbc.queryForObject(
                "SELECT NAME FROM AUTH_ROLE WHERE CODE = 'UPGRADE_ROLE'", String.class))
        .isEqualTo("Before upgrade");
    assertThat(
            jdbc.queryForObject(
                "SELECT CODE FROM AUTH_PERMISSION WHERE NAME = 'Before upgrade'", String.class))
        .isEqualTo("URL:EMPLOYEE_VIEW");
    assertThat(
            jdbc.queryForObject(
                "SELECT PERMISSION_CODE FROM AUTH_AUDIT_EVENT WHERE EVENT_TYPE = 'AUTHORIZATION_GRANTED'",
                String.class))
        .isEqualTo("URL:EMPLOYEE_VIEW");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM AUTH_PERMISSION_GROUP_PERMISSION", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM AUTH_SYNC_STATE WHERE SYNC_KEY = 'GLOBAL_SEED_INITIALIZATION'",
                Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'AUTH_CACHE_INVALIDATION'",
                Integer.class))
        .isEqualTo(1);
  }
}
