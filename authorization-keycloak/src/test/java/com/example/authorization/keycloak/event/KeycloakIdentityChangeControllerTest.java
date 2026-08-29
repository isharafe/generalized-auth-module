package com.example.authorization.keycloak.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.authorization.domain.IdentityChangeEvent;
import com.example.authorization.domain.IdentityChangeProcessingResult;
import com.example.authorization.keycloak.config.AuthorizationKeycloakProperties;
import com.example.authorization.spi.IdentityChangeEventProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class KeycloakIdentityChangeControllerTest {
  private static final Instant NOW = Instant.parse("2026-08-29T12:00:00Z");
  private static final String SECRET = "0123456789abcdef0123456789abcdef";

  @Test
  void verifiesAndTranslatesAUserEventToProviderNeutralTargetedProcessing() {
    AuthorizationKeycloakProperties properties = properties();
    AtomicReference<IdentityChangeEvent> received = new AtomicReference<>();
    IdentityChangeEventProcessor processor =
        event -> {
          received.set(event);
          return IdentityChangeProcessingResult.PROCESSED;
        };
    KeycloakIdentityChangeController controller = controller(properties, processor);
    byte[] body =
        """
        {"eventId":"evt-1","type":"GROUP_MEMBERSHIP_CHANGED","userId":"user-1",
         "occurredAt":"2026-08-29T11:59:50Z"}
        """
            .getBytes(StandardCharsets.UTF_8);
    String timestamp = Long.toString(NOW.getEpochSecond());

    var response =
        controller.receive(
            timestamp, KeycloakEventTestSupport.signature(SECRET, timestamp, body), body);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().result()).isEqualTo(IdentityChangeProcessingResult.PROCESSED);
    assertThat(received.get().sourceSystem()).isEqualTo("KEYCLOAK");
    assertThat(received.get().identity().issuer())
        .isEqualTo("https://id.example/realms/company");
    assertThat(received.get().identity().subject()).isEqualTo("user-1");
  }

  @Test
  void returnsAcceptedForAnEventAlreadyBeingProcessed() {
    AuthorizationKeycloakProperties properties = properties();
    KeycloakIdentityChangeController controller =
        controller(properties, event -> IdentityChangeProcessingResult.IN_PROGRESS);
    byte[] body =
        "{\"eventId\":\"evt-2\",\"type\":\"USER_UPDATED\",\"userId\":\"user-2\"}"
            .getBytes(StandardCharsets.UTF_8);
    String timestamp = Long.toString(NOW.getEpochSecond());

    var response =
        controller.receive(
            timestamp, KeycloakEventTestSupport.signature(SECRET, timestamp, body), body);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getBody().result()).isEqualTo(IdentityChangeProcessingResult.IN_PROGRESS);
  }

  @Test
  void rejectsAnInvalidSignatureBeforeParsingOrProcessing() {
    AuthorizationKeycloakProperties properties = properties();
    AtomicReference<IdentityChangeEvent> received = new AtomicReference<>();
    KeycloakIdentityChangeController controller =
        controller(
            properties,
            event -> {
              received.set(event);
              return IdentityChangeProcessingResult.PROCESSED;
            });

    assertThatThrownBy(
            () ->
                controller.receive(
                    Long.toString(NOW.getEpochSecond()),
                    "sha256=00",
                    "not-json".getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            exception ->
                assertThat(((ResponseStatusException) exception).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED));
    assertThat(received.get()).isNull();
  }

  @Test
  void rejectsAMalformedOccurrenceTimestampWithoutProcessing() {
    AuthorizationKeycloakProperties properties = properties();
    AtomicReference<IdentityChangeEvent> received = new AtomicReference<>();
    KeycloakIdentityChangeController controller =
        controller(
            properties,
            event -> {
              received.set(event);
              return IdentityChangeProcessingResult.PROCESSED;
            });
    byte[] body =
        ("{\"eventId\":\"evt-3\",\"type\":\"USER_UPDATED\",\"userId\":\"user-3\","
                + "\"occurredAt\":\"yesterday\"}")
            .getBytes(StandardCharsets.UTF_8);
    String timestamp = Long.toString(NOW.getEpochSecond());

    assertThatThrownBy(
            () ->
                controller.receive(
                    timestamp, KeycloakEventTestSupport.signature(SECRET, timestamp, body), body))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            exception ->
                assertThat(((ResponseStatusException) exception).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST));
    assertThat(received.get()).isNull();
  }

  private KeycloakIdentityChangeController controller(
      AuthorizationKeycloakProperties properties, IdentityChangeEventProcessor processor) {
    return new KeycloakIdentityChangeController(
        properties,
        new KeycloakEventSignatureVerifier(properties, Clock.fixed(NOW, ZoneOffset.UTC)),
        processor,
        new ObjectMapper().findAndRegisterModules());
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
