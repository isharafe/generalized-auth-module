package com.example.authorization.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;

@Getter
@Entity
@Table(name = "AUTH_SEED_HISTORY")
public class SeedHistoryEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String source;

  @Column(nullable = false, length = 64)
  private String checksum;

  @Column(nullable = false)
  private String status;

  @Column(name = "APPLIED_AT", nullable = false)
  private Instant appliedAt;

  public static SeedHistoryEntity applied(String source, String checksum) {
    SeedHistoryEntity value = new SeedHistoryEntity();
    value.source = source;
    value.checksum = checksum;
    value.status = "APPLIED";
    value.appliedAt = Instant.now();
    return value;
  }
}
