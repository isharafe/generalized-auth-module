package com.example.authorization.keycloak.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.authorization.domain.AssignmentSource;
import com.example.authorization.domain.AuthenticatedIdentity;
import com.example.authorization.domain.IdentityChangeProcessingResult;
import com.example.authorization.keycloak.client.KeycloakAdminClient;
import com.example.authorization.keycloak.client.KeycloakGroup;
import com.example.authorization.keycloak.client.KeycloakRole;
import com.example.authorization.keycloak.client.KeycloakUser;
import com.example.authorization.keycloak.event.KeycloakIdentityChangeController;
import com.example.authorization.persistence.entity.*;
import com.example.authorization.persistence.repository.AuditEventRepository;
import com.example.authorization.persistence.repository.ExternalAuthorityMappingRepository;
import com.example.authorization.persistence.repository.IdentityChangeEventRepository;
import com.example.authorization.persistence.repository.PendingUserAssignmentRepository;
import com.example.authorization.persistence.repository.PermissionGroupRepository;
import com.example.authorization.persistence.repository.RoleRepository;
import com.example.authorization.persistence.repository.SyncStateRepository;
import com.example.authorization.persistence.repository.UserPermissionGroupRepository;
import com.example.authorization.persistence.repository.UserRepository;
import com.example.authorization.persistence.repository.UserRoleRepository;
import com.example.authorization.spi.AuthorizationCacheInvalidator;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootTest(
    classes = {
      KeycloakIdentitySynchronizationProviderIntegrationTest.TestApplication.class,
      KeycloakIdentitySynchronizationProviderIntegrationTest.TestBeans.class
    },
    properties = {
      "spring.datasource.url=jdbc:h2:mem:keycloak-sync;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.jpa.open-in-view=false",
      "authorization.source=keycloak",
      "authorization.seed.enabled=false",
      "authorization.keycloak.base-url=http://unused",
      "authorization.keycloak.realm=company",
      "authorization.keycloak.client-id=sync-client",
      "authorization.keycloak.client-secret=secret",
      "authorization.keycloak.issuer=https://id.example/realms/company",
      "authorization.keycloak.sync.enabled=false",
      "authorization.keycloak.events.enabled=true",
      "authorization.keycloak.events.secret=0123456789abcdef0123456789abcdef"
    })
class KeycloakIdentitySynchronizationProviderIntegrationTest {
  private static final String ISSUER = "https://id.example/realms/company";

  @jakarta.annotation.Resource private KeycloakIdentitySynchronizationProvider synchronization;
  @jakarta.annotation.Resource private FakeKeycloakAdminClient client;
  @jakarta.annotation.Resource private TrackingInvalidator invalidator;
  @jakarta.annotation.Resource private UserRepository users;
  @jakarta.annotation.Resource private RoleRepository roles;
  @jakarta.annotation.Resource private PermissionGroupRepository groups;
  @jakarta.annotation.Resource private UserRoleRepository userRoles;
  @jakarta.annotation.Resource private UserPermissionGroupRepository userGroups;
  @jakarta.annotation.Resource private ExternalAuthorityMappingRepository mappings;
  @jakarta.annotation.Resource private PendingUserAssignmentRepository pending;
  @jakarta.annotation.Resource private AuditEventRepository audit;
  @jakarta.annotation.Resource private SyncStateRepository syncStates;
  @jakarta.annotation.Resource private IdentityChangeEventRepository identityEvents;
  @jakarta.annotation.Resource private KeycloakIdentityChangeController eventCallback;

  @BeforeEach
  void reset() {
    userGroups.deleteAll();
    userRoles.deleteAll();
    pending.deleteAll();
    mappings.deleteAll();
    users.deleteAll();
    roles.deleteAll();
    groups.deleteAll();
    audit.deleteAll();
    identityEvents.deleteAll();
    client.reset();
    invalidator.clear();
    SyncStateEntity state = syncStates.findById("GLOBAL_IDENTITY_SYNC").orElseThrow();
    state.setStatus("NEVER");
    state.setDetails(null);
    syncStates.saveAndFlush(state);
  }

