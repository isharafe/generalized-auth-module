package com.example.authorization.keycloak.config;

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
}
