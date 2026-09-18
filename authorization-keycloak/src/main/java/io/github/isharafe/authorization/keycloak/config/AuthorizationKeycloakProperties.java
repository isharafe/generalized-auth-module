package io.github.isharafe.authorization.keycloak.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("authorization.keycloak")
public class AuthorizationKeycloakProperties {
  private boolean enabled = true;
  private String baseUrl;
  private String realm;
  private String clientId;
  private String clientSecret;
  private String issuer;
  private final Sync sync = new Sync();
  private final Http http = new Http();
  private final Events events = new Events();

  public String resolvedIssuer() {
    if (issuer != null && !issuer.isBlank()) return issuer;
    return normalizedBaseUrl() + "/realms/" + realm;
  }

  public String normalizedBaseUrl() {
    if (baseUrl == null) return null;
    return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }

  public void validate() {
    required(baseUrl, "authorization.keycloak.base-url");
    required(realm, "authorization.keycloak.realm");
    required(clientId, "authorization.keycloak.client-id");
    required(clientSecret, "authorization.keycloak.client-secret");
    if (sync.pageSize < 1 || sync.pageSize > 1000)
      throw new IllegalStateException("authorization.keycloak.sync.page-size must be between 1 and 1000");
    if (http.maxAttempts < 1)
      throw new IllegalStateException("authorization.keycloak.http.max-attempts must be at least 1");
    if (http.connectTimeout == null || http.connectTimeout.isZero() || http.connectTimeout.isNegative())
      throw new IllegalStateException("authorization.keycloak.http.connect-timeout must be positive");
    if (http.readTimeout == null || http.readTimeout.isZero() || http.readTimeout.isNegative())
      throw new IllegalStateException("authorization.keycloak.http.read-timeout must be positive");
    if (http.retryBackoff == null || http.retryBackoff.isNegative())
      throw new IllegalStateException("authorization.keycloak.http.retry-backoff must not be negative");
    if (events.enabled) {
      required(events.secret, "authorization.keycloak.events.secret");
      if (events.secret.getBytes(StandardCharsets.UTF_8).length < 32)
        throw new IllegalStateException(
            "authorization.keycloak.events.secret must contain at least 32 bytes");
      required(events.path, "authorization.keycloak.events.path");
      if (!events.path.startsWith("/") || events.path.length() > 200)
        throw new IllegalStateException(
            "authorization.keycloak.events.path must start with / and not exceed 200 characters");
      if (events.maxClockSkew == null
          || events.maxClockSkew.isZero()
          || events.maxClockSkew.isNegative())
        throw new IllegalStateException(
            "authorization.keycloak.events.max-clock-skew must be positive");
    }
  }

  private void required(String value, String property) {
    if (value == null || value.isBlank()) throw new IllegalStateException(property + " is required");
  }

  @Getter
  @Setter
  public static class Sync {
    private boolean enabled = true;
    private int pageSize = 100;
    private String fullCron = "-";
    private String incrementalCron = "-";
  }

  @Getter
  @Setter
  public static class Http {
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(15);
    private int maxAttempts = 3;
    private Duration retryBackoff = Duration.ofMillis(250);
  }

  @Getter
  @Setter
  public static class Events {
    private boolean enabled;
    private String path = "/authorization/keycloak/events";
    private String secret;
    private Duration maxClockSkew = Duration.ofMinutes(5);
  }
}
