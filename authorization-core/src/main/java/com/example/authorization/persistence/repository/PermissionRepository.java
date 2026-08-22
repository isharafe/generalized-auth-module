package com.example.authorization.persistence.repository;

import com.example.authorization.persistence.entity.PermissionEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PermissionRepository extends JpaRepository<PermissionEntity, Long> {
  Optional<PermissionEntity> findByCode(String code);

  @Query("""
      select p from PermissionEntity p
      where :search = ''
         or lower(p.code) like lower(concat('%', :search, '%'))
         or lower(p.name) like lower(concat('%', :search, '%'))
         or lower(p.pattern) like lower(concat('%', :search, '%'))
      """)
  Page<PermissionEntity> search(@Param("search") String search, Pageable pageable);
}
