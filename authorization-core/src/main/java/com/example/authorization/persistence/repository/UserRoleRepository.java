package com.example.authorization.persistence.repository;

import com.example.authorization.persistence.entity.UserRoleEntity;
import com.example.authorization.persistence.entity.UserRoleId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRoleEntity, UserRoleId> {
  List<UserRoleEntity> findByUserId(Long userId);

  List<UserRoleEntity> findByRoleId(Long roleId);
}
