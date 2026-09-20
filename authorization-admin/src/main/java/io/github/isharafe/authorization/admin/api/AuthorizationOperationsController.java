package io.github.isharafe.authorization.admin.api;

import static io.github.isharafe.authorization.admin.api.AdminPageableFactory.create;

import io.github.isharafe.authorization.admin.dto.AdminDtos;
import io.github.isharafe.authorization.admin.service.AuthorizationOperationsAdminService;
import io.github.isharafe.authorization.domain.AuditEventKind;
import io.github.isharafe.authorization.domain.AuthenticatedIdentity;
import jakarta.validation.Valid;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@ConditionalOnProperty(
    prefix = "authorization",
    name = {"enabled", "admin.api.enabled"},
    havingValue = "true",
    matchIfMissing = true)
@RequiredArgsConstructor
@RequestMapping("${authorization.admin.api.base-path:/authorization-admin/api}")
public class AuthorizationOperationsController {
  private static final Set<String> MAPPING_SORTS =
      Set.of(
          "id",
          "sourceSystem",
          "authorityType",
          "authorityValue",
          "targetType",
          "targetCode",
          "enabled",
          "version");
  private static final Set<String> AUDIT_SORTS =
      Set.of(
          "id",
          "timestamp",
          "eventKind",
          "eventType",
          "actorIssuer",
          "actorSubject",
          "target");
  private final AuthorizationOperationsAdminService service;

  @GetMapping("/external-mappings")
  public AdminDtos.Page<AdminDtos.ExternalMapping> externalMappings(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "id,asc") String sort,
      @RequestParam(defaultValue = "") String search) {
    return service.externalMappings(search, create(page, size, sort, "id", MAPPING_SORTS));
  }

  @PostMapping("/external-mappings")
  @ResponseStatus(HttpStatus.CREATED)
  public AdminDtos.ExternalMapping createExternalMapping(
      @Valid @RequestBody AdminDtos.ExternalMapping request) {
    return service.createExternalMapping(request);
  }

  @GetMapping("/external-mappings/{id}")
  public AdminDtos.ExternalMapping externalMapping(@PathVariable Long id) {
    return service.externalMapping(id);
  }

  @PutMapping("/external-mappings/{id}")
  public AdminDtos.ExternalMapping updateExternalMapping(
      @PathVariable Long id, @Valid @RequestBody AdminDtos.ExternalMapping request) {
    return service.updateExternalMapping(id, request);
  }

  @DeleteMapping("/external-mappings/{id}")
  public ResponseEntity<Void> deleteExternalMapping(
      @PathVariable Long id, @RequestParam Long version) {
    service.deleteExternalMapping(id, version);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/audit")
  public AdminDtos.Page<AdminDtos.AuditEvent> audit(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "timestamp,desc") String sort,
      @RequestParam(required = false) AuditEventKind eventKind,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) String actor,
      @RequestParam(required = false) String target) {
    return service.audit(
        eventKind,
        eventType,
        actor,
        target,
        create(page, size, sort, "timestamp", AUDIT_SORTS));
  }

  @GetMapping("/sync/status")
  public AdminDtos.SyncStatus syncStatus() {
    return service.syncStatus();
  }

  @PostMapping("/sync/full")
  public AdminDtos.SyncStatus synchronizeAll() {
    return service.synchronizeAll();
  }

  @PostMapping("/sync/incremental")
  public AdminDtos.SyncStatus synchronizeIncremental() {
    return service.synchronizeIncremental();
  }

  @PostMapping("/sync/users/{subject}")
  public AdminDtos.SyncStatus synchronizeIdentity(
      @PathVariable String subject,
      @RequestParam(defaultValue = "external") String issuer) {
    return service.synchronizeIdentity(new AuthenticatedIdentity(issuer, subject, subject));
  }
}
