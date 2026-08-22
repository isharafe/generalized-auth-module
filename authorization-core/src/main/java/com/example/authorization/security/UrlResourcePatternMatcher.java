package com.example.authorization.security;

import com.example.authorization.domain.ResourceType;
import com.example.authorization.spi.ResourcePatternMatcher;
import com.example.authorization.util.HttpResourceUtil;
import org.springframework.web.util.pattern.PathPatternParser;

public final class UrlResourcePatternMatcher implements ResourcePatternMatcher {
  private static final PathPatternParser PARSER = PathPatternParser.defaultInstance;

  @Override
  public ResourceType resourceType() {
    return ResourceType.URL;
  }

  @Override
  public boolean matches(String expectedPattern, String actualPattern) {
    return HttpResourceUtil.methodMatches(expectedPattern, actualPattern)
        && HttpResourceUtil.pathMatches(expectedPattern, actualPattern);
  }

  @Override
  public void validate(String pattern) {
    String method = HttpResourceUtil.method(pattern);
    if (!(method.equals("*") || method.matches("[A-Z]+")))
      throw new IllegalArgumentException("Invalid HTTP method " + method);
    String path = HttpResourceUtil.path(pattern);
    if (!path.startsWith("/")) throw new IllegalArgumentException("Invalid URL pattern " + pattern);
    try {
      PARSER.parse(path);
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Invalid URL pattern " + pattern, exception);
    }
  }

  @Override
  public int compareSpecificity(String leftPattern, String rightPattern) {
    int comparison = Boolean.compare(isExactPath(rightPattern), isExactPath(leftPattern));
    if (comparison != 0) return comparison;
    comparison = Integer.compare(pathSpecificity(rightPattern), pathSpecificity(leftPattern));
    if (comparison != 0) return comparison;
    return Boolean.compare(isExactMethod(rightPattern), isExactMethod(leftPattern));
  }

  private boolean isExactPath(String pattern) {
    String path = HttpResourceUtil.path(pattern);
    return !path.contains("*") && !path.contains("{");
  }

  private boolean isExactMethod(String pattern) {
    return !HttpResourceUtil.method(pattern).equals("*");
  }

  private int pathSpecificity(String pattern) {
    int score = 0;
    for (String segment : HttpResourceUtil.path(pattern).split("/"))
      score += segment.equals("**") ? 0 : segment.contains("*") || segment.contains("{") ? 1 : 10;
    return score;
  }
}
