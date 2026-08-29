package com.example.authorization.cache;

import com.example.authorization.domain.AuthenticatedIdentity;
import com.example.authorization.domain.AuthorizationCacheInvalidation;
import com.example.authorization.spi.AuthorizationCacheInvalidator;
import com.example.authorization.spi.AuthorizationInvalidationPublisher;
import com.example.authorization.spi.AuthorizationObservation;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public final class PublishingAuthorizationCacheInvalidator
    implements AuthorizationCacheInvalidator {
  private final AuthorizationCacheInvalidator local;
  private final AuthorizationInvalidationPublisher publisher;
  private final AuthorizationObservation observation;
  private final String origin;
  private final Clock clock;

  public PublishingAuthorizationCacheInvalidator(
      AuthorizationCacheInvalidator local,
      AuthorizationInvalidationPublisher publisher,
      AuthorizationObservation observation,
      String origin,
      Clock clock) {
    this.local = local;
    this.publisher = publisher;
    this.observation = observation;
    this.origin = origin;
    this.clock = clock;
  }

  @Override
  public void invalidateIdentity(AuthenticatedIdentity identity) {
    local.invalidateIdentity(identity);
    observation.recordInvalidation("identity", false);
    publish(
        AuthorizationCacheInvalidation.Scope.IDENTITY,
        identity.issuer(),
        identity.subject());
  }

  @Override
  public void invalidateResourceRules() {
    local.invalidateResourceRules();
    observation.recordInvalidation("resource-rules", false);
    publish(AuthorizationCacheInvalidation.Scope.RESOURCE_RULES, null, null);
  }

  @Override
  public void invalidateAll() {
    local.invalidateAll();
    observation.recordInvalidation("all", false);
    publish(AuthorizationCacheInvalidation.Scope.ALL, null, null);
  }

  public void receive(AuthorizationCacheInvalidation invalidation) {
    switch (invalidation.scope()) {
      case IDENTITY ->
          local.invalidateIdentity(
              new AuthenticatedIdentity(
                  invalidation.issuer(), invalidation.subject(), null));
      case RESOURCE_RULES -> local.invalidateResourceRules();
      case ALL -> local.invalidateAll();
    }
    observation.recordInvalidation(metricScope(invalidation.scope()), true);
  }

  public String origin() {
    return origin;
  }

  private void publish(
      AuthorizationCacheInvalidation.Scope scope, String issuer, String subject) {
    publisher.publish(
        new AuthorizationCacheInvalidation(
            UUID.randomUUID().toString(), origin, scope, issuer, subject, Instant.now(clock)));
  }

  private String metricScope(AuthorizationCacheInvalidation.Scope scope) {
    return scope.name().toLowerCase(Locale.ROOT).replace('_', '-');
  }
}
