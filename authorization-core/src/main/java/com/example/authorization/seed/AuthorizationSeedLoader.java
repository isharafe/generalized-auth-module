package com.example.authorization.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.util.List;
import org.springframework.core.io.ResourceLoader;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class AuthorizationSeedLoader {
  private final ResourceLoader resources;
  private final ObjectMapper mapper =
      new ObjectMapper(new YAMLFactory())
          .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE);

  public AuthorizationSeedDefinition load(List<String> locations) {
    AuthorizationSeedDefinition merged = new AuthorizationSeedDefinition();
    for (String location : locations) {
      try (var input = resources.getResource(location).getInputStream()) {
        SeedDocument document = mapper.readValue(input, SeedDocument.class);
        if (document.authorization() != null && document.authorization().seed() != null)
          merged.merge(document.authorization().seed());
      } catch (IOException exception) {
        throw new IllegalArgumentException("Cannot load authorization seed " + location, exception);
      }
    }
    return merged;
  }

  private record SeedDocument(AuthorizationNode authorization) {}

  private record AuthorizationNode(AuthorizationSeedDefinition seed) {}
}
