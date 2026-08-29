package com.example.authorization.persistence.entity;

import com.example.authorization.domain.IdentityChangeEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
@Entity
@Table(
    name = "AUTH_IDENTITY_CHANGE_EVENT",
    uniqueConstraints =
        @UniqueConstraint(columnNames = {"SOURCE_SYSTEM", "EXTERNAL_EVENT_ID"}))
public class IdentityChangeEventEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "SOURCE_SYSTEM", nullable = false, length = 50)
  private String sourceSystem;

  @Column(name = "EXTERNAL_EVENT_ID", nullable = false, length = 255)
  private String eventId;

  @Column(name = "EVENT_TYPE", nullable = false, length = 50)
  private String eventType;

  @Column(name = "EXTERNAL_ISSUER", nullable = false, length = 500)
  private String issuer;

  @Column(name = "EXTERNAL_SUBJECT", nullable = false, length = 500)
  private String subject;

  @Column(name = "OCCURRED_AT")
  private Instant occurredAt;

  @Column(name = "RECEIVED_AT", nullable = false)
  private Instant receivedAt;

  @Column(nullable = false, length = 30)
  private String status;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "PROCESSING_STARTED_AT")
  private Instant processingStartedAt;

  @Column(name = "PROCESSED_AT")
  private Instant processedAt;

  @Column(name = "LAST_ERROR", length = 1000)
  private String lastError;

  @Setter(AccessLevel.NONE)
  @Version
  private long version;

  public static IdentityChangeEventEntity received(IdentityChangeEvent event, Instant now) {
    IdentityChangeEventEntity entity = new IdentityChangeEventEntity();
    entity.sourceSystem = event.sourceSystem();
    entity.eventId = event.eventId();
    entity.eventType = event.type().name();
    entity.issuer = event.identity().issuer();
    entity.subject = event.identity().subject();
    entity.occurredAt = event.occurredAt();
    entity.receivedAt = now;
    entity.status = "RECEIVED";
    return entity;
  }

  public boolean represents(IdentityChangeEvent event) {
    return eventType.equals(event.type().name())
        && issuer.equals(event.identity().issuer())
        && subject.equals(event.identity().subject());
  }

  public boolean completed() {
    return "PROCESSED".equals(status);
  }

  public boolean processingAfter(Instant threshold) {
    return "PROCESSING".equals(status)
        && processingStartedAt != null
        && processingStartedAt.isAfter(threshold);
  }

  public void start(Instant now) {
    status = "PROCESSING";
    attempts++;
    processingStartedAt = now;
    processedAt = null;
    lastError = null;
  }

  public void complete(Instant now) {
    status = "PROCESSED";
    processedAt = now;
    lastError = null;
  }

  public void fail(Instant now, String error) {
    status = "FAILED";
    processedAt = now;
    lastError = error;
  }
}
