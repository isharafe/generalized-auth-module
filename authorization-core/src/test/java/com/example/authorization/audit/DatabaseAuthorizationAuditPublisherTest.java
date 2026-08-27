package com.example.authorization.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.authorization.domain.AuditEventKind;
import com.example.authorization.domain.AuthorizationChangeAuditEvent;
import com.example.authorization.domain.AuthorizationDecision;
import com.example.authorization.domain.AuthorizationReason;
import com.example.authorization.domain.AuthenticatedIdentity;
import com.example.authorization.domain.AuthorizationResult;
import com.example.authorization.domain.ProtectedResource;
import com.example.authorization.domain.ResourceType;
import com.example.authorization.persistence.entity.AuditEventEntity;
import com.example.authorization.persistence.repository.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

class DatabaseAuthorizationAuditPublisherTest {
  @Test
  void storesTheDocumentedAuthorizationAuditFields() {
    AuditEventRepository repository = mock(AuditEventRepository.class);
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    DatabaseAuthorizationAuditPublisher publisher =
        new DatabaseAuthorizationAuditPublisher(repository);

    MDC.put("correlationId", "request-123");
    try {
      publisher.publishDecision(
          AuthorizationResult.of(
              AuthorizationDecision.DENIED,
              AuthorizationReason.NO_MATCHING_RULE,
              null,
              null,
              new AuthenticatedIdentity("local", "alice", "alice")),
          new ProtectedResource(ResourceType.URL, "GET:/api/items/a:b"));
    } finally {
      MDC.remove("correlationId");
    }

    ArgumentCaptor<AuditEventEntity> event = ArgumentCaptor.forClass(AuditEventEntity.class);
    verify(repository).save(event.capture());
    AuditEventEntity value = event.getValue();
    assertThat(value.getEventKind()).isEqualTo(AuditEventKind.DECISION);
    assertThat(value.getActorIssuer()).isEqualTo("local");
    assertThat(value.getActorSubject()).isEqualTo("alice");
    assertThat(value.getTarget()).isEqualTo("/api/items/a:b");
    assertThat(value.getAction()).isEqualTo("GET");
    assertThat(value.getCorrelationId()).isEqualTo("request-123");
    assertThat(value.getDetailsJson()).isEqualTo("{\"resourceType\":\"URL\"}");
    assertThat(value.getRequestMethod()).isEqualTo("GET");
    assertThat(value.getRequestPath()).isEqualTo("/api/items/a:b");
  }

  @Test
  void storesAnOpaqueUiIdentifierAsTheRequestPath() {
    AuditEventRepository repository = mock(AuditEventRepository.class);
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    DatabaseAuthorizationAuditPublisher publisher =
        new DatabaseAuthorizationAuditPublisher(repository);

    publisher.publishDecision(
        AuthorizationResult.of(
            AuthorizationDecision.GRANTED,
            AuthorizationReason.PUBLIC_RESOURCE,
            null,
            null,
            null),
        new ProtectedResource(ResourceType.UI, "employees.edit"));

    ArgumentCaptor<AuditEventEntity> event = ArgumentCaptor.forClass(AuditEventEntity.class);
    verify(repository).save(event.capture());
    assertThat(event.getValue().getAction()).isEqualTo("ACCESS");
    assertThat(event.getValue().getTarget()).isEqualTo("employees.edit");
    assertThat(event.getValue().getCorrelationId()).isNotBlank();
    assertThat(event.getValue().getRequestMethod()).isNull();
    assertThat(event.getValue().getRequestPath()).isEqualTo("employees.edit");
  }

  @Test
  void storesAuthorizationChangeFieldsWithoutDecisionFields() {
    AuditEventRepository repository = mock(AuditEventRepository.class);
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    DatabaseAuthorizationAuditPublisher publisher =
        new DatabaseAuthorizationAuditPublisher(repository);

    MDC.put("correlationId", "change-123");
    try {
      publisher.publishChange(
          new AuthorizationChangeAuditEvent(
              "ROLE_UPDATED",
              "local",
              "manager",
              "ROLE:HR_MANAGER",
              "UPDATE",
              "{\"version\":2}"));
    } finally {
      MDC.remove("correlationId");
    }

    ArgumentCaptor<AuditEventEntity> event = ArgumentCaptor.forClass(AuditEventEntity.class);
    verify(repository).save(event.capture());
    AuditEventEntity value = event.getValue();
    assertThat(value.getEventKind()).isEqualTo(AuditEventKind.CHANGE);
    assertThat(value.getEventType()).isEqualTo("ROLE_UPDATED");
    assertThat(value.getActorIssuer()).isEqualTo("local");
    assertThat(value.getActorSubject()).isEqualTo("manager");
    assertThat(value.getTarget()).isEqualTo("ROLE:HR_MANAGER");
    assertThat(value.getAction()).isEqualTo("UPDATE");
    assertThat(value.getCorrelationId()).isEqualTo("change-123");
    assertThat(value.getDetailsJson()).isEqualTo("{\"version\":2}");
    assertThat(value.getRequestMethod()).isNull();
    assertThat(value.getRequestPath()).isNull();
    assertThat(value.getDecision()).isNull();
    assertThat(value.getReason()).isNull();
    assertThat(value.getRuleCode()).isNull();
    assertThat(value.getPermissionCode()).isNull();
  }
}
