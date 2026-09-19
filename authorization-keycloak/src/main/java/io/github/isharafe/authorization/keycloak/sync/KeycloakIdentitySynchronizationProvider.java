package io.github.isharafe.authorization.keycloak.sync;

import io.github.isharafe.authorization.domain.AssignmentSource;
import io.github.isharafe.authorization.domain.AuthenticatedIdentity;
import io.github.isharafe.authorization.domain.AuthorizationChangeAuditEvent;
import io.github.isharafe.authorization.domain.SynchronizationStatus;
import io.github.isharafe.authorization.keycloak.client.KeycloakAdminClient;
import io.github.isharafe.authorization.keycloak.client.KeycloakGroup;
import io.github.isharafe.authorization.keycloak.client.KeycloakRole;
import io.github.isharafe.authorization.keycloak.client.KeycloakUser;
import io.github.isharafe.authorization.keycloak.config.AuthorizationKeycloakProperties;
import io.github.isharafe.authorization.persistence.entity.PermissionGroupEntity;
import io.github.isharafe.authorization.persistence.entity.RoleEntity;
import io.github.isharafe.authorization.persistence.entity.SyncStateEntity;
import io.github.isharafe.authorization.persistence.entity.UserEntity;
import io.github.isharafe.authorization.persistence.entity.UserPermissionGroupEntity;
import io.github.isharafe.authorization.persistence.entity.UserRoleEntity;
import io.github.isharafe.authorization.persistence.repository.PermissionGroupRepository;
import io.github.isharafe.authorization.persistence.repository.RoleRepository;
import io.github.isharafe.authorization.persistence.repository.SyncStateRepository;
import io.github.isharafe.authorization.persistence.repository.UserPermissionGroupRepository;
import io.github.isharafe.authorization.persistence.repository.UserRepository;
import io.github.isharafe.authorization.persistence.repository.UserRoleRepository;
import io.github.isharafe.authorization.persistence.service.PendingUserAssignmentResolver;
import io.github.isharafe.authorization.spi.AuthorizationAuditPublisher;
import io.github.isharafe.authorization.spi.AuthorizationCacheInvalidator;
import io.github.isharafe.authorization.spi.AuthorizationObservation;
import io.github.isharafe.authorization.spi.ExternalAuthorityMapper;
import io.github.isharafe.authorization.spi.IdentitySynchronizationProvider;
import java.time.Instant;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class KeycloakIdentitySynchronizationProvider
    implements IdentitySynchronizationProvider {
  private static final String SYNC_KEY = "GLOBAL_IDENTITY_SYNC";
  private static final String SOURCE_SYSTEM = "KEYCLOAK";

  private final AuthorizationKeycloakProperties properties;
  private final KeycloakAdminClient client;
  private final ExternalAuthorityMapper authorityMapper;
  private final UserRepository users;
  private final RoleRepository roles;
  private final PermissionGroupRepository groups;
  private final UserRoleRepository userRoles;
  private final UserPermissionGroupRepository userGroups;
  private final SyncStateRepository syncStates;
  private final ObjectProvider<PendingUserAssignmentResolver> pendingResolver;
  private final AuthorizationCacheInvalidator cache;
  private final AuthorizationAuditPublisher audit;
  private final AuthorizationObservation observation;
  private final TransactionTemplate transaction;

  public KeycloakIdentitySynchronizationProvider(
      AuthorizationKeycloakProperties properties,
      KeycloakAdminClient client,
      ExternalAuthorityMapper authorityMapper,
      UserRepository users,
      RoleRepository roles,
      PermissionGroupRepository groups,
      UserRoleRepository userRoles,
      UserPermissionGroupRepository userGroups,
      SyncStateRepository syncStates,
      ObjectProvider<PendingUserAssignmentResolver> pendingResolver,
      AuthorizationCacheInvalidator cache,
      AuthorizationAuditPublisher audit,
      AuthorizationObservation observation,
      PlatformTransactionManager transactionManager) {
    properties.validate();
    this.properties = properties;
    this.client = client;
    this.authorityMapper = authorityMapper;
    this.users = users;
    this.roles = roles;
    this.groups = groups;
    this.userRoles = userRoles;
    this.userGroups = userGroups;
    this.syncStates = syncStates;
    this.pendingResolver = pendingResolver;
    this.cache = cache;
    this.audit = audit;
    this.observation = observation;
    this.transaction = new TransactionTemplate(transactionManager);
  }

  @Override
  public boolean supported() {
    return true;
  }

  @Override
  public SynchronizationStatus status() {
    return syncStates
        .findById(SYNC_KEY)
        .map(
            state ->
                new SynchronizationStatus(
                    true,
                    "keycloak",
                    state.getStatus(),
                    state.getUpdatedAt(),
                    state.getDetails()))
        .orElseGet(
            () -> new SynchronizationStatus(true, "keycloak", "NEVER", null, null));
  }

  @Override
  public void synchronize(AuthenticatedIdentity identity) {
    if (!properties.resolvedIssuer().equals(identity.issuer()))
      throw new KeycloakSynchronizationException(
          "Target identity issuer does not match authorization.keycloak.issuer");
    execute(
        "TARGETED",
        "IDENTITY:" + identity.issuer() + ":" + identity.subject(),
        () -> synchronizeTarget(identity));
  }

  @Override
  public void synchronizeAll() {
    execute("FULL", "SYNC:FULL", () -> synchronizeUsers(true));
  }

  @Override
  public void synchronizeIncremental() {
    execute(
        "INCREMENTAL_FULL_SCAN",
        "SYNC:INCREMENTAL",
        () -> synchronizeUsers(true));
  }

  private void execute(String operation, String target, Supplier<SyncOutcome> work) {
    long started = System.nanoTime();
    String result = "failure";
    try {
      publish("IDENTITY_SYNC_STARTED", target, "SYNC", operation);
      SyncOutcome outcome =
          transaction.execute(
              status -> {
                SyncStateEntity state = lockState();
                state.setStatus("RUNNING");
                state.setUpdatedAt(Instant.now());
                state.setDetails(operation);
                syncStates.saveAndFlush(state);
                SyncOutcome value = work.get();
                state.setStatus("COMPLETED");
                state.setUpdatedAt(Instant.now());
                state.setDetails(value.details(operation));
                syncStates.save(state);
                return value;
              });
      if (outcome == null)
        throw new KeycloakSynchronizationException("Keycloak synchronization returned no result");
      outcome.invalidatedIdentities().forEach(cache::invalidateIdentity);
      publish(
          "IDENTITY_SYNC_COMPLETED",
          target,
          "SYNC",
          outcome.details(operation));
      result = "success";
    } catch (RuntimeException exception) {
      recordFailure(operation, exception);
      publish(
          "IDENTITY_SYNC_FAILED",
          target,
          "SYNC",
          operation + " failed: " + safeMessage(exception));
      if (exception instanceof KeycloakSynchronizationException synchronizationException)
        throw synchronizationException;
      throw new KeycloakSynchronizationException(
          "Keycloak " + operation + " synchronization failed", exception);
    } finally {
      observation.recordSynchronization(
          "keycloak", operation.toLowerCase(java.util.Locale.ROOT), result,
          Duration.ofNanos(System.nanoTime() - started));
    }
  }

  private SyncOutcome synchronizeUsers(boolean removeMissing) {
    List<KeycloakUser> externalUsers = client.users();
    Set<String> seen = new LinkedHashSet<>();
    Set<AuthenticatedIdentity> invalidated = new LinkedHashSet<>();
    int processed = 0;
    int removed = 0;
    for (KeycloakUser external : externalUsers) {
      if (external.id() == null || external.id().isBlank())
        throw new KeycloakSynchronizationException("Keycloak returned a user without an id");
      if (!seen.add(external.id())) continue;
      invalidated.add(synchronizeUser(external));
      processed++;
    }
    if (removeMissing) {
      for (UserEntity local : users.findAll()) {
        if (!properties.resolvedIssuer().equals(local.getIssuer()) || seen.contains(local.getSubject()))
          continue;
        if (markMissing(local)) {
          invalidated.add(identity(local));
          removed++;
        }
      }
    }
    return new SyncOutcome(invalidated, processed, removed);
  }

  private SyncOutcome synchronizeTarget(AuthenticatedIdentity identity) {
    Optional<KeycloakUser> external = client.user(identity.subject());
    if (external.isPresent())
      return new SyncOutcome(Set.of(synchronizeUser(external.get())), 1, 0);
    UserEntity local =
        users.findByIssuerAndSubject(identity.issuer(), identity.subject()).orElse(null);
    if (local == null) return new SyncOutcome(Set.of(), 0, 0);
    boolean changed = markMissing(local);
    return new SyncOutcome(changed ? Set.of(identity(local)) : Set.of(), 0, changed ? 1 : 0);
  }

  private AuthenticatedIdentity synchronizeUser(KeycloakUser external) {
    Map<Target, String> desired = desiredAssignments(external);
    String issuer = properties.resolvedIssuer();
    UserEntity user =
        users.findByIssuerAndSubject(issuer, external.id()).orElseGet(UserEntity::new);
    boolean enabledChanged = user.getId() != null && user.isEnabled() != external.enabled();
    user.setIssuer(issuer);
    user.setSubject(external.id());
    user.setUsername(external.username());
    user.setEmail(external.email());
    user.setFirstName(external.firstName());
    user.setLastName(external.lastName());
    user.setEnabled(external.enabled());
    user.setExternalDirectoryId(external.id());
    user.setLastIdentitySyncAt(Instant.now());
    user.setIdentitySyncStatus("SYNCED");
    user = users.saveAndFlush(user);

    boolean changed = enabledChanged | reconcileRoles(user, desired) | reconcileGroups(user, desired);
    if (changed) {
      user.incrementEntitlementVersion();
      users.save(user);
    }
    AuthenticatedIdentity identity = identity(user);
    pendingResolver.ifAvailable(resolver -> resolver.resolve(identity));
    return identity;
  }

  private Map<Target, String> desiredAssignments(KeycloakUser user) {
    Map<Target, String> desired = new LinkedHashMap<>();
    for (KeycloakGroup group : client.groups(user.id())) {
      String authority = group.path() == null || group.path().isBlank() ? group.name() : group.path();
      addTargets(desired, "GROUP", authority);
    }
    for (KeycloakRole role : client.realmRoles(user.id())) addTargets(desired, "ROLE", role.name());
    return desired;
  }

  private void addTargets(Map<Target, String> desired, String authorityType, String authority) {
    if (authority == null || authority.isBlank()) return;
    String reference = authorityType + ":" + authority;
    for (String mapped : authorityMapper.map(SOURCE_SYSTEM, authorityType, authority)) {
      int separator = mapped.indexOf(':');
      if (separator <= 0 || separator == mapped.length() - 1)
        throw new KeycloakSynchronizationException(
            "Invalid external authority mapping target " + mapped);
      Target target = new Target(mapped.substring(0, separator), mapped.substring(separator + 1));
      if (!target.type().equals("ROLE") && !target.type().equals("PERMISSION_GROUP"))
        throw new KeycloakSynchronizationException(
            "Unsupported external authority mapping target " + mapped);
      desired.putIfAbsent(target, reference);
    }
  }

  private boolean reconcileRoles(UserEntity user, Map<Target, String> desired) {
    Map<String, UserRoleEntity> current = new LinkedHashMap<>();
    userRoles.findByUserId(user.getId()).forEach(value -> current.put(value.getRole().getCode(), value));
    Set<String> desiredCodes =
        desired.keySet().stream()
            .filter(target -> target.type().equals("ROLE"))
            .map(Target::code)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    boolean changed = false;
    for (UserRoleEntity assignment : List.copyOf(current.values())) {
      if (assignment.getSource() == AssignmentSource.IDENTITY_SYNC
          && !desiredCodes.contains(assignment.getRole().getCode())) {
        userRoles.delete(assignment);
        current.remove(assignment.getRole().getCode());
        changed = true;
      }
    }
    for (String code : desiredCodes) {
      if (current.containsKey(code)) continue;
      RoleEntity role =
          roles
              .findByCode(code)
              .orElseThrow(
                  () ->
                      new KeycloakSynchronizationException(
                          "External mapping references missing role " + code));
      userRoles.save(
          new UserRoleEntity(
              user,
              role,
              AssignmentSource.IDENTITY_SYNC,
              desired.get(new Target("ROLE", code))));
      changed = true;
    }
    return changed;
  }

  private boolean reconcileGroups(UserEntity user, Map<Target, String> desired) {
    Map<String, UserPermissionGroupEntity> current = new LinkedHashMap<>();
    userGroups
        .findByUserId(user.getId())
        .forEach(value -> current.put(value.getPermissionGroup().getCode(), value));
    Set<String> desiredCodes =
        desired.keySet().stream()
            .filter(target -> target.type().equals("PERMISSION_GROUP"))
            .map(Target::code)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    boolean changed = false;
    for (UserPermissionGroupEntity assignment : List.copyOf(current.values())) {
      if (assignment.getSource() == AssignmentSource.IDENTITY_SYNC
          && !desiredCodes.contains(assignment.getPermissionGroup().getCode())) {
        userGroups.delete(assignment);
        current.remove(assignment.getPermissionGroup().getCode());
        changed = true;
      }
    }
    for (String code : desiredCodes) {
      if (current.containsKey(code)) continue;
      PermissionGroupEntity group =
          groups
              .findByCode(code)
              .orElseThrow(
                  () ->
                      new KeycloakSynchronizationException(
                          "External mapping references missing permission group " + code));
      userGroups.save(
          new UserPermissionGroupEntity(
              user,
              group,
              AssignmentSource.IDENTITY_SYNC,
              desired.get(new Target("PERMISSION_GROUP", code))));
      changed = true;
    }
    return changed;
  }

  private boolean markMissing(UserEntity user) {
    boolean changed = false;
    for (UserRoleEntity assignment : userRoles.findByUserId(user.getId())) {
      if (assignment.getSource() == AssignmentSource.IDENTITY_SYNC) {
        userRoles.delete(assignment);
        changed = true;
      }
    }
    for (UserPermissionGroupEntity assignment : userGroups.findByUserId(user.getId())) {
      if (assignment.getSource() == AssignmentSource.IDENTITY_SYNC) {
        userGroups.delete(assignment);
        changed = true;
      }
    }
    boolean wasEnabled = user.isEnabled();
    user.setEnabled(false);
    user.setLastIdentitySyncAt(Instant.now());
    user.setIdentitySyncStatus("MISSING");
    if (changed || wasEnabled) user.incrementEntitlementVersion();
    users.save(user);
    return changed || wasEnabled;
  }

  private SyncStateEntity lockState() {
    return syncStates
        .findByKeyForUpdate(SYNC_KEY)
        .orElseThrow(
            () ->
                new KeycloakSynchronizationException(
                    "Authorization sync state is not initialized; run authorization Flyway migrations"));
  }

  private void recordFailure(String operation, RuntimeException exception) {
    try {
      transaction.executeWithoutResult(
          status -> {
            SyncStateEntity state = lockState();
            state.setStatus("FAILED");
            state.setUpdatedAt(Instant.now());
            state.setDetails(truncate(operation + " failed: " + safeMessage(exception)));
            syncStates.save(state);
          });
    } catch (RuntimeException ignored) {
      // Preserve the original synchronization failure.
    }
  }

  private void publish(String eventType, String target, String action, String details) {
    audit.publishChange(
        new AuthorizationChangeAuditEvent(
            eventType,
            null,
            null,
            target,
            action,
            "{\"provider\":\"keycloak\",\"details\":\""
                + json(details)
                + "\"}"));
  }

  private String safeMessage(Throwable exception) {
    String message = exception.getMessage();
    return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
  }

  private String truncate(String value) {
    return value.length() <= 1000 ? value : value.substring(0, 1000);
  }

  private String json(String value) {
    return truncate(value)
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", " ");
  }

  private AuthenticatedIdentity identity(UserEntity user) {
    return new AuthenticatedIdentity(user.getIssuer(), user.getSubject(), user.getUsername());
  }

  private record Target(String type, String code) {}

  private record SyncOutcome(
      Set<AuthenticatedIdentity> invalidatedIdentities, int processed, int removed) {
    SyncOutcome {
      invalidatedIdentities = Set.copyOf(invalidatedIdentities);
    }

    String details(String operation) {
      return operation + " processed=" + processed + " removed=" + removed;
    }
  }
}
