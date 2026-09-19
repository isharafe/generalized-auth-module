package io.github.isharafe.authorization.demo.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.isharafe.authorization.domain.AuthenticatedIdentity;
import io.github.isharafe.authorization.keycloak.client.HttpKeycloakAdminClient;
import io.github.isharafe.authorization.keycloak.config.AuthorizationKeycloakProperties;
import io.github.isharafe.authorization.keycloak.sync.KeycloakIdentitySynchronizationProvider;
import io.github.isharafe.authorization.persistence.repository.PermissionGroupRepository;
import io.github.isharafe.authorization.persistence.repository.RoleRepository;
import io.github.isharafe.authorization.persistence.repository.SyncStateRepository;
import io.github.isharafe.authorization.persistence.repository.UserPermissionGroupRepository;
import io.github.isharafe.authorization.persistence.repository.UserRepository;
import io.github.isharafe.authorization.persistence.repository.UserRoleRepository;
import io.github.isharafe.authorization.persistence.service.PendingUserAssignmentResolver;
import io.github.isharafe.authorization.spi.AuthorizationAuditPublisher;
import io.github.isharafe.authorization.spi.AuthorizationCacheInvalidator;
import io.github.isharafe.authorization.spi.AuthorizationObservation;
import io.github.isharafe.authorization.spi.ExternalAuthorityMapper;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest
@ActiveProfiles({"performance", "test"})
@AutoConfigureMockMvc
class AuthorizationPerformanceIT {
  private static final int WARMUPS = Integer.getInteger("authorization.performance.warmups", 3);
  private static final int SAMPLES = Integer.getInteger("authorization.performance.samples", 10);

  @Autowired private MockMvc mvc;
  @Autowired private DemoPerformanceMeasurements measurements;
  @Autowired private AuthorizationCacheInvalidator cache;
  @Autowired private AuthorizationObservation observation;
  @Autowired private ExternalAuthorityMapper authorityMapper;
  @Autowired private UserRepository users;
  @Autowired private RoleRepository roles;
  @Autowired private PermissionGroupRepository groups;
  @Autowired private UserRoleRepository userRoles;
  @Autowired private UserPermissionGroupRepository userGroups;
  @Autowired private SyncStateRepository syncStates;
  @Autowired private ObjectProvider<PendingUserAssignmentResolver> pendingResolver;
  @Autowired private AuthorizationAuditPublisher audit;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void writesRepeatableRequestAndLoginSynchronizationReports() throws Exception {
    List<ScenarioReport> reports = new ArrayList<>();
    reports.add(requestScenario("public_cold", null, "/demo/public", true));
    reports.add(requestScenario("public_warm", null, "/demo/public", false));
    reports.add(requestScenario("authenticated_only", "emma", "/demo/profile", false));
    reports.add(requestScenario("authorized_view_cold", "emma", "/demo/employees", true));
    reports.add(requestScenario("authorized_view_warm", "emma", "/demo/employees", false));
    reports.add(requestScenario("second_user_cold", "michael", "/demo/employees", true));
    reports.addAll(keycloakSynchronizationScenarios());

    Path directory = Path.of("target", "authorization-performance");
    Files.createDirectories(directory);
    new ObjectMapper()
        .findAndRegisterModules()
        .writerWithDefaultPrettyPrinter()
        .writeValue(
            directory.resolve("authorization-performance.json").toFile(),
            new PerformanceReport(Instant.now().toString(), WARMUPS, SAMPLES, reports));
    Files.writeString(
        directory.resolve("authorization-performance.md"),
        markdown(reports),
        StandardCharsets.UTF_8);

    assertThat(reports).isNotEmpty();
    assertThat(reports.stream().filter(report -> report.name().contains("keycloak")).toList())
        .extracting(ScenarioReport::externalCallsMedian)
        .containsExactly(4L, 3L);
  }

  private ScenarioReport requestScenario(
      String name, String user, String path, boolean invalidateBeforeEverySample) throws Exception {
    for (int index = 0; index < WARMUPS; index++) runRequest(user, path, invalidateBeforeEverySample);
    List<Sample> samples = new ArrayList<>();
    for (int index = 0; index < SAMPLES; index++)
      samples.add(runRequest(user, path, invalidateBeforeEverySample));
    return summarize(name, samples);
  }

  private Sample runRequest(String user, String path, boolean invalidate) throws Exception {
    if (invalidate) cache.invalidateAll();
    var request = get(path);
    if (user != null) request.header("X-Demo-User", user);
    long started = System.nanoTime();
    int status = mvc.perform(request).andReturn().getResponse().getStatus();
    Duration total = Duration.ofNanos(System.nanoTime() - started);
    assertThat(status).isIn(200, 403);
    return sample(total, measurements.lastCompleted());
  }

