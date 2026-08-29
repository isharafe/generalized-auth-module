package com.example.authorization.spi;

import com.example.authorization.domain.IdentityChangeEvent;
import com.example.authorization.domain.IdentityChangeProcessingResult;

public interface IdentityChangeEventProcessor {
  IdentityChangeProcessingResult process(IdentityChangeEvent event);
}
