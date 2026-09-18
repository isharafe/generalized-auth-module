package io.github.isharafe.authorization.admin.api;

import org.springframework.http.HttpStatus;

public class AdminApiException extends RuntimeException {
  private final HttpStatus status;
  private final String code;

  public AdminApiException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }

  public static AdminApiException notFound(String kind, Object identifier) {
    return new AdminApiException(
        HttpStatus.NOT_FOUND,
        "AUTHZ_" + kind.toUpperCase().replace(' ', '_') + "_NOT_FOUND",
        kind + " " + identifier + " does not exist");
  }

  public static AdminApiException conflict(String code, String message) {
    return new AdminApiException(HttpStatus.CONFLICT, code, message);
  }

  public static AdminApiException validation(String message) {
    return new AdminApiException(HttpStatus.BAD_REQUEST, "AUTHZ_VALIDATION_FAILED", message);
  }

  public static AdminApiException unsupported(String message) {
    return new AdminApiException(HttpStatus.NOT_IMPLEMENTED, "AUTHZ_SYNC_UNSUPPORTED", message);
  }
}
