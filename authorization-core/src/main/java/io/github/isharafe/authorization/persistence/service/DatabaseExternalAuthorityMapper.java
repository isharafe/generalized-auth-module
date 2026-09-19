package io.github.isharafe.authorization.persistence.service;

import io.github.isharafe.authorization.persistence.repository.ExternalAuthorityMappingRepository;
import io.github.isharafe.authorization.spi.AuthorizationObservation;
import io.github.isharafe.authorization.spi.ExternalAuthorityMapper;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

public class DatabaseExternalAuthorityMapper implements ExternalAuthorityMapper {
  private final ExternalAuthorityMappingRepository repository;
  private final AuthorizationObservation observation;

  public DatabaseExternalAuthorityMapper(
      ExternalAuthorityMappingRepository repository, AuthorizationObservation observation) {
    this.repository = repository;
    this.observation = observation;
  }

  @Override
  public Set<String> map(String sourceSystem, String authorityType, String authorityValue) {
    long started = System.nanoTime();
    String result = "success";
    try {
      return repository
          .findBySourceSystemAndAuthorityTypeAndAuthorityValueAndEnabledTrue(
              sourceSystem, authorityType, authorityValue)
          .stream()
          .map(value -> value.getTargetType() + ":" + value.getTargetCode())
          .collect(Collectors.toUnmodifiableSet());
    } catch (RuntimeException exception) {
      result = "failure";
      throw exception;
    } finally {
      observation.recordPersistenceOperation(
          "external_authority_map", result, Duration.ofNanos(System.nanoTime() - started));
    }
  }
}
