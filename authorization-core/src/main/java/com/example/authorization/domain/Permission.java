package com.example.authorization.domain;

import java.util.Objects;

public record Permission(
    String code,
    String name,
    String description,
    ResourceType resourceType,
    String pattern,
    boolean enabled) {
  public Permission {
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(resourceType, "resourceType");
    pattern = Objects.requireNonNull(pattern, "pattern");
    if (pattern.isBlank()) throw new IllegalArgumentException("pattern must not be blank");
  }
}
