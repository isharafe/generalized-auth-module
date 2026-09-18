package io.github.isharafe.authorization.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
    name = "AUTH_EXTERNAL_AUTHORITY_MAPPING",
    uniqueConstraints =
        @UniqueConstraint(
            columnNames = {
              "SOURCE_SYSTEM",
              "AUTHORITY_TYPE",
              "AUTHORITY_VALUE",
              "TARGET_TYPE",
              "TARGET_CODE"
            }))
public class ExternalAuthorityMappingEntity {
  @Setter(AccessLevel.NONE)
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "SOURCE_SYSTEM", nullable = false)
  private String sourceSystem;

  @Column(name = "AUTHORITY_TYPE", nullable = false)
  private String authorityType;

  @Column(name = "AUTHORITY_VALUE", nullable = false)
  private String authorityValue;

  @Column(name = "TARGET_TYPE", nullable = false)
  private String targetType;

  @Column(name = "TARGET_CODE", nullable = false)
  private String targetCode;

  private boolean enabled = true;
  @Setter(AccessLevel.NONE)
  @Version private long version;
}
