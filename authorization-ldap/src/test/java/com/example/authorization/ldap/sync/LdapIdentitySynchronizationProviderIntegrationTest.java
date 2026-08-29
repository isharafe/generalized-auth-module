package com.example.authorization.ldap.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.authorization.domain.AssignmentSource;
import com.example.authorization.domain.AuthenticatedIdentity;
import com.example.authorization.ldap.client.LdapAuthority;
import com.example.authorization.ldap.client.LdapDirectoryClient;
import com.example.authorization.ldap.client.LdapUser;
import com.example.authorization.persistence.entity.AuditEventEntity;
import com.example.authorization.persistence.entity.ExternalAuthorityMappingEntity;
import com.example.authorization.persistence.entity.PendingUserAssignmentEntity;
import com.example.authorization.persistence.entity.PermissionGroupEntity;
import com.example.authorization.persistence.entity.RoleEntity;
import com.example.authorization.persistence.entity.SyncStateEntity;
import com.example.authorization.persistence.entity.UserEntity;
import com.example.authorization.persistence.entity.UserPermissionGroupEntity;
import com.example.authorization.persistence.entity.UserPermissionGroupId;
import com.example.authorization.persistence.entity.UserRoleEntity;
import com.example.authorization.persistence.entity.UserRoleId;
import com.example.authorization.persistence.repository.AuditEventRepository;
import com.example.authorization.persistence.repository.ExternalAuthorityMappingRepository;
import com.example.authorization.persistence.repository.PendingUserAssignmentRepository;
import com.example.authorization.persistence.repository.PermissionGroupRepository;
import com.example.authorization.persistence.repository.RoleRepository;
import com.example.authorization.persistence.repository.SyncStateRepository;
import com.example.authorization.persistence.repository.UserPermissionGroupRepository;
import com.example.authorization.persistence.repository.UserRepository;
import com.example.authorization.persistence.repository.UserRoleRepository;
import com.example.authorization.spi.AuthorizationCacheInvalidator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootTest(
    classes = {
      LdapIdentitySynchronizationProviderIntegrationTest.TestApplication.class,
      LdapIdentitySynchronizationProviderIntegrationTest.TestBeans.class
    },
    properties = {
      "spring.datasource.url=jdbc:h2:mem:ldap-sync;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.jpa.open-in-view=false",
      "authorization.source=ldap",
      "authorization.seed.enabled=false",
      "authorization.ldap.urls[0]=ldap://unused:389",
      "authorization.ldap.base-dn=dc=example,dc=com",
      "authorization.ldap.issuer=ldap-company",
      "authorization.ldap.sync.enabled=false"
    })
class LdapIdentitySynchronizationProviderIntegrationTest {
  private static final String ISSUER = "ldap-company";

