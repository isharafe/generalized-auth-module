package com.example.authorization.security;

import com.example.authorization.domain.ResourceType;
import com.example.authorization.spi.ResourcePatternMatcher;
import com.example.authorization.util.UIResourceUtil;

public final class UiResourcePatternMatcher implements ResourcePatternMatcher {
  @Override
  public ResourceType resourceType() {
    return ResourceType.UI;
  }

  @Override
  public boolean matches(String expectedPattern, String actualPattern) {
    return UIResourceUtil.matches(expectedPattern, actualPattern);
  }

  @Override
  public int compareSpecificity(String leftPattern, String rightPattern) {
    return 0;
  }
}
