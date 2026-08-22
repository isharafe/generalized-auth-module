package com.example.authorization.persistence.entity;

import com.example.authorization.domain.AccessMode;
import com.example.authorization.domain.ResourceRule;
import com.example.authorization.domain.ResourceType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "AUTH_RESOURCE_RULE")
public class ResourceRuleEntity extends AbstractCodedEntity {
  @Enumerated(EnumType.STRING)
  @Column(name = "RESOURCE_TYPE", nullable = false)
  private ResourceType resourceType;

  @Column(name = "RESOURCE_PATTERN", nullable = false)
  private String pattern;

  @Enumerated(EnumType.STRING)
  @Column(name = "ACCESS_MODE", nullable = false)
  private AccessMode accessMode;

  private int priority;

  public ResourceRule toDomain() {
    return new ResourceRule(
        getCode(), resourceType, pattern, accessMode, priority, isEnabled());
  }
}
