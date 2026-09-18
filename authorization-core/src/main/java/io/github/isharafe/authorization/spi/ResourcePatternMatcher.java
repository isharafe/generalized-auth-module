package io.github.isharafe.authorization.spi;

import io.github.isharafe.authorization.domain.ResourceType;

public interface ResourcePatternMatcher {
  ResourceType resourceType();

  boolean matches(String expectedPattern, String actualPattern);

  default void validate(String pattern) {}

  /** Returns a negative value when the left pattern is more specific than the right pattern. */
  int compareSpecificity(String leftPattern, String rightPattern);
}
