package io.github.isharafe.authorization.persistence.entity;

import io.github.isharafe.authorization.domain.Permission;
import io.github.isharafe.authorization.domain.PermissionCode;
import io.github.isharafe.authorization.domain.ResourceType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "AUTH_PERMISSION")
public class PermissionEntity extends AbstractCodedEntity {
  @Column(nullable = false)
  private String name;

  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "RESOURCE_TYPE", nullable = false)
  private ResourceType resourceType;

  @Column(name = "RESOURCE_PATTERN", nullable = false)
  private String pattern;

  @PrePersist
  @PreUpdate
  void validatePermissionCode() {
    PermissionCode.validate(getCode(), resourceType);
  }

  public Permission toDomain() {
    return new Permission(getCode(), name, description, resourceType, pattern, isEnabled());
  }
}
