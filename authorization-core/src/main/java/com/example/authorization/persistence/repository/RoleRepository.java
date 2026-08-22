package com.example.authorization.persistence.repository;

import com.example.authorization.persistence.entity.RoleEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleRepository extends JpaRepository<RoleEntity, Long> {
  Optional<RoleEntity> findByCode(String code);

  @Query("""
      select r from RoleEntity r
      where :search = ''
         or lower(r.code) like lower(concat('%', :search, '%'))
         or lower(r.name) like lower(concat('%', :search, '%'))
      """)
  Page<RoleEntity> search(@Param("search") String search, Pageable pageable);
}
