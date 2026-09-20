package io.github.isharafe.authorization.seed;

import java.util.Map;
import java.util.Objects;

public record AuthorizationSeedResource(
    String source, String location, Map<String, String> variables) {
  public AuthorizationSeedResource {
    source = requireText(source, "source");
    location = requireText(location, "location");
    variables = variables == null ? Map.of() : Map.copyOf(variables);
  }

  public AuthorizationSeedResource(String source, String location) {
    this(source, location, Map.of());
  }

  private static String requireText(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    return value;
  }
}
