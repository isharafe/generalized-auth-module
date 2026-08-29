package com.example.authorization.persistence.repository;

import com.example.authorization.persistence.entity.CacheInvalidationEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CacheInvalidationRepository
    extends JpaRepository<CacheInvalidationEntity, Long> {
  List<CacheInvalidationEntity> findByIdGreaterThanOrderByIdAsc(long id, Pageable pageable);

  @Modifying
  @Transactional
  @Query("delete from CacheInvalidationEntity e where e.createdAt < :cutoff")
  int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
