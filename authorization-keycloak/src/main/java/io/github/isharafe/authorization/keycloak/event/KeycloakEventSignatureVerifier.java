package io.github.isharafe.authorization.keycloak.event;

import io.github.isharafe.authorization.keycloak.config.AuthorizationKeycloakProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class KeycloakEventSignatureVerifier {
  private static final String PREFIX = "sha256=";

  private final AuthorizationKeycloakProperties properties;
  private final Clock clock;

  public KeycloakEventSignatureVerifier(
      AuthorizationKeycloakProperties properties, Clock clock) {
    properties.validate();
    this.properties = properties;
    this.clock = clock;
  }

  public void verify(String timestampHeader, String signatureHeader, byte[] body) {
    if (timestampHeader == null || signatureHeader == null || body == null)
      throw new KeycloakEventAuthenticationException("Missing event signature headers");
    Instant signedAt;
    try {
      signedAt = Instant.ofEpochSecond(Long.parseLong(timestampHeader));
    } catch (RuntimeException exception) {
      throw new KeycloakEventAuthenticationException("Invalid event signature timestamp", exception);
    }
    Duration age = Duration.between(signedAt, Instant.now(clock)).abs();
    if (age.compareTo(properties.getEvents().getMaxClockSkew()) > 0)
      throw new KeycloakEventAuthenticationException("Event signature timestamp is outside the allowed window");

    if (!signatureHeader.startsWith(PREFIX))
      throw new KeycloakEventAuthenticationException("Invalid event signature format");
    byte[] supplied;
    try {
      supplied = HexFormat.of().parseHex(signatureHeader.substring(PREFIX.length()));
    } catch (IllegalArgumentException exception) {
      throw new KeycloakEventAuthenticationException("Invalid event signature format", exception);
    }
    byte[] expected = signature(timestampHeader, body);
    if (!MessageDigest.isEqual(expected, supplied))
      throw new KeycloakEventAuthenticationException("Event signature does not match");
  }

  private byte[] signature(String timestamp, byte[] body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(
          new SecretKeySpec(
              properties.getEvents().getSecret().getBytes(StandardCharsets.UTF_8),
              "HmacSHA256"));
      mac.update(timestamp.getBytes(StandardCharsets.US_ASCII));
      mac.update((byte) '.');
      return mac.doFinal(body);
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("HMAC-SHA256 is not available", exception);
    }
  }
}
