package io.github.isharafe.authorization.persistence.repository;

import io.github.isharafe.authorization.persistence.entity.UserPermissionGroupEntity;
import io.github.isharafe.authorization.persistence.entity.UserPermissionGroupId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPermissionGroupRepository
    extends JpaRepository<UserPermissionGroupEntity, UserPermissionGroupId> {
  List<UserPermissionGroupEntity> findByUserId(Long userId);
}
