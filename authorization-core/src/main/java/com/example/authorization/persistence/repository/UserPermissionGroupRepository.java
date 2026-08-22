package com.example.authorization.persistence.repository;

import com.example.authorization.persistence.entity.UserPermissionGroupEntity;
import com.example.authorization.persistence.entity.UserPermissionGroupId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPermissionGroupRepository
    extends JpaRepository<UserPermissionGroupEntity, UserPermissionGroupId> {
  List<UserPermissionGroupEntity> findByUserId(Long userId);
}
