package io.github.isharafe.authorization.admin.service;

import io.github.isharafe.authorization.domain.AuthenticatedIdentity;
import io.github.isharafe.authorization.domain.AuthorizationChangeAuditEvent;
import io.github.isharafe.authorization.persistence.entity.UserEntity;
import io.github.isharafe.authorization.security.SpringAuthenticationIdentityResolver;
import io.github.isharafe.authorization.spi.AuthorizationAuditPublisher;
import io.github.isharafe.authorization.spi.AuthorizationCacheInvalidator;
import io.github.isharafe.authorization.util.AfterCommitExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@RequiredArgsConstructor
public final class AdminChangePublisher {
  private final AuthorizationCacheInvalidator cache;
  private final AuthorizationAuditPublisher audit;
  private final SpringAuthenticationIdentityResolver identityResolver;
  private final AfterCommitExecutor afterCommit;

  public void all(String type, String target, String action) {
    publish(cache::invalidateAll, event(type, target, action));
  }

  public void rules(String type, String target, String action) {
    publish(cache::invalidateResourceRules, event(type, target, action));
  }

  public void identity(UserEntity user, String type, String target, String action) {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity(user.getIssuer(), user.getSubject(), user.getUsername());
    publish(() -> cache.invalidateIdentity(identity), event(type, target, action));
  }

  public void publish(Runnable invalidation, AuthorizationChangeAuditEvent event) {
    afterCommit.execute(
        () -> {
          invalidation.run();
          audit.publishChange(event);
        });
  }

  private AuthorizationChangeAuditEvent event(String type, String target, String action) {
    AuthenticatedIdentity actor = actor();
    return new AuthorizationChangeAuditEvent(
        type,
        actor == null ? null : actor.issuer(),
        actor == null ? null : actor.subject(),
        target,
        action,
        null);
  }

  private AuthenticatedIdentity actor() {
    try {
      Authentication authentication =
          SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();
      return identityResolver.resolve(authentication);
    } catch (RuntimeException ignored) {
      return null;
    }
  }
}
