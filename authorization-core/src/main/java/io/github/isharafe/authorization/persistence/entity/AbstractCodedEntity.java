package io.github.isharafe.authorization.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@MappedSuperclass
public abstract class AbstractCodedEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Setter
  @Column(nullable = false, unique = true, length = 100)
  private String code;

  @Setter
  @Column(nullable = false)
  private boolean enabled = true;

  @Version private long version;
}
