package io.github.isharafe.authorization.persistence.entity;

import jakarta.persistence.*;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

@Getter
@Setter
@Entity
@BatchSize(size = 100)
@Table(name = "AUTH_ROLE")
public class RoleEntity extends AbstractCodedEntity {
  @Column(nullable = false)
  private String name;

  private String description;

  @ManyToMany(fetch = FetchType.LAZY)
  @BatchSize(size = 100)
  @JoinTable(
      name = "AUTH_ROLE_PERMISSION_GROUP",
      joinColumns = @JoinColumn(name = "ROLE_ID"),
      inverseJoinColumns = @JoinColumn(name = "PERMISSION_GROUP_ID"))
  @Setter(AccessLevel.NONE)
  private Set<PermissionGroupEntity> permissionGroups = new LinkedHashSet<>();
}
