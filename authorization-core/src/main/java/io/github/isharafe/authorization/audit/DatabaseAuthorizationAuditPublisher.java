package io.github.isharafe.authorization.audit;

import io.github.isharafe.authorization.domain.AuditEventKind;
import io.github.isharafe.authorization.domain.AuthorizationChangeAuditEvent;
import io.github.isharafe.authorization.domain.AuthorizationResult;
import io.github.isharafe.authorization.domain.ProtectedResource;
import io.github.isharafe.authorization.domain.ResourceType;
import io.github.isharafe.authorization.persistence.entity.AuditEventEntity;
import io.github.isharafe.authorization.persistence.repository.AuditEventRepository;
import io.github.isharafe.authorization.spi.AuthorizationAuditPublisher;
import io.github.isharafe.authorization.util.HttpResourceUtil;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
public class DatabaseAuthorizationAuditPublisher implements AuthorizationAuditPublisher {
  private final AuditEventRepository repository;

  @Override
  public void publishDecision(AuthorizationResult result, ProtectedResource resource) {
    try {
      String requestMethod =
          resource.resourceType() == ResourceType.URL
              ? HttpResourceUtil.method(resource.pattern())
              : null;
      String requestPath =
          resource.resourceType() == ResourceType.URL
              ? HttpResourceUtil.path(resource.pattern())
              : resource.pattern();
      Actor actor = actor(result.identityKey());
      String action = requestMethod == null ? "ACCESS" : requestMethod;
      repository.save(
          AuditEventEntity.authorizationDecision(
              AuditEventKind.DECISION,
              "AUTHORIZATION_" + result.decision(),
              actor.issuer(),
              actor.subject(),
              requestPath,
              action,
              correlationId(),
              "{\"resourceType\":\"" + resource.resourceType().name() + "\"}",
              requestMethod,
              requestPath,
              result.decision().name(),
              result.reason().name(),
              result.matchedRuleCode(),
              result.matchedPermissionCode()));
    } catch (RuntimeException exception) {
      log.warn("Unable to persist authorization decision audit event", exception);
    }
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void publishChange(AuthorizationChangeAuditEvent event) {
    try {
      repository.save(
          AuditEventEntity.authorizationChange(
              AuditEventKind.CHANGE,
              event.eventType(),
              event.actorIssuer(),
              event.actorSubject(),
              event.target(),
              event.action(),
              correlationId(),
              event.detailsJson()));
    } catch (RuntimeException exception) {
      log.warn("Unable to persist authorization change audit event", exception);
    }
  }

  private Actor actor(String identityKey) {
    if (identityKey == null) return new Actor(null, null);
    int separator = identityKey.indexOf('\0');
    if (separator < 0) return new Actor(null, identityKey);
    return new Actor(identityKey.substring(0, separator), identityKey.substring(separator + 1));
  }

  private String correlationId() {
    String value = MDC.get("correlationId");
    if (value == null || value.isBlank()) value = MDC.get("traceId");
    if (value == null || value.isBlank()) value = UUID.randomUUID().toString();
    return value.length() <= 255 ? value : value.substring(0, 255);
  }

  private record Actor(String issuer, String subject) {}
}
