package com.example.authorization.seed;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

class AuthorizationSeedRunnerTest {
  @Test
  void continuesWhenFailOnErrorIsDisabled() {
    AuthorizationSeedLoader loader = mock(AuthorizationSeedLoader.class);
    when(loader.load(anyList())).thenThrow(new IllegalArgumentException("bad seed"));
    AuthorizationSeedRunner runner =
        new AuthorizationSeedRunner(
            loader,
            List.of("seed.yml"),
            List.of(),
            mock(AuthorizationSeedInitializationService.class),
            false);

    assertThatCode(() -> runner.run(mock(ApplicationArguments.class))).doesNotThrowAnyException();
  }

  @Test
  void failsStartupWhenFailOnErrorIsEnabled() {
    AuthorizationSeedLoader loader = mock(AuthorizationSeedLoader.class);
    when(loader.load(anyList())).thenThrow(new IllegalArgumentException("bad seed"));
    AuthorizationSeedRunner runner =
        new AuthorizationSeedRunner(
            loader,
            List.of("seed.yml"),
            List.of(),
            mock(AuthorizationSeedInitializationService.class),
            true);

    assertThatThrownBy(() -> runner.run(mock(ApplicationArguments.class)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("bad seed");
  }
}
