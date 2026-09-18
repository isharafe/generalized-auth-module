package io.github.isharafe.authorization.persistence.entity;

import jakarta.persistence.*;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "AUTH_PERMISSION_GROUP")
public class PermissionGroupEntity extends AbstractCodedEntity {
  @Column(nullable = false)
  private String name;

  private String description;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "AUTH_PERMISSION_GROUP_PERMISSION",
      joinColumns = @JoinColumn(name = "PERMISSION_GROUP_ID"),
      inverseJoinColumns = @JoinColumn(name = "PERMISSION_ID"))
  @Setter(AccessLevel.NONE)
  private Set<PermissionEntity> permissions = new LinkedHashSet<>();
}
