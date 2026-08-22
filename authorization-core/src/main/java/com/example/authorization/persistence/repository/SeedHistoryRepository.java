package com.example.authorization.persistence.repository;

import com.example.authorization.persistence.entity.SeedHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeedHistoryRepository extends JpaRepository<SeedHistoryEntity, Long> {
  boolean existsBySourceAndChecksumAndStatus(String source, String checksum, String status);
}
