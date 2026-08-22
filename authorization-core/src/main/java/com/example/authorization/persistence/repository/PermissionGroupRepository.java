package com.example.authorization.persistence.repository;

import com.example.authorization.persistence.entity.PermissionGroupEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PermissionGroupRepository extends JpaRepository<PermissionGroupEntity, Long> {
  Optional<PermissionGroupEntity> findByCode(String code);

  @Query("""
      select g from PermissionGroupEntity g
      where :search = ''
         or lower(g.code) like lower(concat('%', :search, '%'))
         or lower(g.name) like lower(concat('%', :search, '%'))
      """)
  Page<PermissionGroupEntity> search(@Param("search") String search, Pageable pageable);
}
