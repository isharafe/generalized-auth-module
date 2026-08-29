package com.example.authorization.persistence.repository;

import com.example.authorization.persistence.entity.IdentityChangeEventEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdentityChangeEventRepository
    extends JpaRepository<IdentityChangeEventEntity, Long> {
  Optional<IdentityChangeEventEntity> findBySourceSystemAndEventId(
      String sourceSystem, String eventId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select e from IdentityChangeEventEntity e
      where e.sourceSystem = :sourceSystem and e.eventId = :eventId
      """)
  Optional<IdentityChangeEventEntity> findForUpdate(
      @Param("sourceSystem") String sourceSystem, @Param("eventId") String eventId);
}
