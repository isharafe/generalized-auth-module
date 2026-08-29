package com.example.authorization.keycloak.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.authorization.keycloak.config.AuthorizationKeycloakProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class KeycloakEventSignatureVerifierTest {
  private static final Instant NOW = Instant.parse("2026-08-29T12:00:00Z");
  private static final String SECRET = "0123456789abcdef0123456789abcdef";

  @Test
  void acceptsAValidSignatureAndRejectsTampering() {
    AuthorizationKeycloakProperties properties = properties();
    KeycloakEventSignatureVerifier verifier =
        new KeycloakEventSignatureVerifier(properties, Clock.fixed(NOW, ZoneOffset.UTC));
    byte[] body = "{\"eventId\":\"evt-1\"}".getBytes(StandardCharsets.UTF_8);
    String timestamp = Long.toString(NOW.getEpochSecond());
    String signature = KeycloakEventTestSupport.signature(SECRET, timestamp, body);

    assertThatCode(() -> verifier.verify(timestamp, signature, body)).doesNotThrowAnyException();
    assertThatThrownBy(
            () ->
                verifier.verify(
                    timestamp,
                    signature,
                    "{\"eventId\":\"tampered\"}".getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(KeycloakEventAuthenticationException.class)
        .hasMessageContaining("does not match");
  }

  @Test
  void rejectsExpiredAndMalformedSignatures() {
    AuthorizationKeycloakProperties properties = properties();
    KeycloakEventSignatureVerifier verifier =
        new KeycloakEventSignatureVerifier(properties, Clock.fixed(NOW, ZoneOffset.UTC));
    byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
    String staleTimestamp = Long.toString(NOW.minusSeconds(301).getEpochSecond());

    assertThatThrownBy(
            () ->
                verifier.verify(
                    staleTimestamp,
                    KeycloakEventTestSupport.signature(SECRET, staleTimestamp, body),
                    body))
        .isInstanceOf(KeycloakEventAuthenticationException.class)
        .hasMessageContaining("outside the allowed window");
    assertThatThrownBy(
            () -> verifier.verify(Long.toString(NOW.getEpochSecond()), "invalid", body))
        .isInstanceOf(KeycloakEventAuthenticationException.class)
        .hasMessageContaining("format");
  }

  private AuthorizationKeycloakProperties properties() {
    AuthorizationKeycloakProperties properties = new AuthorizationKeycloakProperties();
    properties.setBaseUrl("https://id.example");
    properties.setRealm("company");
    properties.setClientId("sync-client");
    properties.setClientSecret("client-secret");
    properties.getEvents().setEnabled(true);
    properties.getEvents().setSecret(SECRET);
    return properties;
  }
}
