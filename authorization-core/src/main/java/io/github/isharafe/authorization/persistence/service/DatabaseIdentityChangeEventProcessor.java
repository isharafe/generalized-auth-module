package io.github.isharafe.authorization.persistence.service;

import io.github.isharafe.authorization.config.AuthorizationProperties;
import io.github.isharafe.authorization.domain.IdentityChangeEvent;
import io.github.isharafe.authorization.domain.IdentityChangeProcessingResult;
import io.github.isharafe.authorization.persistence.entity.IdentityChangeEventEntity;
import io.github.isharafe.authorization.persistence.repository.IdentityChangeEventRepository;
import io.github.isharafe.authorization.observability.NoOpAuthorizationObservation;
import io.github.isharafe.authorization.spi.AuthorizationObservation;
import io.github.isharafe.authorization.spi.IdentityChangeEventProcessor;
import io.github.isharafe.authorization.spi.IdentitySynchronizationProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

public final class DatabaseIdentityChangeEventProcessor implements IdentityChangeEventProcessor {
  private final IdentityChangeEventRepository events;
  private final ObjectProvider<IdentitySynchronizationProvider> synchronizationProviders;
  private final AuthorizationProperties properties;
  private final Clock clock;
  private final AuthorizationObservation observation;
  private final TransactionTemplate ledgerTransaction;

  public DatabaseIdentityChangeEventProcessor(
      IdentityChangeEventRepository events,
      ObjectProvider<IdentitySynchronizationProvider> synchronizationProviders,
      AuthorizationProperties properties,
      Clock clock,
      PlatformTransactionManager transactionManager) {
    this(
        events,
        synchronizationProviders,
        properties,
        clock,
        transactionManager,
        new NoOpAuthorizationObservation());
  }

  public DatabaseIdentityChangeEventProcessor(
      IdentityChangeEventRepository events,
      ObjectProvider<IdentitySynchronizationProvider> synchronizationProviders,
      AuthorizationProperties properties,
      Clock clock,
      PlatformTransactionManager transactionManager,
      AuthorizationObservation observation) {
    this.events = events;
    this.synchronizationProviders = synchronizationProviders;
    this.properties = properties;
    this.clock = clock;
    this.observation = observation;
    ledgerTransaction = new TransactionTemplate(transactionManager);
    ledgerTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Override
  public IdentityChangeProcessingResult process(IdentityChangeEvent event) {
    if (event == null) throw new IllegalArgumentException("event is required");
    long started = System.nanoTime();
    try {
      IdentityChangeProcessingResult result = processClaimed(event);
      observation.recordIdentityEvent(
          result, Duration.ofNanos(System.nanoTime() - started));
      return result;
    } catch (RuntimeException exception) {
      observation.recordIdentityEventFailure(Duration.ofNanos(System.nanoTime() - started));
      throw exception;
    }
  }

  private IdentityChangeProcessingResult processClaimed(IdentityChangeEvent event) {
    ensureRecorded(event);
    ClaimResult claim = claim(event);
    if (claim == ClaimResult.DUPLICATE) return IdentityChangeProcessingResult.DUPLICATE;
    if (claim == ClaimResult.IN_PROGRESS) return IdentityChangeProcessingResult.IN_PROGRESS;

    try {
      IdentitySynchronizationProvider synchronization = synchronizationProviders.getIfAvailable();
      if (synchronization == null || !synchronization.supported())
        throw new IdentityChangeProcessingException(
            "Identity synchronization is not configured for event processing");
      synchronization.synchronize(event.identity());
      complete(event);
      return IdentityChangeProcessingResult.PROCESSED;
    } catch (RuntimeException exception) {
      fail(event, exception);
      if (exception instanceof IdentityChangeProcessingException processingException)
        throw processingException;
      throw new IdentityChangeProcessingException(
          "Identity change event " + event.eventId() + " could not be processed", exception);
    }
  }

  private void ensureRecorded(IdentityChangeEvent event) {
    if (events.findBySourceSystemAndEventId(event.sourceSystem(), event.eventId()).isPresent())
      return;
    try {
      ledgerTransaction.executeWithoutResult(
          status ->
              events.saveAndFlush(
                  IdentityChangeEventEntity.received(event, Instant.now(clock))));
    } catch (DataIntegrityViolationException duplicateCandidate) {
      if (events.findBySourceSystemAndEventId(event.sourceSystem(), event.eventId()).isEmpty())
        throw duplicateCandidate;
    }
  }

  private ClaimResult claim(IdentityChangeEvent event) {
    ClaimResult result =
        ledgerTransaction.execute(
            status -> {
              IdentityChangeEventEntity entity = locked(event);
              validateIdentity(entity, event);
              if (entity.completed()) return ClaimResult.DUPLICATE;
              Instant now = Instant.now(clock);
              Instant staleBefore =
                  now.minus(properties.getIdentityEvents().getProcessingTimeout());
              if (entity.processingAfter(staleBefore)) return ClaimResult.IN_PROGRESS;
              entity.start(now);
              events.save(entity);
              return ClaimResult.CLAIMED;
            });
    if (result == null)
      throw new IdentityChangeProcessingException("Identity change event claim returned no result");
    return result;
  }

  private void complete(IdentityChangeEvent event) {
    ledgerTransaction.executeWithoutResult(
        status -> {
          IdentityChangeEventEntity entity = locked(event);
          validateIdentity(entity, event);
          entity.complete(Instant.now(clock));
          events.save(entity);
        });
  }

  private void fail(IdentityChangeEvent event, RuntimeException exception) {
    try {
      ledgerTransaction.executeWithoutResult(
          status -> {
            IdentityChangeEventEntity entity = locked(event);
            validateIdentity(entity, event);
            entity.fail(Instant.now(clock), safeError(exception));
            events.save(entity);
          });
    } catch (RuntimeException ignored) {
      // Preserve the original processing failure for the caller.
    }
  }

  private IdentityChangeEventEntity locked(IdentityChangeEvent event) {
    return events
        .findForUpdate(event.sourceSystem(), event.eventId())
        .orElseThrow(
            () ->
                new IdentityChangeProcessingException(
                    "Identity change event ledger row is missing"));
  }

  private void validateIdentity(IdentityChangeEventEntity entity, IdentityChangeEvent event) {
    if (!entity.represents(event))
      throw new IdentityChangeEventConflictException(
          "Event id " + event.eventId() + " was already used for different identity data");
  }

  private String safeError(RuntimeException exception) {
    return exception.getClass().getSimpleName();
  }

  private enum ClaimResult {
    CLAIMED,
    DUPLICATE,
    IN_PROGRESS
  }
}
