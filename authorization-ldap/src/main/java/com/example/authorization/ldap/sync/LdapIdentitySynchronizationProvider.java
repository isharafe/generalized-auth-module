package com.example.authorization.ldap.sync;

import com.example.authorization.domain.AssignmentSource;
import com.example.authorization.domain.AuthenticatedIdentity;
import com.example.authorization.domain.AuthorizationChangeAuditEvent;
import com.example.authorization.domain.SynchronizationStatus;
import com.example.authorization.ldap.client.LdapAuthority;
import com.example.authorization.ldap.client.LdapDirectoryClient;
import com.example.authorization.ldap.client.LdapUser;
import com.example.authorization.ldap.config.AuthorizationLdapProperties;
import com.example.authorization.persistence.entity.PermissionGroupEntity;
import com.example.authorization.persistence.entity.RoleEntity;
import com.example.authorization.persistence.entity.SyncStateEntity;
import com.example.authorization.persistence.entity.UserEntity;
import com.example.authorization.persistence.entity.UserPermissionGroupEntity;
import com.example.authorization.persistence.entity.UserRoleEntity;
import com.example.authorization.persistence.repository.PermissionGroupRepository;
import com.example.authorization.persistence.repository.RoleRepository;
import com.example.authorization.persistence.repository.SyncStateRepository;
import com.example.authorization.persistence.repository.UserPermissionGroupRepository;
import com.example.authorization.persistence.repository.UserRepository;
import com.example.authorization.persistence.repository.UserRoleRepository;
import com.example.authorization.persistence.service.PendingUserAssignmentResolver;
import com.example.authorization.spi.AuthorizationAuditPublisher;
import com.example.authorization.spi.AuthorizationCacheInvalidator;
import com.example.authorization.spi.ExternalAuthorityMapper;
import com.example.authorization.spi.IdentitySynchronizationProvider;
import java.time.Instant;
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

public final class LdapIdentitySynchronizationProvider implements IdentitySynchronizationProvider {
  private static final String SYNC_KEY = "GLOBAL_IDENTITY_SYNC";
  private static final String SOURCE_SYSTEM = "LDAP";

  private final AuthorizationLdapProperties properties;
  private final LdapDirectoryClient client;
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
  private final TransactionTemplate transaction;

  public LdapIdentitySynchronizationProvider(
      AuthorizationLdapProperties properties,
      LdapDirectoryClient client,
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
                    "ldap",
                    state.getStatus(),
                    state.getUpdatedAt(),
                    state.getDetails()))
        .orElseGet(() -> new SynchronizationStatus(true, "ldap", "NEVER", null, null));
  }

  @Override
  public void synchronize(AuthenticatedIdentity identity) {
    if (!properties.resolvedIssuer().equals(identity.issuer()))
      throw new LdapSynchronizationException(
          "Target identity issuer does not match authorization.ldap.issuer");
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
    execute("INCREMENTAL_FULL_SCAN", "SYNC:INCREMENTAL", () -> synchronizeUsers(true));
  }

  private void execute(String operation, String target, Supplier<SyncOutcome> work) {
    publish("IDENTITY_SYNC_STARTED", target, "SYNC", operation);
    try {
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
        throw new LdapSynchronizationException("LDAP synchronization returned no result");
      outcome.invalidatedIdentities().forEach(cache::invalidateIdentity);
      publish("IDENTITY_SYNC_COMPLETED", target, "SYNC", outcome.details(operation));
    } catch (RuntimeException exception) {
      recordFailure(operation, exception);
      publish(
          "IDENTITY_SYNC_FAILED",
          target,
          "SYNC",
          operation + " failed: " + safeMessage(exception));
      if (exception instanceof LdapSynchronizationException synchronizationException)
        throw synchronizationException;
      throw new LdapSynchronizationException(
          "LDAP " + operation + " synchronization failed", exception);
    }
  }

  private SyncOutcome synchronizeUsers(boolean removeMissing) {
    List<LdapUser> externalUsers = client.users();
    Set<String> seen = new LinkedHashSet<>();
    Set<AuthenticatedIdentity> invalidated = new LinkedHashSet<>();
    int processed = 0;
    int removed = 0;
    for (LdapUser external : externalUsers) {
      if (external.id() == null || external.id().isBlank())
        throw new LdapSynchronizationException("LDAP returned a user without a stable id");
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
    Optional<LdapUser> external = client.user(identity.subject());
    if (external.isPresent())
      return new SyncOutcome(Set.of(synchronizeUser(external.get())), 1, 0);
    UserEntity local =
        users.findByIssuerAndSubject(identity.issuer(), identity.subject()).orElse(null);
    if (local == null) return new SyncOutcome(Set.of(), 0, 0);
    boolean changed = markMissing(local);
    return new SyncOutcome(changed ? Set.of(identity(local)) : Set.of(), 0, changed ? 1 : 0);
  }

  private AuthenticatedIdentity synchronizeUser(LdapUser external) {
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

  private Map<Target, String> desiredAssignments(LdapUser user) {
    Map<Target, String> desired = new LinkedHashMap<>();
    for (LdapAuthority authority : user.authorities())
      addTargets(desired, authority.type(), authority.value());
    return desired;
  }

  private void addTargets(Map<Target, String> desired, String authorityType, String authority) {
    if (authority == null || authority.isBlank()) return;
    String normalizedType = authorityType.toUpperCase(java.util.Locale.ROOT);
    String reference = normalizedType + ":" + authority;
    for (String mapped : authorityMapper.map(SOURCE_SYSTEM, normalizedType, authority)) {
      int separator = mapped.indexOf(':');
      if (separator <= 0 || separator == mapped.length() - 1)
        throw new LdapSynchronizationException(
            "Invalid external authority mapping target " + mapped);
      Target target = new Target(mapped.substring(0, separator), mapped.substring(separator + 1));
      if (!target.type().equals("ROLE") && !target.type().equals("PERMISSION_GROUP"))
        throw new LdapSynchronizationException(
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
                      new LdapSynchronizationException(
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
                      new LdapSynchronizationException(
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
                new LdapSynchronizationException(
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
            "{\"provider\":\"ldap\",\"details\":\"" + json(details) + "\"}"));
  }

  private String safeMessage(Throwable exception) {
    String message = exception.getMessage();
    return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
  }

  private String truncate(String value) {
    return value.length() <= 1000 ? value : value.substring(0, 1000);
  }

  private String json(String value) {
    return truncate(value).replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
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
