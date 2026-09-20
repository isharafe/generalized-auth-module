package io.github.isharafe.authorization.security;

import io.github.isharafe.authorization.domain.ResourceType;
import io.github.isharafe.authorization.spi.ResourcePatternMatcher;
import java.util.Objects;

public final class UiResourcePatternMatcher implements ResourcePatternMatcher {
  @Override
  public ResourceType resourceType() {
    return ResourceType.UI;
  }

  @Override
  public boolean matches(String expectedPattern, String actualPattern) {
    return Objects.equals(expectedPattern, actualPattern);
  }

  @Override
  public int compareSpecificity(String leftPattern, String rightPattern) {
    return 0;
  }
}
