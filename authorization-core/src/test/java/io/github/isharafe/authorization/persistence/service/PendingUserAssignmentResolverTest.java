package io.github.isharafe.authorization.persistence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.github.isharafe.authorization.domain.AuthenticatedIdentity;
import io.github.isharafe.authorization.persistence.entity.UserEntity;
import io.github.isharafe.authorization.persistence.repository.PendingUserAssignmentRepository;
import io.github.isharafe.authorization.persistence.repository.PermissionGroupRepository;
import io.github.isharafe.authorization.persistence.repository.RoleRepository;
import io.github.isharafe.authorization.persistence.repository.UserPermissionGroupRepository;
import io.github.isharafe.authorization.persistence.repository.UserRepository;
import io.github.isharafe.authorization.persistence.repository.UserRoleRepository;
import io.github.isharafe.authorization.spi.AuthorizationCacheInvalidator;
import io.github.isharafe.authorization.util.AfterCommitExecutor;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PendingUserAssignmentResolverTest {
  @Test
  void queriesOnlyAssignmentsForTheResolvedIdentity() {
    AuthenticatedIdentity identity = new AuthenticatedIdentity("issuer", "subject", "user");
    UserRepository users = mock(UserRepository.class);
    PendingUserAssignmentRepository pending = mock(PendingUserAssignmentRepository.class);
    UserEntity user = new UserEntity();
    user.setIssuer(identity.issuer());
    user.setSubject(identity.subject());
    when(users.findByIssuerAndSubject(identity.issuer(), identity.subject()))
        .thenReturn(Optional.of(user));
    when(pending.findByIssuerAndSubject(identity.issuer(), identity.subject()))
        .thenReturn(List.of());

    PendingUserAssignmentResolver resolver =
        new PendingUserAssignmentResolver(
            users,
            mock(RoleRepository.class),
            mock(PermissionGroupRepository.class),
            mock(UserRoleRepository.class),
            mock(UserPermissionGroupRepository.class),
            pending,
            mock(AuthorizationCacheInvalidator.class),
            new AfterCommitExecutor());

    assertThat(resolver.resolve(identity)).isZero();
    verify(pending).findByIssuerAndSubject(identity.issuer(), identity.subject());
    verifyNoMoreInteractions(pending);
  }
}
