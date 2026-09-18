package io.github.isharafe.authorization.spi;

import io.github.isharafe.authorization.domain.IdentityChangeEvent;
import io.github.isharafe.authorization.domain.IdentityChangeProcessingResult;

public interface IdentityChangeEventProcessor {
  IdentityChangeProcessingResult process(IdentityChangeEvent event);
}
