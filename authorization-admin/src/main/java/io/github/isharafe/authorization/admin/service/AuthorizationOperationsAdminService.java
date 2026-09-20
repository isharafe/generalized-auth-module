package io.github.isharafe.authorization.admin.service;

import io.github.isharafe.authorization.admin.api.AdminApiException;
import io.github.isharafe.authorization.admin.dto.AdminDtos;
import io.github.isharafe.authorization.domain.AssignmentTargetType;
import io.github.isharafe.authorization.domain.AuditEventKind;
import io.github.isharafe.authorization.domain.AuthenticatedIdentity;
import io.github.isharafe.authorization.domain.SynchronizationStatus;
import io.github.isharafe.authorization.persistence.entity.AuditEventEntity;
import io.github.isharafe.authorization.persistence.entity.ExternalAuthorityMappingEntity;
import io.github.isharafe.authorization.persistence.entity.PermissionGroupEntity;
import io.github.isharafe.authorization.persistence.entity.RoleEntity;
import io.github.isharafe.authorization.persistence.repository.AuditEventRepository;
import io.github.isharafe.authorization.persistence.repository.ExternalAuthorityMappingRepository;
import io.github.isharafe.authorization.persistence.repository.PermissionGroupRepository;
import io.github.isharafe.authorization.persistence.repository.RoleRepository;
import io.github.isharafe.authorization.spi.IdentitySynchronizationProvider;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class AuthorizationOperationsAdminService {
  private final RoleRepository roles;
  private final PermissionGroupRepository groups;
  private final ExternalAuthorityMappingRepository externalMappings;
  private final AuditEventRepository auditEvents;
  private final IdentitySynchronizationProvider synchronization;
  private final AdminChangePublisher changes;

  @Transactional(readOnly = true)
  public AdminDtos.Page<AdminDtos.ExternalMapping> externalMappings(
      String search, Pageable pageable) {
    return page(externalMappings.search(text(search), pageable), this::externalMapping);
  }

  @Transactional(readOnly = true)
  public AdminDtos.ExternalMapping externalMapping(Long id) {
    return externalMapping(requireExternalMapping(id));
  }

  @Transactional
  public AdminDtos.ExternalMapping createExternalMapping(AdminDtos.ExternalMapping request) {
    validateExternalMapping(request);
    ExternalAuthorityMappingEntity entity = new ExternalAuthorityMappingEntity();
    updateExternalMapping(entity, request, false);
    externalMappings.saveAndFlush(entity);
    changedAll(
        "ADMIN_CREATE",
        "EXTERNAL_MAPPING:" + entity.getId(),
        "CREATE");
    return externalMapping(entity);
  }

  @Transactional
  public AdminDtos.ExternalMapping updateExternalMapping(
      Long id, AdminDtos.ExternalMapping request) {
    ExternalAuthorityMappingEntity entity = requireExternalMapping(id);
    requireVersion(request.version(), entity.getVersion());
    validateExternalMapping(request);
    updateExternalMapping(entity, request, true);
    externalMappings.saveAndFlush(entity);
    changedAll("ADMIN_UPDATE", "EXTERNAL_MAPPING:" + id, "UPDATE");
    return externalMapping(entity);
  }

  @Transactional
  public void deleteExternalMapping(Long id, Long version) {
    ExternalAuthorityMappingEntity entity = requireExternalMapping(id);
    requireVersion(version, entity.getVersion());
    externalMappings.delete(entity);
    externalMappings.flush();
    changedAll("ADMIN_DISABLE", "EXTERNAL_MAPPING:" + id, "DELETE");
  }

  @Transactional(readOnly = true)
  public AdminDtos.Page<AdminDtos.AuditEvent> audit(
      AuditEventKind eventKind,
      String eventType,
      String actor,
      String target,
      Pageable pageable) {
    return page(
        auditEvents.search(
            eventKind, nullable(eventType), nullable(actor), nullable(target), pageable),
        this::auditEvent);
  }

  public AdminDtos.SyncStatus syncStatus() {
    SynchronizationStatus status = synchronization.status();
    return new AdminDtos.SyncStatus(
        status.supported(),
        status.provider(),
        status.status(),
        status.updatedAt(),
        status.details());
  }

  public AdminDtos.SyncStatus synchronizeAll() {
    requireSync();
    synchronization.synchronizeAll();
    return syncStatus();
  }

  public AdminDtos.SyncStatus synchronizeIncremental() {
    requireSync();
    synchronization.synchronizeIncremental();
    return syncStatus();
  }

  public AdminDtos.SyncStatus synchronizeIdentity(AuthenticatedIdentity identity) {
    requireSync();
    synchronization.synchronize(identity);
    return syncStatus();
  }

  private void updateExternalMapping(
      ExternalAuthorityMappingEntity entity,
      AdminDtos.ExternalMapping request,
      boolean update) {
    entity.setSourceSystem(request.sourceSystem());
    entity.setAuthorityType(request.authorityType());
    entity.setAuthorityValue(request.authorityValue());
    entity.setTargetType(request.targetType());
    entity.setTargetCode(request.targetCode());
    entity.setEnabled(request.enabled() == null ? !update || entity.isEnabled() : request.enabled());
  }

  private void validateExternalMapping(AdminDtos.ExternalMapping request) {
    if (request.targetType() == AssignmentTargetType.ROLE) requireRole(request.targetCode());
    else requireGroup(request.targetCode());
  }

  private RoleEntity requireRole(String code) {
    return roles.findByCode(code).orElseThrow(() -> AdminApiException.notFound("role", code));
  }

  private PermissionGroupEntity requireGroup(String code) {
    return groups
        .findByCode(code)
        .orElseThrow(() -> AdminApiException.notFound("permission group", code));
  }

  private ExternalAuthorityMappingEntity requireExternalMapping(Long id) {
    return externalMappings
        .findById(id)
        .orElseThrow(() -> AdminApiException.notFound("external mapping", id));
  }

  private void requireVersion(Long supplied, long current) {
    if (supplied == null)
      throw AdminApiException.validation("version is required for updates and deletes");
    if (supplied != current)
      throw AdminApiException.conflict(
          "AUTHZ_CONCURRENT_MODIFICATION",
          "The resource was changed by another administrator");
  }

  private void requireSync() {
    if (!synchronization.supported())
      throw AdminApiException.unsupported("Identity synchronization is not configured");
  }

  private AdminDtos.ExternalMapping externalMapping(ExternalAuthorityMappingEntity entity) {
    return new AdminDtos.ExternalMapping(
        entity.getId(),
        entity.getSourceSystem(),
        entity.getAuthorityType(),
        entity.getAuthorityValue(),
        entity.getTargetType(),
        entity.getTargetCode(),
        entity.isEnabled(),
        entity.getVersion());
  }

  private AdminDtos.AuditEvent auditEvent(AuditEventEntity entity) {
    return new AdminDtos.AuditEvent(
        entity.getId(),
        entity.getTimestamp(),
        entity.getEventKind(),
        entity.getEventType(),
        entity.getActorIssuer(),
        entity.getActorSubject(),
        entity.getTarget(),
        entity.getAction(),
        entity.getCorrelationId(),
        entity.getDetailsJson(),
        entity.getRequestMethod(),
        entity.getRequestPath(),
        entity.getDecision(),
        entity.getReason(),
        entity.getRuleCode(),
        entity.getPermissionCode());
  }

  private <E, D> AdminDtos.Page<D> page(
      org.springframework.data.domain.Page<E> source, Function<E, D> mapper) {
    return new AdminDtos.Page<>(
        source.getContent().stream().map(mapper).toList(),
        source.getNumber(),
        source.getSize(),
        source.getTotalElements(),
        source.getTotalPages());
  }

  private String text(String value) {
    return value == null ? "" : value.trim();
  }

  private String nullable(String value) {
    String normalized = text(value);
    return normalized.isEmpty() ? null : normalized;
  }

  private void changedAll(String type, String target, String action) {
    changes.all(type, target, action);
  }

}
