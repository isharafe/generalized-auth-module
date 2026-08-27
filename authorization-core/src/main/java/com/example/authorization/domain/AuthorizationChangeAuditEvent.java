package com.example.authorization.domain;

public record AuthorizationChangeAuditEvent(
    String eventType,
    String actorIssuer,
    String actorSubject,
    String target,
    String action,
    String detailsJson) {}
