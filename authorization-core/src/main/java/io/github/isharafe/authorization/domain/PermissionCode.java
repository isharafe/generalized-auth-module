package io.github.isharafe.authorization.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/** Canonical type-qualified identifier for an application permission. */
public final class PermissionCode {
  public static final int MAX_LENGTH = 100;

  private static final Pattern LOCAL_CODE =
      Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]*");

  private PermissionCode() {}

  /** Creates a canonical {@code TYPE:LOCAL_CODE} identifier. */
  public static String of(ResourceType resourceType, String localCode) {
    ResourceType type = Objects.requireNonNull(resourceType, "resourceType");
    validateLocalCode(localCode);
    return validate(type.name() + ":" + localCode, type);
  }

  /** Validates and returns a canonical identifier for the supplied resource type. */
  public static String validate(String code, ResourceType resourceType) {
    Objects.requireNonNull(code, "code");
    ResourceType type = Objects.requireNonNull(resourceType, "resourceType");
    if (code.length() > MAX_LENGTH)
      throw new IllegalArgumentException(
          "permission code must contain at most " + MAX_LENGTH + " characters");

    String prefix = type.name() + ":";
    if (!code.startsWith(prefix))
      throw new IllegalArgumentException(
          "permission code must start with " + prefix + " for resource type " + type);

    validateLocalCode(code.substring(prefix.length()));
    return code;
  }

  private static void validateLocalCode(String localCode) {
    if (localCode == null || !LOCAL_CODE.matcher(localCode).matches())
      throw new IllegalArgumentException(
          "permission local code must start with an alphanumeric character and contain only alphanumeric characters, '_', '.', or '-'");
  }
}
