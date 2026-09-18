package io.github.isharafe.authorization.persistence.entity;

import io.github.isharafe.authorization.domain.AssignmentSource;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "AUTH_USER_PERMISSION_GROUP")
public class UserPermissionGroupEntity {
  @EmbeddedId private UserPermissionGroupId id;

  @MapsId("userId")
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "USER_ID")
  private UserEntity user;

  @MapsId("permissionGroupId")
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "PERMISSION_GROUP_ID")
  private PermissionGroupEntity permissionGroup;

  @Enumerated(EnumType.STRING)
  @Column(name = "ASSIGNMENT_SOURCE", nullable = false)
  private AssignmentSource source;

  @Column(name = "SOURCE_REFERENCE")
  private String sourceReference;

  public UserPermissionGroupEntity(
      UserEntity user, PermissionGroupEntity group, AssignmentSource source, String reference) {
    this.user = user;
    this.permissionGroup = group;
    this.source = source;
    this.sourceReference = reference;
    this.id = new UserPermissionGroupId(user.getId(), group.getId());
  }
}
