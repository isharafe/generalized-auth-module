package com.example.authorization.keycloak.event;

import com.example.authorization.domain.AuthenticatedIdentity;
import com.example.authorization.domain.IdentityChangeEvent;
import com.example.authorization.domain.IdentityChangeProcessingResult;
import com.example.authorization.keycloak.config.AuthorizationKeycloakProperties;
import com.example.authorization.persistence.service.IdentityChangeEventConflictException;
import com.example.authorization.persistence.service.IdentityChangeProcessingException;
import com.example.authorization.spi.IdentityChangeEventProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("${authorization.keycloak.events.path:/authorization/keycloak/events}")
public final class KeycloakIdentityChangeController {
  private final AuthorizationKeycloakProperties properties;
  private final KeycloakEventSignatureVerifier signatures;
  private final IdentityChangeEventProcessor events;
  private final ObjectMapper objectMapper;

  public KeycloakIdentityChangeController(
      AuthorizationKeycloakProperties properties,
      KeycloakEventSignatureVerifier signatures,
      IdentityChangeEventProcessor events,
      ObjectMapper objectMapper) {
    this.properties = properties;
    this.signatures = signatures;
    this.events = events;
    this.objectMapper = objectMapper;
  }

  @PostMapping(consumes = "application/json", produces = "application/json")
  public ResponseEntity<KeycloakIdentityChangeResponse> receive(
      @RequestHeader("X-Authorization-Timestamp") String timestamp,
      @RequestHeader("X-Authorization-Signature") String signature,
      @RequestBody byte[] body) {
    try {
      signatures.verify(timestamp, signature, body);
    } catch (KeycloakEventAuthenticationException exception) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid event signature");
    }

    KeycloakIdentityChangeRequest request = parse(body);
    IdentityChangeProcessingResult result;
    try {
      result =
          events.process(
              new IdentityChangeEvent(
                  request.eventId(),
                  "KEYCLOAK",
                  new AuthenticatedIdentity(
                      properties.resolvedIssuer(), request.userId(), null),
                  request.type(),
                  request.parsedOccurredAt()));
    } catch (IdentityChangeEventConflictException exception) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
    } catch (IdentityChangeProcessingException exception) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Identity synchronization failed");
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
    }
    HttpStatus status =
        result == IdentityChangeProcessingResult.IN_PROGRESS
            ? HttpStatus.ACCEPTED
            : HttpStatus.OK;
    return ResponseEntity.status(status)
        .body(new KeycloakIdentityChangeResponse(request.eventId(), result));
  }

  private KeycloakIdentityChangeRequest parse(byte[] body) {
    try {
      return objectMapper.readValue(body, KeycloakIdentityChangeRequest.class);
    } catch (IOException | IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid identity event payload");
    }
  }

  public record KeycloakIdentityChangeResponse(
      String eventId, IdentityChangeProcessingResult result) {}
}
