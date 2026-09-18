package io.github.isharafe.authorization.seed;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

@Slf4j
@RequiredArgsConstructor
public final class AuthorizationSeedRunner implements ApplicationRunner {
  private final AuthorizationSeedLoader loader;
  private final List<String> locations;
  private final List<AuthorizationSeedContributor> contributors;
  private final AuthorizationSeedInitializationService service;
  private final boolean failOnError;

  @Override
  public void run(ApplicationArguments args) {
    try {
      AuthorizationSeedDefinition seed = loader.load(locations);
      for (AuthorizationSeedContributor contributor : contributors) {
        AuthorizationSeedBuilder builder = new AuthorizationSeedBuilder();
        contributor.contribute(builder);
        seed.merge(builder.build());
      }
      service.apply(seed);
    } catch (RuntimeException exception) {
      if (failOnError) throw exception;
      log.warn("Authorization seed processing failed; continuing because fail-on-error=false", exception);
    }
  }
}
