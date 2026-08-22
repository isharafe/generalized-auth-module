package com.example.authorization.persistence.repository;

import com.example.authorization.persistence.entity.ExternalAuthorityMappingEntity;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExternalAuthorityMappingRepository
    extends JpaRepository<ExternalAuthorityMappingEntity, Long> {
  List<ExternalAuthorityMappingEntity>
      findBySourceSystemAndAuthorityTypeAndAuthorityValueAndEnabledTrue(
          String source, String type, String value);

  @Query("""
      select m from ExternalAuthorityMappingEntity m
      where :search = ''
         or lower(m.sourceSystem) like lower(concat('%', :search, '%'))
         or lower(m.authorityType) like lower(concat('%', :search, '%'))
         or lower(m.authorityValue) like lower(concat('%', :search, '%'))
         or lower(m.targetCode) like lower(concat('%', :search, '%'))
      """)
  Page<ExternalAuthorityMappingEntity> search(
      @Param("search") String search, Pageable pageable);
}