  private List<ScenarioReport> keycloakSynchronizationScenarios() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    try {
      server.createContext(
          "/realms/performance/protocol/openid-connect/token",
          exchange -> json(exchange, 200, "{\"access_token\":\"token\",\"expires_in\":300}"));
      server.createContext(
          "/admin/realms/performance/users/performance-user/groups",
          exchange -> json(exchange, 200, "[]"));
      server.createContext(
          "/admin/realms/performance/users/performance-user/role-mappings/realm",
          exchange -> json(exchange, 200, "[]"));
      server.createContext(
          "/admin/realms/performance/users/performance-user",
          exchange ->
              json(
                  exchange,
                  200,
                  "{\"id\":\"performance-user\",\"username\":\"performance-user\",\"enabled\":true}"));
      server.start();

      AuthorizationKeycloakProperties properties = new AuthorizationKeycloakProperties();
      properties.setBaseUrl("http://localhost:" + server.getAddress().getPort());
      properties.setRealm("performance");
      properties.setClientId("performance-client");
      properties.setClientSecret("performance-secret");
      properties.setIssuer("performance-issuer");
      HttpKeycloakAdminClient client =
          new HttpKeycloakAdminClient(properties, new ObjectMapper(), observation);
      KeycloakIdentitySynchronizationProvider synchronization =
          new KeycloakIdentitySynchronizationProvider(
              properties,
              client,
              authorityMapper,
              users,
              roles,
              groups,
              userRoles,
              userGroups,
              syncStates,
              pendingResolver,
              cache,
              audit,
              observation,
              transactionManager);
      AuthenticatedIdentity identity =
          new AuthenticatedIdentity("performance-issuer", "performance-user", "performance-user");

      Sample first = measure(() -> synchronization.synchronize(identity));
      Sample repeat = measure(() -> synchronization.synchronize(identity));
      return List.of(
          summarize("keycloak_targeted_sync_first_user_cold_token", List.of(first)),
          summarize("keycloak_targeted_sync_unchanged_user_warm_token", List.of(repeat)));
    } finally {
      server.stop(0);
    }
  }

  private Sample measure(Runnable action) {
    measurements.begin();
    long started = System.nanoTime();
    try {
      action.run();
      return sample(
          Duration.ofNanos(System.nanoTime() - started), measurements.finish());
    } catch (RuntimeException exception) {
      measurements.finish();
      throw exception;
    }
  }

  private Sample sample(
      Duration total, DemoPerformanceMeasurements.RequestMeasurement measurement) {
    return new Sample(
        nanosToMillis(total),
        measurement.jdbcCalls(),
        measurement.jdbcStatements(),
        nanosToMillis(measurement.jdbcDuration()),
        measurement.externalCalls(),
        nanosToMillis(measurement.externalDuration()));
  }

  private ScenarioReport summarize(String name, List<Sample> samples) {
    List<Double> totals = samples.stream().map(Sample::totalMs).sorted().toList();
    return new ScenarioReport(
        name,
        samples.size(),
        totals.getFirst(),
        percentile(totals, 0.50),
        percentile(totals, 0.95),
        totals.getLast(),
        totals.stream().mapToDouble(Double::doubleValue).average().orElse(0),
        medianLong(samples.stream().map(Sample::jdbcCalls).toList()),
        medianLong(samples.stream().map(Sample::sqlStatements).toList()),
        medianDouble(samples.stream().map(Sample::jdbcMs).toList()),
        medianLong(samples.stream().map(Sample::externalCalls).toList()),
        medianDouble(samples.stream().map(Sample::externalMs).toList()));
  }

  private String markdown(List<ScenarioReport> reports) {
    StringBuilder value =
        new StringBuilder(
                """
                        # Authorization performance report
                        
                        Generated with H2 and a local mock Keycloak server. Latencies are environment-specific. \
                        JDBC calls are driver executions; SQL statements include batch members.
                        
                        | Scenario | Samples | p50 total ms | p95 total ms | JDBC calls | SQL statements | JDBC ms | External calls | External ms |
                        | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
                        """);
    for (ScenarioReport report : reports)
      value.append(
          "| %s | %d | %.3f | %.3f | %d | %d | %.3f | %d | %.3f |%n"
              .formatted(
                  report.name(),
                  report.samples(),
                  report.totalP50Ms(),
                  report.totalP95Ms(),
                  report.jdbcCallsMedian(),
                  report.sqlStatementsMedian(),
                  report.jdbcMsMedian(),
                  report.externalCallsMedian(),
                  report.externalMsMedian()));
    return value.toString();
  }

  private double percentile(List<Double> sorted, double percentile) {
    int index = Math.max(0, (int) Math.ceil(percentile * sorted.size()) - 1);
    return sorted.get(Math.min(index, sorted.size() - 1));
  }

  private long medianLong(List<Long> values) {
    List<Long> sorted = values.stream().sorted().toList();
    return sorted.get((sorted.size() - 1) / 2);
  }

  private double medianDouble(List<Double> values) {
    List<Double> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
    return sorted.get((sorted.size() - 1) / 2);
  }

  private double nanosToMillis(Duration duration) {
    return duration.toNanos() / 1_000_000.0;
  }

  private void json(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }

  private record Sample(
      double totalMs,
      long jdbcCalls,
      long sqlStatements,
      double jdbcMs,
      long externalCalls,
      double externalMs) {}

  private record ScenarioReport(
      String name,
      int samples,
      double totalMinMs,
      double totalP50Ms,
      double totalP95Ms,
      double totalMaxMs,
      double totalAverageMs,
      long jdbcCallsMedian,
      long sqlStatementsMedian,
      double jdbcMsMedian,
      long externalCallsMedian,
      double externalMsMedian) {}

  private record PerformanceReport(
      String generatedAt,
      int warmups,
      int configuredSamples,
      List<ScenarioReport> scenarios) {}
}
