package com.example.authorization.admin.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.example.authorization.admin.api")
@ConditionalOnProperty(
    prefix = "authorization",
    name = {"enabled", "admin.api.enabled"},
    havingValue = "true",
    matchIfMissing = true)
public class AdminApiExceptionHandler {
  public record ValidationError(String field, String message) {}

  public record ErrorResponse(
      String code,
      String message,
      Instant timestamp,
      String correlationId,
      List<ValidationError> errors) {}

  @ExceptionHandler(AdminApiException.class)
  ResponseEntity<ErrorResponse> handle(AdminApiException exception) {
    return response(exception.status(), exception.code(), exception.getMessage(), List.of());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
    List<ValidationError> errors =
        exception.getBindingResult().getFieldErrors().stream()
            .map(this::fieldError)
            .toList();
    return response(
        HttpStatus.BAD_REQUEST,
        "AUTHZ_VALIDATION_FAILED",
        "The request is invalid",
        errors);
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException exception) {
    return response(
        HttpStatus.CONFLICT,
        "AUTHZ_CONCURRENT_MODIFICATION",
        "The resource was changed by another administrator",
        List.of());
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException exception) {
    return response(
        HttpStatus.CONFLICT,
        "AUTHZ_CONFLICT",
        "The requested change conflicts with existing authorization data",
        List.of());
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MissingServletRequestParameterException.class
  })
  ResponseEntity<ErrorResponse> handleMalformedRequest(Exception exception) {
    return response(
        HttpStatus.BAD_REQUEST,
        "AUTHZ_VALIDATION_FAILED",
        "The request is invalid",
        List.of());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
    return response(
        HttpStatus.BAD_REQUEST,
        "AUTHZ_VALIDATION_FAILED",
        exception.getMessage(),
        List.of());
  }

  private ValidationError fieldError(FieldError error) {
    return new ValidationError(error.getField(), error.getDefaultMessage());
  }

  private ResponseEntity<ErrorResponse> response(
      HttpStatus status, String code, String message, List<ValidationError> errors) {
    String correlationId = MDC.get("correlationId");
    if (correlationId == null || correlationId.isBlank()) correlationId = MDC.get("traceId");
    if (correlationId == null || correlationId.isBlank())
      correlationId = UUID.randomUUID().toString();
    return ResponseEntity.status(status)
        .body(new ErrorResponse(code, message, Instant.now(), correlationId, errors));
  }
}
