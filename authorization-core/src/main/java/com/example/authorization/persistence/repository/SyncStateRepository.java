package com.example.authorization.persistence.repository;

import com.example.authorization.persistence.entity.SyncStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncStateRepository extends JpaRepository<SyncStateEntity, String> {}
