package io.github.isharafe.authorization.persistence.repository;

import io.github.isharafe.authorization.persistence.entity.SeedHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeedHistoryRepository extends JpaRepository<SeedHistoryEntity, Long> {
  boolean existsBySourceAndChecksumAndStatus(String source, String checksum, String status);
}
