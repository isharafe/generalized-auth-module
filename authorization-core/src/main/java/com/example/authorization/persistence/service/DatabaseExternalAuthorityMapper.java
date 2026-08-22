package com.example.authorization.persistence.service;

import com.example.authorization.persistence.repository.ExternalAuthorityMappingRepository;
import com.example.authorization.spi.ExternalAuthorityMapper;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
@RequiredArgsConstructor
public class DatabaseExternalAuthorityMapper implements ExternalAuthorityMapper {
  private final ExternalAuthorityMappingRepository repository;

  @Override
  public Set<String> map(String sourceSystem, String authorityType, String authorityValue) {
    return repository
        .findBySourceSystemAndAuthorityTypeAndAuthorityValueAndEnabledTrue(
            sourceSystem, authorityType, authorityValue)
        .stream()
        .map(value -> value.getTargetType() + ":" + value.getTargetCode())
        .collect(Collectors.toUnmodifiableSet());
  }
}
