package io.github.isharafe.authorization.util;

import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

public final class HttpResourceUtil {
  private static final char SEPARATOR = ':';
  private static final PathPatternParser PARSER = PathPatternParser.defaultInstance;

  private HttpResourceUtil() {}

  public static String method(String value) {
    return value.substring(0, value.indexOf(SEPARATOR));
  }

  public static String path(String value) {
    return value.substring(value.indexOf(SEPARATOR) + 1);
  }

  public static boolean methodMatches(String expectedPattern, String actualResource) {
    String expectedMethod = method(expectedPattern);
    return expectedMethod.equals("*")
        || expectedMethod.equalsIgnoreCase(method(actualResource));
  }

  public static boolean pathMatches(String expectedPattern, String actualResource) {
    PathPattern pathPattern = PARSER.parse(path(expectedPattern));
    return pathPattern.matches(PathContainer.parsePath(path(actualResource)));
  }
}
