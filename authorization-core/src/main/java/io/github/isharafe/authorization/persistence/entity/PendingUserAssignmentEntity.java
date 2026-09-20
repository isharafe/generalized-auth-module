package io.github.isharafe.authorization.persistence.entity;

import io.github.isharafe.authorization.domain.AssignmentSource;
import io.github.isharafe.authorization.domain.AssignmentTargetType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
    name = "AUTH_PENDING_USER_ASSIGNMENT",
    uniqueConstraints =
        @UniqueConstraint(
            columnNames = {
              "EXTERNAL_ISSUER",
              "EXTERNAL_SUBJECT",
              "TARGET_TYPE",
              "TARGET_CODE",
              "ASSIGNMENT_SOURCE"
            }))
public class PendingUserAssignmentEntity {
  @Setter(AccessLevel.NONE)
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "EXTERNAL_ISSUER", nullable = false)
  private String issuer;

  @Column(name = "EXTERNAL_SUBJECT", nullable = false)
  private String subject;

  @Enumerated(EnumType.STRING)
  @Column(name = "TARGET_TYPE", nullable = false)
  private AssignmentTargetType targetType;

  @Column(name = "TARGET_CODE", nullable = false)
  private String targetCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "ASSIGNMENT_SOURCE", nullable = false)
  private AssignmentSource source;
}
