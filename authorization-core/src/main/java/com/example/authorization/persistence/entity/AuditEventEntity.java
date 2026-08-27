package com.example.authorization.persistence.entity;

import com.example.authorization.domain.AuditEventKind;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;

@Getter
@Entity
@Table(name = "AUTH_AUDIT_EVENT")
public class AuditEventEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Instant timestamp;

  @Enumerated(EnumType.STRING)
  @Column(name = "EVENT_KIND", nullable = false, length = 30)
  private AuditEventKind eventKind;

  @Column(name = "EVENT_TYPE", nullable = false, length = 100)
  private String eventType;

  @Column(name = "ACTOR_ISSUER", length = 500)
  private String actorIssuer;

  @Column(name = "ACTOR_SUBJECT", length = 500)
  private String actorSubject;

  @Column(name = "EVENT_TARGET", length = 1000)
  private String target;

  @Column(name = "AUDIT_ACTION", length = 100)
  private String action;

  @Column(name = "CORRELATION_ID", length = 255)
  private String correlationId;

  @Column(name = "DETAILS_JSON", length = 4000)
  private String detailsJson;

  @Column(name = "REQUEST_METHOD", length = 20)
  private String requestMethod;

  @Column(name = "REQUEST_PATH", length = 1000)
  private String requestPath;

  @Column(length = 30)
  private String decision;

  @Column(length = 100)
  private String reason;

  @Column(name = "RULE_CODE", length = 100)
  private String ruleCode;

  @Column(name = "PERMISSION_CODE", length = 100)
  private String permissionCode;

  public static AuditEventEntity authorizationChange(
      AuditEventKind eventKind,
      String type,
      String actorIssuer,
      String actorSubject,
      String target,
      String action,
      String correlationId,
      String detailsJson) {
    requireKind(eventKind, AuditEventKind.CHANGE);
    AuditEventEntity value = new AuditEventEntity();
    value.timestamp = Instant.now();
    value.eventKind = eventKind;
    value.eventType = type;
    value.actorIssuer = actorIssuer;
    value.actorSubject = actorSubject;
    value.target = target;
    value.action = action;
    value.correlationId = correlationId;
    value.detailsJson = detailsJson;
    return value;
  }

  public static AuditEventEntity authorizationDecision(
      AuditEventKind eventKind,
      String type,
      String actorIssuer,
      String actorSubject,
      String target,
      String action,
      String correlationId,
      String detailsJson,
      String method,
      String path,
      String decision,
      String reason,
      String rule,
      String permission) {
    requireKind(eventKind, AuditEventKind.DECISION);
    AuditEventEntity value = new AuditEventEntity();
    value.timestamp = Instant.now();
    value.eventKind = eventKind;
    value.eventType = type;
    value.actorIssuer = actorIssuer;
    value.actorSubject = actorSubject;
    value.target = target;
    value.action = action;
    value.correlationId = correlationId;
    value.detailsJson = detailsJson;
    value.requestMethod = method;
    value.requestPath = path;
    value.decision = decision;
    value.reason = reason;
    value.ruleCode = rule;
    value.permissionCode = permission;
    return value;
  }

  private static void requireKind(AuditEventKind actual, AuditEventKind expected) {
    if (actual != expected) {
      throw new IllegalArgumentException(
          "Expected audit event kind " + expected + " but received " + actual);
    }
  }
}