  @Test
  void fullSyncMapsGroupsAndRolesRemovesOnlyStaleSyncAssignmentsAndResolvesPending() {
    RoleEntity manualRole = role("MANUAL_ROLE");
    RoleEntity staleRole = role("STALE_ROLE");
    RoleEntity synchronizedRole = role("SYNC_ROLE");
    RoleEntity pendingRole = role("PENDING_ROLE");
    PermissionGroupEntity synchronizedGroup = group("SYNC_GROUP");
    mapping("GROUP", "/Finance", "ROLE", synchronizedRole.getCode());
    mapping("ROLE", "approver", "PERMISSION_GROUP", synchronizedGroup.getCode());

    UserEntity alice = user("u1", "old-alice");
    userRoles.save(new UserRoleEntity(alice, manualRole, AssignmentSource.MANUAL, "admin-api"));
    userRoles.save(
        new UserRoleEntity(alice, staleRole, AssignmentSource.IDENTITY_SYNC, "GROUP:/Old"));

    UserEntity missing = user("missing", "missing-user");
    userRoles.save(new UserRoleEntity(missing, manualRole, AssignmentSource.SEED, "seed"));
    userRoles.save(
        new UserRoleEntity(missing, staleRole, AssignmentSource.IDENTITY_SYNC, "GROUP:/Old"));

    PendingUserAssignmentEntity pendingAssignment = new PendingUserAssignmentEntity();
    pendingAssignment.setIssuer(ISSUER);
    pendingAssignment.setSubject("u2");
    pendingAssignment.setTargetType("ROLE");
    pendingAssignment.setTargetCode(pendingRole.getCode());
    pendingAssignment.setSource(AssignmentSource.SEED);
    pending.save(pendingAssignment);

    client.users =
        List.of(
            new KeycloakUser(
                "u1", "alice", "alice@example.com", "Alice", "Admin", true, Map.of()),
            new KeycloakUser(
                "u2", "bob", "bob@example.com", "Bob", "Builder", true, Map.of()));
    client.groups.put("u1", List.of(new KeycloakGroup("g1", "Finance", "/Finance")));
    client.roles.put("u1", List.of(new KeycloakRole("r1", "approver")));

    synchronization.synchronizeAll();

    UserEntity refreshedAlice = users.findByIssuerAndSubject(ISSUER, "u1").orElseThrow();
    UserEntity bob = users.findByIssuerAndSubject(ISSUER, "u2").orElseThrow();
    UserEntity refreshedMissing = users.findByIssuerAndSubject(ISSUER, "missing").orElseThrow();

    assertThat(refreshedAlice.getUsername()).isEqualTo("alice");
    assertThat(userRoles.findById(new UserRoleId(refreshedAlice.getId(), manualRole.getId())))
        .get()
        .extracting(UserRoleEntity::getSource)
        .isEqualTo(AssignmentSource.MANUAL);
    assertThat(userRoles.existsById(new UserRoleId(refreshedAlice.getId(), staleRole.getId())))
        .isFalse();
    assertThat(userRoles.findById(new UserRoleId(refreshedAlice.getId(), synchronizedRole.getId())))
        .get()
        .extracting(UserRoleEntity::getSource)
        .isEqualTo(AssignmentSource.IDENTITY_SYNC);
    assertThat(
            userGroups.findById(
                new UserPermissionGroupId(refreshedAlice.getId(), synchronizedGroup.getId())))
        .get()
        .extracting(UserPermissionGroupEntity::getSource)
        .isEqualTo(AssignmentSource.IDENTITY_SYNC);

    assertThat(userRoles.findById(new UserRoleId(bob.getId(), pendingRole.getId())))
        .get()
        .extracting(UserRoleEntity::getSource)
        .isEqualTo(AssignmentSource.SEED);
    assertThat(pending.findAll()).isEmpty();

    assertThat(refreshedMissing.isEnabled()).isFalse();
    assertThat(userRoles.existsById(new UserRoleId(refreshedMissing.getId(), staleRole.getId())))
        .isFalse();
    assertThat(userRoles.findById(new UserRoleId(refreshedMissing.getId(), manualRole.getId())))
        .get()
        .extracting(UserRoleEntity::getSource)
        .isEqualTo(AssignmentSource.SEED);

    assertThat(invalidator.identities)
        .extracting(AuthenticatedIdentity::subject)
        .contains("u1", "u2", "missing");
    assertThat(synchronization.status().status()).isEqualTo("COMPLETED");
    assertThat(audit.findAll())
        .extracting(AuditEventEntity::getEventType)
        .contains("IDENTITY_SYNC_STARTED", "IDENTITY_SYNC_COMPLETED");
  }

  @Test
  void targetedSyncUpdatesOneIdentityAndInvalidatesItsCache() {
    user("u1", "old-name");
    client.users =
        List.of(new KeycloakUser("u1", "new-name", null, null, null, true, Map.of()));

    synchronization.synchronize(new AuthenticatedIdentity(ISSUER, "u1", "old-name"));

    assertThat(users.findByIssuerAndSubject(ISSUER, "u1").orElseThrow().getUsername())
        .isEqualTo("new-name");
    assertThat(invalidator.identities)
        .extracting(AuthenticatedIdentity::subject)
        .containsExactly("u1");
  }

