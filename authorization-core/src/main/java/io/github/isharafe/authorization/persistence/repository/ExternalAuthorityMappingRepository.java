package io.github.isharafe.authorization.persistence.repository;

import io.github.isharafe.authorization.persistence.entity.ExternalAuthorityMappingEntity;
import io.github.isharafe.authorization.domain.AssignmentTargetType;
import java.util.List;
import java.util.Optional;
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

  Optional<ExternalAuthorityMappingEntity>
      findBySourceSystemAndAuthorityTypeAndAuthorityValueAndTargetTypeAndTargetCode(
          String sourceSystem,
          String authorityType,
          String authorityValue,
          AssignmentTargetType targetType,
          String targetCode);

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
