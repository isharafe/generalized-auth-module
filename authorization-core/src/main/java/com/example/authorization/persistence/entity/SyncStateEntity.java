package com.example.authorization.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "AUTH_SYNC_STATE")
public class SyncStateEntity {
  @Id
  @Column(name = "SYNC_KEY")
  private String key;

  private String status;

  @Column(name = "UPDATED_AT")
  private Instant updatedAt;

  @Setter(AccessLevel.NONE)
  @Version private long version;
}