  @Test
  void signedEventCallbackPerformsTargetedSyncOnlyOnceAcrossReplay() throws Exception {
    client.users =
        List.of(new KeycloakUser("u-event", "event-user", null, null, null, true, Map.of()));
    byte[] body =
        "{\"eventId\":\"evt-integration\",\"type\":\"USER_UPDATED\",\"userId\":\"u-event\"}"
            .getBytes(StandardCharsets.UTF_8);
    String timestamp = Long.toString(Instant.now().getEpochSecond());
    String signature = eventSignature(timestamp, body);

    var first = eventCallback.receive(timestamp, signature, body);
    var replay = eventCallback.receive(timestamp, signature, body);

    assertThat(first.getBody().result()).isEqualTo(IdentityChangeProcessingResult.PROCESSED);
    assertThat(replay.getBody().result()).isEqualTo(IdentityChangeProcessingResult.DUPLICATE);
    assertThat(users.findByIssuerAndSubject(ISSUER, "u-event").orElseThrow().getUsername())
        .isEqualTo("event-user");
    assertThat(invalidator.identities)
        .extracting(AuthenticatedIdentity::subject)
        .containsExactly("u-event");
  }

  @Test
  void failedSyncRollsBackAndRecordsTypedStatusAndAudit() {
    client.failure = new IllegalStateException("remote unavailable");

    assertThatThrownBy(synchronization::synchronizeAll)
        .isInstanceOf(KeycloakSynchronizationException.class)
        .hasMessageContaining("FULL");

    assertThat(synchronization.status().status()).isEqualTo("FAILED");
    assertThat(synchronization.status().details()).contains("remote unavailable");
    assertThat(audit.findAll())
        .extracting(AuditEventEntity::getEventType)
        .contains("IDENTITY_SYNC_STARTED", "IDENTITY_SYNC_FAILED");
  }

  private RoleEntity role(String code) {
    RoleEntity value = new RoleEntity();
    value.setCode(code);
    value.setName(code);
    return roles.saveAndFlush(value);
  }

  private PermissionGroupEntity group(String code) {
    PermissionGroupEntity value = new PermissionGroupEntity();
    value.setCode(code);
    value.setName(code);
    return groups.saveAndFlush(value);
  }

  private UserEntity user(String subject, String username) {
    UserEntity value = new UserEntity();
    value.setIssuer(ISSUER);
    value.setSubject(subject);
    value.setUsername(username);
    value.setEnabled(true);
    return users.saveAndFlush(value);
  }

  private void mapping(String authorityType, String authority, String targetType, String targetCode) {
    ExternalAuthorityMappingEntity value = new ExternalAuthorityMappingEntity();
    value.setSourceSystem("KEYCLOAK");
    value.setAuthorityType(authorityType);
    value.setAuthorityValue(authority);
    value.setTargetType(targetType);
    value.setTargetCode(targetCode);
    value.setEnabled(true);
    mappings.saveAndFlush(value);
  }

  private String eventSignature(String timestamp, byte[] body) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(
        new SecretKeySpec(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8),
            "HmacSHA256"));
    mac.update(timestamp.getBytes(StandardCharsets.US_ASCII));
    mac.update((byte) '.');
    return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class TestApplication {}

  @TestConfiguration(proxyBeanMethods = false)
  static class TestBeans {
    @Bean
    FakeKeycloakAdminClient keycloakAdminClient() {
      return new FakeKeycloakAdminClient();
    }

    @Bean
    TrackingInvalidator authorizationCacheInvalidator() {
      return new TrackingInvalidator();
    }
  }

  static final class FakeKeycloakAdminClient implements KeycloakAdminClient {
    private List<KeycloakUser> users = List.of();
    private final Map<String, List<KeycloakGroup>> groups = new java.util.HashMap<>();
    private final Map<String, List<KeycloakRole>> roles = new java.util.HashMap<>();
    private RuntimeException failure;

    @Override
    public List<KeycloakUser> users() {
      if (failure != null) throw failure;
      return users;
    }

    @Override
    public Optional<KeycloakUser> user(String id) {
      if (failure != null) throw failure;
      return users.stream().filter(value -> value.id().equals(id)).findFirst();
    }

    @Override
    public List<KeycloakGroup> groups(String userId) {
      if (failure != null) throw failure;
      return groups.getOrDefault(userId, List.of());
    }

    @Override
    public List<KeycloakRole> realmRoles(String userId) {
      if (failure != null) throw failure;
      return roles.getOrDefault(userId, List.of());
    }

    void reset() {
      users = List.of();
      groups.clear();
      roles.clear();
      failure = null;
    }
  }

  static final class TrackingInvalidator implements AuthorizationCacheInvalidator {
    private final Set<AuthenticatedIdentity> identities = new LinkedHashSet<>();

    @Override
    public void invalidateIdentity(AuthenticatedIdentity identity) {
      identities.add(identity);
    }

    @Override
    public void invalidateResourceRules() {}

    @Override
    public void invalidateAll() {}

    void clear() {
      identities.clear();
    }
  }
}
