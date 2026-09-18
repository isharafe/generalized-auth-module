package io.github.isharafe.authorization.persistence.repository;

import io.github.isharafe.authorization.persistence.entity.PendingUserAssignmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingUserAssignmentRepository
    extends JpaRepository<PendingUserAssignmentEntity, Long> {}
