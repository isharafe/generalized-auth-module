package com.example.authorization.persistence.repository;

import com.example.authorization.domain.AuditEventKind;
import com.example.authorization.persistence.entity.AuditEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, Long> {
  @Query("""
      select a from AuditEventEntity a
      where (:eventKind is null or a.eventKind = :eventKind)
        and (:eventType is null or a.eventType = :eventType)
        and (:actor is null
          or lower(coalesce(a.actorIssuer, '')) like lower(concat('%', :actor, '%'))
          or lower(coalesce(a.actorSubject, '')) like lower(concat('%', :actor, '%')))
        and (:target is null
          or lower(coalesce(a.target, '')) like lower(concat('%', :target, '%')))
      """)
  Page<AuditEventEntity> search(
      @Param("eventKind") AuditEventKind eventKind,
      @Param("eventType") String eventType,
      @Param("actor") String actor,
      @Param("target") String target,
      Pageable pageable);
}
