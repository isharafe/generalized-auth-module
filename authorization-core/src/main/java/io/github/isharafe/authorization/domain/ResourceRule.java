package io.github.isharafe.authorization.domain;

import java.util.Objects;

public record ResourceRule(
    String code,
    ResourceType resourceType,
    String pattern,
    AccessMode accessMode,
    int priority,
    boolean enabled) {
  public ResourceRule {
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(resourceType, "resourceType");
    pattern = Objects.requireNonNull(pattern, "pattern");
    if (pattern.isBlank()) throw new IllegalArgumentException("pattern must not be blank");
    Objects.requireNonNull(accessMode, "accessMode");
  }
}
