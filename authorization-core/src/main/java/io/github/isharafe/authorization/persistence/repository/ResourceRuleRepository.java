package io.github.isharafe.authorization.persistence.repository;

import io.github.isharafe.authorization.persistence.entity.ResourceRuleEntity;
import io.github.isharafe.authorization.domain.ResourceType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResourceRuleRepository extends JpaRepository<ResourceRuleEntity, Long> {
  List<ResourceRuleEntity> findByEnabledTrue();

  Optional<ResourceRuleEntity> findByCode(String code);

  @Query("""
      select r from ResourceRuleEntity r
      where :search = ''
         or lower(r.code) like lower(concat('%', :search, '%'))
         or lower(r.pattern) like lower(concat('%', :search, '%'))
      """)
  Page<ResourceRuleEntity> search(@Param("search") String search, Pageable pageable);

  @Query("""
      select r from ResourceRuleEntity r
      where r.resourceType = :resourceType
        and (:search = ''
          or lower(r.code) like lower(concat('%', :search, '%'))
          or lower(r.pattern) like lower(concat('%', :search, '%')))
      """)
  Page<ResourceRuleEntity> searchByResourceType(
      @Param("resourceType") ResourceType resourceType,
      @Param("search") String search,
      Pageable pageable);
}
