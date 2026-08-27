package com.example.authorization.keycloak.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.authorization.keycloak.config.AuthorizationKeycloakProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpKeycloakAdminClientTest {
  private HttpServer server;
  private AuthorizationKeycloakProperties properties;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.start();
    properties = new AuthorizationKeycloakProperties();
    properties.setBaseUrl("http://localhost:" + server.getAddress().getPort());
    properties.setRealm("company");
    properties.setClientId("sync-client");
    properties.setClientSecret("secret value");
    properties.getSync().setPageSize(2);
    properties.getHttp().setRetryBackoff(Duration.ZERO);
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void authenticatesPaginatesAndLoadsGroupsAndRealmRoles() {
    AtomicInteger tokens = new AtomicInteger();
    server.createContext(
        "/realms/company/protocol/openid-connect/token",
        exchange -> {
          tokens.incrementAndGet();
          String requestBody = body(exchange);
          assertThat(requestBody).contains("grant_type=client_credentials");
          assertThat(requestBody).contains("client_secret=secret+value");
          json(exchange, 200, "{\"access_token\":\"service-token\",\"expires_in\":300}");
        });
    server.createContext(
        "/admin/realms/company/users",
        exchange -> {
          assertBearer(exchange);
          String query = exchange.getRequestURI().getQuery();
          if (query.contains("first=0"))
            json(
                exchange,
                200,
                "[{\"id\":\"u1\",\"username\":\"alice\",\"enabled\":true,\"emailVerified\":true},"
                    + "{\"id\":\"u2\",\"username\":\"bob\",\"enabled\":true}]");
          else
            json(
                exchange,
                200,
                "[{\"id\":\"u3\",\"username\":\"carol\",\"enabled\":true}]");
        });
    server.createContext(
        "/admin/realms/company/users/u1/groups",
        exchange -> {
          assertBearer(exchange);
          json(exchange, 200, "[{\"id\":\"g1\",\"name\":\"Finance\",\"path\":\"/Finance\",\"subGroupCount\":0}]");
        });
    server.createContext(
        "/admin/realms/company/users/u1/role-mappings/realm",
        exchange -> {
          assertBearer(exchange);
          json(exchange, 200, "[{\"id\":\"r1\",\"name\":\"approver\",\"composite\":false}]");
        });
    server.createContext(
        "/admin/realms/company/users/u1",
        exchange -> {
          assertBearer(exchange);
          json(exchange, 200, "{\"id\":\"u1\",\"username\":\"alice\",\"enabled\":true,\"emailVerified\":true}");
        });

    KeycloakAdminClient client = new HttpKeycloakAdminClient(properties, new ObjectMapper());

    assertThat(client.users()).extracting(KeycloakUser::id).containsExactly("u1", "u2", "u3");
    assertThat(client.user("u1")).get().extracting(KeycloakUser::username).isEqualTo("alice");
    assertThat(client.groups("u1")).extracting(KeycloakGroup::path).containsExactly("/Finance");
    assertThat(client.realmRoles("u1")).extracting(KeycloakRole::name).containsExactly("approver");
    assertThat(tokens).hasValue(1);
  }

  @Test
  void retriesRemoteFailuresAndReturnsATypedException() {
    AtomicInteger requests = new AtomicInteger();
    tokenEndpoint();
    server.createContext(
        "/admin/realms/company/users",
        exchange -> {
          requests.incrementAndGet();
          json(exchange, 503, "{\"error\":\"unavailable\"}");
        });
    properties.getHttp().setMaxAttempts(2);

    KeycloakAdminClient client = new HttpKeycloakAdminClient(properties, new ObjectMapper());

    assertThatThrownBy(client::users)
        .isInstanceOfSatisfying(
            KeycloakClientException.class,
            exception -> {
              assertThat(exception.failureType()).isEqualTo(KeycloakFailureType.REMOTE_SERVER);
              assertThat(exception.status()).isEqualTo(503);
            });
    assertThat(requests).hasValue(2);
  }

  @Test
  void enforcesTheConfiguredReadTimeout() {
    tokenEndpoint();
    server.createContext(
        "/admin/realms/company/users",
        exchange -> {
          try {
            Thread.sleep(200);
            json(exchange, 200, "[]");
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
          }
        });
    properties.getHttp().setReadTimeout(Duration.ofMillis(50));
    properties.getHttp().setMaxAttempts(1);

    KeycloakAdminClient client = new HttpKeycloakAdminClient(properties, new ObjectMapper());

    assertThatThrownBy(client::users)
        .isInstanceOfSatisfying(
            KeycloakClientException.class,
            exception ->
                assertThat(exception.failureType()).isEqualTo(KeycloakFailureType.TRANSPORT));
  }

  private void tokenEndpoint() {
    server.createContext(
        "/realms/company/protocol/openid-connect/token",
        exchange -> json(exchange, 200, "{\"access_token\":\"service-token\",\"expires_in\":300}"));
  }

  private void assertBearer(HttpExchange exchange) {
    assertThat(exchange.getRequestHeaders().getFirst("Authorization"))
        .isEqualTo("Bearer service-token");
  }

  private String body(HttpExchange exchange) throws IOException {
    return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
  }

  private void json(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
