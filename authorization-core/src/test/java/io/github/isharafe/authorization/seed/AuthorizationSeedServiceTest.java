package io.github.isharafe.authorization.seed;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.isharafe.authorization.domain.AuthenticatedIdentity;
import io.github.isharafe.authorization.persistence.entity.UserEntity;
import io.github.isharafe.authorization.persistence.repository.ExternalAuthorityMappingRepository;
import io.github.isharafe.authorization.persistence.repository.PendingUserAssignmentRepository;
import io.github.isharafe.authorization.persistence.repository.PermissionGroupRepository;
import io.github.isharafe.authorization.persistence.repository.PermissionRepository;
import io.github.isharafe.authorization.persistence.repository.ResourceRuleRepository;
import io.github.isharafe.authorization.persistence.repository.RoleRepository;
import io.github.isharafe.authorization.persistence.repository.SeedHistoryRepository;
import io.github.isharafe.authorization.persistence.repository.UserPermissionGroupRepository;
import io.github.isharafe.authorization.persistence.repository.UserRepository;
import io.github.isharafe.authorization.persistence.repository.UserRoleRepository;
import io.github.isharafe.authorization.persistence.service.PendingUserAssignmentResolver;
import io.github.isharafe.authorization.seed.AuthorizationSeedDefinition.ExternalAuthorityMappingSeed;
import io.github.isharafe.authorization.seed.AuthorizationSeedDefinition.ExternalAuthorityTargetSeed;
import io.github.isharafe.authorization.seed.AuthorizationSeedDefinition.RoleSeed;
import io.github.isharafe.authorization.seed.AuthorizationSeedDefinition.UserSeed;
import io.github.isharafe.authorization.spi.AuthorizationCacheInvalidator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AuthorizationSeedServiceTest {
  private final PermissionRepository permissions = mock(PermissionRepository.class);
  private final PermissionGroupRepository groups = mock(PermissionGroupRepository.class);
  private final RoleRepository roles = mock(RoleRepository.class);
  private final ResourceRuleRepository rules = mock(ResourceRuleRepository.class);
  private final ExternalAuthorityMappingRepository externalMappings =
      mock(ExternalAuthorityMappingRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final UserRoleRepository userRoles = mock(UserRoleRepository.class);
  private final UserPermissionGroupRepository userGroups = mock(UserPermissionGroupRepository.class);
  private final PendingUserAssignmentRepository pending =
      mock(PendingUserAssignmentRepository.class);
  private final SeedHistoryRepository history = mock(SeedHistoryRepository.class);
  private final AuthorizationCacheInvalidator cache = mock(AuthorizationCacheInvalidator.class);

  @Test
  void resolvesPendingAssignmentsWhenASeedCreatesTheIdentity() {
    PendingUserAssignmentResolver resolver = mock(PendingUserAssignmentResolver.class);
    when(users.findByIssuerAndSubject("local", "alice")).thenReturn(Optional.empty());
    when(users.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    AuthorizationSeedDefinition seed = new AuthorizationSeedDefinition();
    seed.setUsers(List.of(new UserSeed("local", "alice", "alice", null, null, null, true)));

    service(provider(resolver)).apply(seed);

    verify(resolver).resolve(new AuthenticatedIdentity("local", "alice", "alice"));
  }

  @Test
  void invalidatesCachesOnlyAfterCommit() {
    TransactionSynchronizationManager.initSynchronization();
    try {
      service(provider()).apply(new AuthorizationSeedDefinition());

      verify(cache, never()).invalidateAll();
      List<TransactionSynchronization> synchronizations =
          TransactionSynchronizationManager.getSynchronizations();
      synchronizations.forEach(TransactionSynchronization::afterCommit);
      verify(cache).invalidateAll();
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void mergesExternalAuthorityMappingsIdempotentlyByNaturalKey() {
    AuthorizationSeedDefinition seed = new AuthorizationSeedDefinition();
    seed.setRoles(List.of(new RoleSeed("FINANCE", "Finance", null, List.of(), true)));
    seed.setExternalAuthorityMappings(
        List.of(
            new ExternalAuthorityMappingSeed(
                "KEYCLOAK",
                "GROUP",
                "/Finance",
                new ExternalAuthorityTargetSeed("ROLE", "FINANCE"),
                true)));

    service(provider()).apply(seed);

    verify(externalMappings)
        .findBySourceSystemAndAuthorityTypeAndAuthorityValueAndTargetTypeAndTargetCode(
            "KEYCLOAK", "GROUP", "/Finance", "ROLE", "FINANCE");
    verify(externalMappings)
        .save(
            argThat(
                mapping ->
                    mapping.getSourceSystem().equals("KEYCLOAK")
                        && mapping.getAuthorityType().equals("GROUP")
                        && mapping.getAuthorityValue().equals("/Finance")
                        && mapping.getTargetType().equals("ROLE")
                        && mapping.getTargetCode().equals("FINANCE")
                        && mapping.isEnabled()));
  }

  private AuthorizationSeedService service(
      ObjectProvider<PendingUserAssignmentResolver> resolver) {
    return new AuthorizationSeedService(
        permissions,
        groups,
        roles,
        rules,
        externalMappings,
        users,
        userRoles,
        userGroups,
        pending,
        history,
        cache,
        resolver,
        new AuthorizationSeedValidator());
  }

  private ObjectProvider<PendingUserAssignmentResolver> provider(
      PendingUserAssignmentResolver... resolvers) {
    StaticListableBeanFactory factory = new StaticListableBeanFactory();
    for (int index = 0; index < resolvers.length; index++)
      factory.addBean("resolver" + index, resolvers[index]);
    return factory.getBeanProvider(PendingUserAssignmentResolver.class);
  }
}
