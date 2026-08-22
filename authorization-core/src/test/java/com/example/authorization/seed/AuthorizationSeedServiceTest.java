package com.example.authorization.seed;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.authorization.domain.AuthenticatedIdentity;
import com.example.authorization.persistence.entity.UserEntity;
import com.example.authorization.persistence.repository.PendingUserAssignmentRepository;
import com.example.authorization.persistence.repository.PermissionGroupRepository;
import com.example.authorization.persistence.repository.PermissionRepository;
import com.example.authorization.persistence.repository.ResourceRuleRepository;
import com.example.authorization.persistence.repository.RoleRepository;
import com.example.authorization.persistence.repository.SeedHistoryRepository;
import com.example.authorization.persistence.repository.UserPermissionGroupRepository;
import com.example.authorization.persistence.repository.UserRepository;
import com.example.authorization.persistence.repository.UserRoleRepository;
import com.example.authorization.persistence.service.PendingUserAssignmentResolver;
import com.example.authorization.seed.AuthorizationSeedDefinition.UserSeed;
import com.example.authorization.spi.AuthorizationCacheInvalidator;
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

  private AuthorizationSeedService service(
      ObjectProvider<PendingUserAssignmentResolver> resolver) {
    return new AuthorizationSeedService(
        permissions,
        groups,
        roles,
        rules,
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
