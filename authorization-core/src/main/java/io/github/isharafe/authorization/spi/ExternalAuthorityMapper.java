package io.github.isharafe.authorization.spi;

import java.util.Set;

public interface ExternalAuthorityMapper {
  Set<String> map(String sourceSystem, String authorityType, String authorityValue);
}
