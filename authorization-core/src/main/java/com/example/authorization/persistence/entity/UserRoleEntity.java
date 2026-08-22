package com.example.authorization.persistence.entity;

import com.example.authorization.domain.AssignmentSource;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "AUTH_USER_ROLE")
public class UserRoleEntity {
  @EmbeddedId private UserRoleId id;

  @MapsId("userId")
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "USER_ID")
  private UserEntity user;

  @MapsId("roleId")
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ROLE_ID")
  private RoleEntity role;

  @Enumerated(EnumType.STRING)
  @Column(name = "ASSIGNMENT_SOURCE", nullable = false)
  private AssignmentSource source;

  @Column(name = "SOURCE_REFERENCE")
  private String sourceReference;

  public UserRoleEntity(
      UserEntity user, RoleEntity role, AssignmentSource source, String reference) {
    this.user = user;
    this.role = role;
    this.source = source;
    this.sourceReference = reference;
    this.id = new UserRoleId(user.getId(), role.getId());
  }
}
