package io.github.isharafe.authorization.security;

import java.util.Objects;

/**
 * Observable metadata for a method/path policy declared by a {@code SecurityFilterChain}.
 *
 * <p>The orders must mirror the chain's first-match semantics. The pattern is the effective URL
 * pattern after accounting for both the chain matcher and its authorization matcher.
 */
public record UrlSecurityPolicy(
    String code,
    String pattern,
    UrlSecurityPolicyDecision decision,
    int chainOrder,
    int matcherOrder) {
  public UrlSecurityPolicy {
    code = Objects.requireNonNull(code, "code");
    if (code.isBlank()) throw new IllegalArgumentException("code must not be blank");
    pattern = Objects.requireNonNull(pattern, "pattern");
    if (pattern.isBlank()) throw new IllegalArgumentException("pattern must not be blank");
    Objects.requireNonNull(decision, "decision");
  }
}
