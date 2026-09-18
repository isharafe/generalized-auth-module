package io.github.isharafe.authorization.keycloak.event;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class KeycloakEventTestSupport {
  private KeycloakEventTestSupport() {}

  static String signature(String secret, String timestamp, byte[] body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      mac.update(timestamp.getBytes(StandardCharsets.US_ASCII));
      mac.update((byte) '.');
      return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
