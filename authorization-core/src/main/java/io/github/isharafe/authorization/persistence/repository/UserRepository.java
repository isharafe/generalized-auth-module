package io.github.isharafe.authorization.persistence.repository;

import io.github.isharafe.authorization.persistence.entity.UserEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
  @EntityGraph(
      attributePaths = {
        "roles.role.permissionGroups.permissions",
        "permissionGroups.permissionGroup.permissions"
      })
  Optional<UserEntity> findByIssuerAndSubjectAndEnabledTrue(String issuer, String subject);

  Optional<UserEntity> findByIssuerAndSubject(String issuer, String subject);

  @EntityGraph(attributePaths = {"roles.role", "permissionGroups.permissionGroup"})
  @Query("select u from UserEntity u where u.id = :id")
  Optional<UserEntity> findDetailedById(@Param("id") Long id);

  @Query("""
      select u from UserEntity u
      where :search = ''
         or lower(u.issuer) like lower(concat('%', :search, '%'))
         or lower(u.subject) like lower(concat('%', :search, '%'))
         or lower(coalesce(u.username, '')) like lower(concat('%', :search, '%'))
         or lower(coalesce(u.email, '')) like lower(concat('%', :search, '%'))
      """)
  Page<UserEntity> search(@Param("search") String search, Pageable pageable);
}
