package com.example.authorization.domain;

public record AdminAuditEvent(
    String eventType,
    String actorIssuer,
    String actorSubject,
    String target,
    String action,
    String detailsJson) {}
