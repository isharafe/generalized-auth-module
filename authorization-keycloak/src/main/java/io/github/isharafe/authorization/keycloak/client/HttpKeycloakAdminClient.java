package io.github.isharafe.authorization.keycloak.client;

import io.github.isharafe.authorization.keycloak.config.AuthorizationKeycloakProperties;
import io.github.isharafe.authorization.observability.NoOpAuthorizationObservation;
import io.github.isharafe.authorization.spi.AuthorizationObservation;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class HttpKeycloakAdminClient implements KeycloakAdminClient {
  private final AuthorizationKeycloakProperties properties;
  private final ObjectMapper mapper;
  private final HttpClient http;
  private final AuthorizationObservation observation;
  private volatile Token token;

  public HttpKeycloakAdminClient(
      AuthorizationKeycloakProperties properties, ObjectMapper mapper) {
    this(properties, mapper, new NoOpAuthorizationObservation());
  }

  public HttpKeycloakAdminClient(
      AuthorizationKeycloakProperties properties,
      ObjectMapper mapper,
      AuthorizationObservation observation) {
    properties.validate();
    this.properties = properties;
    this.mapper = mapper;
    this.observation = observation;
    this.http =
        HttpClient.newBuilder()
            .connectTimeout(properties.getHttp().getConnectTimeout())
            .build();
  }

  @Override
  public List<KeycloakUser> users() {
    return paged(
        first ->
            "/admin/realms/"
                + segment(properties.getRealm())
                + "/users?briefRepresentation=false&first="
                + first
                + "&max="
                + properties.getSync().getPageSize(),
        new TypeReference<>() {});
  }

  @Override
  public Optional<KeycloakUser> user(String id) {
    String body =
        adminGet(
            "/admin/realms/"
                + segment(properties.getRealm())
                + "/users/"
                + segment(id),
            true);
    if (body == null) return Optional.empty();
    return Optional.of(read(body, KeycloakUser.class));
  }

  @Override
  public List<KeycloakGroup> groups(String userId) {
    return paged(
        first ->
            "/admin/realms/"
                + segment(properties.getRealm())
                + "/users/"
                + segment(userId)
                + "/groups?first="
                + first
                + "&max="
                + properties.getSync().getPageSize(),
        new TypeReference<>() {});
  }

  @Override
  public List<KeycloakRole> realmRoles(String userId) {
    return paged(
        first ->
            "/admin/realms/"
                + segment(properties.getRealm())
                + "/users/"
                + segment(userId)
                + "/role-mappings/realm?first="
                + first
                + "&max="
                + properties.getSync().getPageSize(),
        new TypeReference<>() {});
  }

  private <T> List<T> paged(PathFactory path, TypeReference<List<T>> type) {
    List<T> values = new ArrayList<>();
    int first = 0;
    while (true) {
      List<T> page = read(adminGet(path.path(first), false), type);
      values.addAll(page);
      if (page.size() < properties.getSync().getPageSize()) return List.copyOf(values);
      first += page.size();
    }
  }

  private String adminGet(String path, boolean notFoundAllowed) {
    KeycloakClientException last = null;
    for (int attempt = 1; attempt <= properties.getHttp().getMaxAttempts(); attempt++) {
      HttpRequest request =
          HttpRequest.newBuilder(uri(path))
              .timeout(properties.getHttp().getReadTimeout())
              .header("Accept", "application/json")
              .header("Authorization", "Bearer " + accessToken())
              .GET()
              .build();
      try {
        HttpResponse<String> response = send(request, operation(path));
        int status = response.statusCode();
        if (status >= 200 && status < 300) return response.body();
        if (status == 404 && notFoundAllowed) return null;
        if (status == 401) {
          token = null;
          last =
              new KeycloakClientException(
                  KeycloakFailureType.AUTHENTICATION,
                  status,
                  "Keycloak Admin API rejected the service-account token");
        } else {
          last = failure(status, "Keycloak Admin API GET " + path + " failed");
        }
        if (!retryable(status) || attempt == properties.getHttp().getMaxAttempts()) throw last;
      } catch (IOException exception) {
        last =
            new KeycloakClientException(
                KeycloakFailureType.TRANSPORT,
                null,
                "Unable to reach the Keycloak Admin API",
                exception);
        if (attempt == properties.getHttp().getMaxAttempts()) throw last;
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new KeycloakClientException(
            KeycloakFailureType.TRANSPORT,
            null,
            "Interrupted while calling the Keycloak Admin API",
            exception);
      }
      backoff(attempt);
    }
    throw last;
  }

  private synchronized String accessToken() {
    if (token != null && token.expiresAt().isAfter(Instant.now().plusSeconds(10))) {
      observation.recordExternalTokenCacheRequest("keycloak", true);
      return token.value();
    }
    observation.recordExternalTokenCacheRequest("keycloak", false);
    String form =
        "grant_type=client_credentials&client_id="
            + form(properties.getClientId())
            + "&client_secret="
            + form(properties.getClientSecret());
    HttpRequest request =
        HttpRequest.newBuilder(
                uri(
                    "/realms/"
                        + segment(properties.getRealm())
                        + "/protocol/openid-connect/token"))
            .timeout(properties.getHttp().getReadTimeout())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();
    KeycloakClientException last = null;
    for (int attempt = 1; attempt <= properties.getHttp().getMaxAttempts(); attempt++) {
      try {
        HttpResponse<String> response = send(request, "service_token");
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
          JsonNode json = mapper.readTree(response.body());
          String value = json.path("access_token").asText(null);
          long expiresIn = json.path("expires_in").asLong(60);
          if (value == null || value.isBlank())
            throw new KeycloakClientException(
                KeycloakFailureType.INVALID_RESPONSE,
                response.statusCode(),
                "Keycloak token response did not contain access_token");
          token = new Token(value, Instant.now().plusSeconds(Math.max(1, expiresIn)));
          return token.value();
        }
        last = failure(response.statusCode(), "Keycloak service-account authentication failed");
        if (!retryable(response.statusCode())
            || attempt == properties.getHttp().getMaxAttempts()) throw last;
      } catch (IOException exception) {
        last =
            new KeycloakClientException(
                KeycloakFailureType.TRANSPORT,
                null,
                "Unable to authenticate with Keycloak",
                exception);
        if (attempt == properties.getHttp().getMaxAttempts()) throw last;
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new KeycloakClientException(
            KeycloakFailureType.TRANSPORT,
            null,
            "Interrupted while authenticating with Keycloak",
            exception);
      }
      backoff(attempt);
    }
    throw last;
  }

  private KeycloakClientException failure(int status, String message) {
    KeycloakFailureType type =
        status == 401 || status == 403
            ? KeycloakFailureType.AUTHENTICATION
            : status == 404
                ? KeycloakFailureType.NOT_FOUND
                : status == 429
                    ? KeycloakFailureType.RATE_LIMITED
                    : status >= 500
                        ? KeycloakFailureType.REMOTE_SERVER
                        : KeycloakFailureType.INVALID_RESPONSE;
    return new KeycloakClientException(type, status, message + " (HTTP " + status + ")");
  }

  private boolean retryable(int status) {
    return status == 401 || status == 429 || status >= 500;
  }

  private HttpResponse<String> send(HttpRequest request, String operation)
      throws IOException, InterruptedException {
    long started = System.nanoTime();
    try {
      HttpResponse<String> response =
          http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      observation.recordExternalRequest(
          "keycloak",
          operation,
          request.method(),
          outcome(response.statusCode()),
          Duration.ofNanos(System.nanoTime() - started));
      return response;
    } catch (IOException exception) {
      observation.recordExternalRequest(
          "keycloak",
          operation,
          request.method(),
          "transport",
          Duration.ofNanos(System.nanoTime() - started));
      throw exception;
    } catch (InterruptedException exception) {
      observation.recordExternalRequest(
          "keycloak",
          operation,
          request.method(),
          "interrupted",
          Duration.ofNanos(System.nanoTime() - started));
      throw exception;
    }
  }

  private String operation(String path) {
    if (path.endsWith("/groups") || path.contains("/groups?")) return "groups";
    if (path.contains("/role-mappings/realm")) return "realm_roles";
    if (path.contains("/users/")) return "user";
    return "users";
  }

  private String outcome(int status) {
    if (status >= 200 && status < 300) return "success";
    if (status == 401 || status == 403) return "authentication";
    if (status == 404) return "not_found";
    if (status == 429) return "rate_limited";
    if (status >= 500) return "remote_server";
    return "invalid_response";
  }

  private void backoff(int attempt) {
    long millis = properties.getHttp().getRetryBackoff().toMillis() * attempt;
    if (millis == 0) return;
    try {
      Thread.sleep(millis);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new KeycloakClientException(
          KeycloakFailureType.TRANSPORT, null, "Interrupted during Keycloak retry backoff", exception);
    }
  }

  private URI uri(String path) {
    return URI.create(properties.normalizedBaseUrl() + path);
  }

  private String segment(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private String form(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private <T> T read(String body, Class<T> type) {
    try {
      return mapper.readValue(body, type);
    } catch (IOException exception) {
      throw new KeycloakClientException(
          KeycloakFailureType.INVALID_RESPONSE,
          null,
          "Keycloak returned an invalid JSON response",
          exception);
    }
  }

  private <T> T read(String body, TypeReference<T> type) {
    try {
      return mapper.readValue(body, type);
    } catch (IOException exception) {
      throw new KeycloakClientException(
          KeycloakFailureType.INVALID_RESPONSE,
          null,
          "Keycloak returned an invalid JSON response",
          exception);
    }
  }

  private interface PathFactory {
    String path(int first);
  }

  private record Token(String value, Instant expiresAt) {}
}
