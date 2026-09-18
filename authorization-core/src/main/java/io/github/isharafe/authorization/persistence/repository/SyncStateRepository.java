package io.github.isharafe.authorization.persistence.repository;

import io.github.isharafe.authorization.persistence.entity.SyncStateEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SyncStateRepository extends JpaRepository<SyncStateEntity, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from SyncStateEntity s where s.key = :key")
  Optional<SyncStateEntity> findByKeyForUpdate(@Param("key") String key);
}
