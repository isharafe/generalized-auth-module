package io.github.isharafe.authorization.seed;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

@Slf4j
@RequiredArgsConstructor
public final class AuthorizationSeedRunner implements ApplicationRunner {
  private final AuthorizationSeedLoader loader;
  private final List<String> locations;
  private final List<AuthorizationSeedResourceContributor> resourceContributors;
  private final List<AuthorizationSeedContributor> contributors;
  private final AuthorizationSeedInitializationService service;
  private final boolean failOnError;

  @Override
  public void run(@NonNull ApplicationArguments args) {
    try {
      AuthorizationSeedComposer composer = new AuthorizationSeedComposer();
      for (AuthorizationSeedResourceContributor contributor : resourceContributors) {
        if (contributor.seedResources() == null) continue;
        for (AuthorizationSeedResource resource : contributor.seedResources())
          composer.addModule(resource.source(), loader.load(resource));
      }
      composer.addApplication("configured application seed locations", loader.load(locations));
      for (AuthorizationSeedContributor contributor : contributors) {
        AuthorizationSeedBuilder builder = new AuthorizationSeedBuilder();
        contributor.contribute(builder);
        composer.addApplication(contributor.getClass().getName(), builder.build());
      }
      service.apply(composer.build());
    } catch (RuntimeException exception) {
      if (failOnError) throw exception;
      log.warn("Authorization seed processing failed; continuing because fail-on-error=false", exception);
    }
  }
}
