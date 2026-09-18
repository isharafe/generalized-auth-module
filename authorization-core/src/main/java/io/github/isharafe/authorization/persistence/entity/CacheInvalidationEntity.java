package io.github.isharafe.authorization.persistence.entity;

import io.github.isharafe.authorization.domain.AuthorizationCacheInvalidation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

@Getter
@Entity
@Table(name = "AUTH_CACHE_INVALIDATION")
public class CacheInvalidationEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "EVENT_ID", nullable = false, unique = true, length = 100)
  private String eventId;

  @Column(nullable = false, length = 100)
  private String origin;

  @Column(nullable = false, length = 30)
  private String scope;

  @Column(name = "EXTERNAL_ISSUER", length = 500)
  private String issuer;

  @Column(name = "EXTERNAL_SUBJECT", length = 500)
  private String subject;

  @Column(name = "CREATED_AT", nullable = false)
  private Instant createdAt;

  public static CacheInvalidationEntity from(AuthorizationCacheInvalidation invalidation) {
    CacheInvalidationEntity entity = new CacheInvalidationEntity();
    entity.eventId = invalidation.eventId();
    entity.origin = invalidation.origin();
    entity.scope = invalidation.scope().name();
    entity.issuer = invalidation.issuer();
    entity.subject = invalidation.subject();
    entity.createdAt = invalidation.createdAt();
    return entity;
  }

  public AuthorizationCacheInvalidation toDomain() {
    return new AuthorizationCacheInvalidation(
        eventId,
        origin,
        AuthorizationCacheInvalidation.Scope.valueOf(scope),
        issuer,
        subject,
        createdAt);
  }
}
