package com.example.authorization.persistence.repository;

import com.example.authorization.persistence.entity.PendingUserAssignmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingUserAssignmentRepository
    extends JpaRepository<PendingUserAssignmentEntity, Long> {}
