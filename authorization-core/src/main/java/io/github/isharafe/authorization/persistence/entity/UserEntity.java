package io.github.isharafe.authorization.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
    name = "AUTH_USER",
    uniqueConstraints = @UniqueConstraint(columnNames = {"EXTERNAL_ISSUER", "EXTERNAL_SUBJECT"}))
public class UserEntity {
  @Setter(AccessLevel.NONE)
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "EXTERNAL_ISSUER", nullable = false)
  private String issuer;

  @Column(name = "EXTERNAL_SUBJECT", nullable = false)
  private String subject;

  private String username;
  private String email;

  @Column(name = "FIRST_NAME")
  private String firstName;

  @Column(name = "LAST_NAME")
  private String lastName;

  private boolean enabled = true;

  @Column(name = "EXTERNAL_DIRECTORY_ID")
  private String externalDirectoryId;

  @Column(name = "LAST_IDENTITY_SYNC_AT")
  private Instant lastIdentitySyncAt;

  @Column(name = "IDENTITY_SYNC_STATUS")
  private String identitySyncStatus;

  @Setter(AccessLevel.NONE)
  @Column(name = "ENTITLEMENT_VERSION")
  private long entitlementVersion;

  @Setter(AccessLevel.NONE)
  @Version private long version;

  @Setter(AccessLevel.NONE)
  @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
  private Set<UserRoleEntity> roles = new LinkedHashSet<>();

  @Setter(AccessLevel.NONE)
  @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
  private Set<UserPermissionGroupEntity> permissionGroups = new LinkedHashSet<>();

  public void incrementEntitlementVersion() {
    entitlementVersion++;
  }
}
