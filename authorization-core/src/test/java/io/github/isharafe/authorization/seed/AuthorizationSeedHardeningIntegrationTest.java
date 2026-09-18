package io.github.isharafe.authorization.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.isharafe.authorization.domain.AccessMode;
import io.github.isharafe.authorization.domain.ResourceType;
import io.github.isharafe.authorization.seed.AuthorizationSeedDefinition.PermissionGroupSeed;
import io.github.isharafe.authorization.seed.AuthorizationSeedDefinition.PermissionSeed;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = AuthorizationSeedHardeningIntegrationTest.TestApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:seed-hardening;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.jpa.open-in-view=false",
      "authorization.seed.enabled=false"
    })
class AuthorizationSeedHardeningIntegrationTest {
  @jakarta.annotation.Resource private AuthorizationSeedInitializationService initialization;
  @jakarta.annotation.Resource private JdbcTemplate jdbc;

  @BeforeEach
  void resetPortableSeedData() {
    for (String table :
        List.of(
            "AUTH_USER_PERMISSION_GROUP",
            "AUTH_USER_ROLE",
            "AUTH_ROLE_PERMISSION_GROUP",
            "AUTH_PERMISSION_GROUP_PERMISSION",
            "AUTH_PENDING_USER_ASSIGNMENT",
            "AUTH_EXTERNAL_AUTHORITY_MAPPING",
            "AUTH_USER",
            "AUTH_ROLE",
            "AUTH_PERMISSION_GROUP",
            "AUTH_PERMISSION",
            "AUTH_RESOURCE_RULE",
            "AUTH_SEED_HISTORY")) jdbc.update("DELETE FROM " + table);
  }

  @Test
  void upgradesSeedDataIdempotentlyAndReplacesSuppliedMemberships() {
    initialization.apply(versionOne());
    initialization.apply(versionTwo());
    initialization.apply(versionTwo());

    assertThat(string("SELECT NAME FROM AUTH_PERMISSION WHERE CODE = 'URL:EMPLOYEE_VIEW'"))
        .isEqualTo("View employees v2");
    assertThat(
            strings(
                """
                SELECT P.CODE
                FROM AUTH_PERMISSION P
                JOIN AUTH_PERMISSION_GROUP_PERMISSION GP ON GP.PERMISSION_ID = P.ID
                JOIN AUTH_PERMISSION_GROUP G ON G.ID = GP.PERMISSION_GROUP_ID
                WHERE G.CODE = 'EMPLOYEE_GROUP'
                ORDER BY P.CODE
                """))
        .containsExactly("URL:EMPLOYEE_EDIT");
    assertThat(count("AUTH_ROLE")).isEqualTo(1);
    assertThat(count("AUTH_USER_ROLE")).isEqualTo(1);
    assertThat(count("AUTH_SEED_HISTORY")).isEqualTo(2);
  }

  @Test
  void invalidUpgradeRollsBackBeforeAnyMutation() {
    initialization.apply(versionOne());
    AuthorizationSeedDefinition invalid = new AuthorizationSeedDefinition();
    invalid.setPermissions(
        List.of(
            new PermissionSeed(
                "URL:SHOULD_NOT_EXIST",
                "Should not exist",
                null,
                ResourceType.URL,
                "GET:/invalid",
                true)));
    invalid.setPermissionGroups(
        List.of(
            new PermissionGroupSeed(
                "INVALID_GROUP", "Invalid", null, List.of("MISSING_PERMISSION"), true)));

    assertThatThrownBy(() -> initialization.apply(invalid))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MISSING_PERMISSION");

    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM AUTH_PERMISSION WHERE CODE = 'URL:SHOULD_NOT_EXIST'",
                Integer.class))
        .isZero();
    assertThat(count("AUTH_SEED_HISTORY")).isEqualTo(1);
  }

  @Test
  void serializesConcurrentSeedInitializationAgainstTheDatabaseLock() throws Exception {
    int workers = 8;
    CountDownLatch ready = new CountDownLatch(workers);
    CountDownLatch start = new CountDownLatch(1);
    List<Callable<Void>> tasks = new ArrayList<>();
    for (int index = 0; index < workers; index++) {
      tasks.add(
          () -> {
            ready.countDown();
            start.await();
            initialization.apply(versionOne());
            return null;
          });
    }

    try (var executor = Executors.newFixedThreadPool(workers)) {
      List<Future<Void>> futures = tasks.stream().map(executor::submit).toList();
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      for (Future<Void> future : futures) future.get();
    }

    assertThat(count("AUTH_PERMISSION")).isEqualTo(1);
    assertThat(count("AUTH_PERMISSION_GROUP")).isEqualTo(1);
    assertThat(count("AUTH_ROLE")).isEqualTo(1);
    assertThat(count("AUTH_USER")).isEqualTo(1);
    assertThat(count("AUTH_USER_ROLE")).isEqualTo(1);
    assertThat(count("AUTH_SEED_HISTORY")).isEqualTo(1);
  }

  private AuthorizationSeedDefinition versionOne() {
    return new AuthorizationSeedBuilder()
        .permission("URL:EMPLOYEE_VIEW", "View employees v1", ResourceType.URL, "GET:/employees/**")
        .permissionGroup("EMPLOYEE_GROUP", "Employees", "URL:EMPLOYEE_VIEW")
        .role("EMPLOYEE_ROLE", "Employee role", "EMPLOYEE_GROUP")
        .rule("EMPLOYEES", "*", "/employees/**", AccessMode.AUTHORIZED)
        .user("local", "alice", "alice")
        .assignRole("local", "alice", "EMPLOYEE_ROLE")
        .build();
  }

  private AuthorizationSeedDefinition versionTwo() {
    return new AuthorizationSeedBuilder()
        .permission("URL:EMPLOYEE_VIEW", "View employees v2", ResourceType.URL, "GET:/employees/**")
        .permission("URL:EMPLOYEE_EDIT", "Edit employees", ResourceType.URL, "PUT:/employees/**")
        .permissionGroup("EMPLOYEE_GROUP", "Employees v2", "URL:EMPLOYEE_EDIT")
        .role("EMPLOYEE_ROLE", "Employee role v2", "EMPLOYEE_GROUP")
        .rule("EMPLOYEES", "*", "/employees/**", AccessMode.AUTHORIZED)
        .user("local", "alice", "alice")
        .assignRole("local", "alice", "EMPLOYEE_ROLE")
        .build();
  }

  private int count(String table) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
  }

  private String string(String sql) {
    return jdbc.queryForObject(sql, String.class);
  }

  private List<String> strings(String sql) {
    return jdbc.queryForList(sql, String.class);
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class TestApplication {}
}