  @jakarta.annotation.Resource private LdapIdentitySynchronizationProvider synchronization;
  @jakarta.annotation.Resource private FakeLdapDirectoryClient client;
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
    client.reset();
    invalidator.clear();
    SyncStateEntity state = syncStates.findById("GLOBAL_IDENTITY_SYNC").orElseThrow();
    state.setStatus("NEVER");
    state.setDetails(null);
    syncStates.saveAndFlush(state);
  }

  @Test
  void fullSyncMapsGroupsAndAttributesAndPreservesManualAndSeedAssignments() {
    RoleEntity manualRole = role("MANUAL_ROLE");
    RoleEntity staleRole = role("STALE_ROLE");
    RoleEntity synchronizedRole = role("SYNC_ROLE");
    RoleEntity pendingRole = role("PENDING_ROLE");
    PermissionGroupEntity synchronizedGroup = group("PAYROLL_ACCESS");
    mapping("GROUP", "Finance-Managers", "ROLE", synchronizedRole.getCode());
    mapping("ATTRIBUTE", "department=Payroll", "PERMISSION_GROUP", synchronizedGroup.getCode());

    UserEntity alice = user("u1", "old-alice");
    userRoles.save(new UserRoleEntity(alice, manualRole, AssignmentSource.MANUAL, "admin-api"));
    userRoles.save(
        new UserRoleEntity(alice, staleRole, AssignmentSource.IDENTITY_SYNC, "GROUP:Old"));

    UserEntity missing = user("missing", "missing-user");
    userRoles.save(new UserRoleEntity(missing, manualRole, AssignmentSource.SEED, "seed"));
    userRoles.save(
        new UserRoleEntity(missing, staleRole, AssignmentSource.IDENTITY_SYNC, "GROUP:Old"));

    PendingUserAssignmentEntity pendingAssignment = new PendingUserAssignmentEntity();
    pendingAssignment.setIssuer(ISSUER);
    pendingAssignment.setSubject("u2");
    pendingAssignment.setTargetType("ROLE");
    pendingAssignment.setTargetCode(pendingRole.getCode());
    pendingAssignment.setSource(AssignmentSource.SEED);
    pending.save(pendingAssignment);

    client.users =
        List.of(
            ldapUser(
                "u1",
                "alice",
                Set.of(
                    new LdapAuthority("GROUP", "Finance-Managers"),
                    new LdapAuthority("ATTRIBUTE", "department=Payroll"))),
            ldapUser("u2", "bob", Set.of()));

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
        .satisfies(
            assignment -> {
              assertThat(assignment.getSource()).isEqualTo(AssignmentSource.IDENTITY_SYNC);
              assertThat(assignment.getSourceReference()).isEqualTo("GROUP:Finance-Managers");
            });
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
    assertThat(synchronization.status().provider()).isEqualTo("ldap");
    assertThat(synchronization.status().status()).isEqualTo("COMPLETED");
    assertThat(audit.findAll())
        .extracting(AuditEventEntity::getEventType)
        .contains("IDENTITY_SYNC_STARTED", "IDENTITY_SYNC_COMPLETED");
  }

  @Test
  void targetedSyncUpdatesOneStableIdentityAndInvalidatesItsCache() {
    user("u1", "old-name");
    client.users = List.of(ldapUser("u1", "new-name", Set.of()));

    synchronization.synchronize(new AuthenticatedIdentity(ISSUER, "u1", "old-name"));

    assertThat(users.findByIssuerAndSubject(ISSUER, "u1").orElseThrow().getUsername())
        .isEqualTo("new-name");
    assertThat(invalidator.identities)
        .extracting(AuthenticatedIdentity::subject)
        .containsExactly("u1");
  }

  @Test
  void failedSyncRollsBackAndRecordsStatusAndAuditWithoutSecrets() {
    client.failure = new IllegalStateException("directory unavailable");

    assertThatThrownBy(synchronization::synchronizeAll)
        .isInstanceOf(LdapSynchronizationException.class)
        .hasMessageContaining("FULL");

    assertThat(synchronization.status().status()).isEqualTo("FAILED");
    assertThat(synchronization.status().details()).contains("directory unavailable");
    assertThat(audit.findAll())
        .extracting(AuditEventEntity::getEventType)
        .contains("IDENTITY_SYNC_STARTED", "IDENTITY_SYNC_FAILED");
  }

  private LdapUser ldapUser(String id, String username, Set<LdapAuthority> authorities) {
    return new LdapUser(
        id,
        "uid=" + username + ",ou=people,dc=example,dc=com",
        username,
        username + "@example.com",
        username,
        "User",
        true,
        authorities);
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
    value.setSourceSystem("LDAP");
    value.setAuthorityType(authorityType);
    value.setAuthorityValue(authority);
    value.setTargetType(targetType);
    value.setTargetCode(targetCode);
    value.setEnabled(true);
    mappings.saveAndFlush(value);
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class TestApplication {}

  @TestConfiguration(proxyBeanMethods = false)
  static class TestBeans {
    @Bean
    FakeLdapDirectoryClient ldapDirectoryClient() {
      return new FakeLdapDirectoryClient();
    }

    @Bean
    TrackingInvalidator authorizationCacheInvalidator() {
      return new TrackingInvalidator();
    }
  }

  static final class FakeLdapDirectoryClient implements LdapDirectoryClient {
    private List<LdapUser> users = List.of();
    private RuntimeException failure;

    @Override
    public List<LdapUser> users() {
      if (failure != null) throw failure;
      return users;
    }

    @Override
    public Optional<LdapUser> user(String stableId) {
      if (failure != null) throw failure;
      return users.stream().filter(value -> value.id().equals(stableId)).findFirst();
    }

    void reset() {
      users = List.of();
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
