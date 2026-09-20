package io.github.isharafe.authorization.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ResourceLoader;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class AuthorizationSeedLoader {
  private static final Pattern VARIABLE = Pattern.compile("\\{\\{([A-Za-z0-9_.-]+)}}");
  private final ResourceLoader resources;
  private final ObjectMapper mapper =
      new ObjectMapper(new YAMLFactory())
          .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE);

  public AuthorizationSeedDefinition load(List<String> locations) {
    AuthorizationSeedComposer composer = new AuthorizationSeedComposer();
    for (String location : locations) {
      composer.addApplication(location, load(location, Map.of()));
    }
    return composer.build();
  }

  public AuthorizationSeedDefinition load(AuthorizationSeedResource resource) {
    return load(resource.location(), resource.variables());
  }

  private AuthorizationSeedDefinition load(String location, Map<String, String> variables) {
    try (InputStream input = resources.getResource(location).getInputStream()) {
      JsonNode root = resolve(mapper.readTree(input), variables, location);
      SeedDocument document = mapper.treeToValue(root, SeedDocument.class);
      if (document == null
          || document.authorization() == null
          || document.authorization().seed() == null)
        return new AuthorizationSeedDefinition();
      return document.authorization().seed();
    } catch (IOException exception) {
      throw new IllegalArgumentException("Cannot load authorization seed " + location, exception);
    }
  }

  private JsonNode resolve(JsonNode node, Map<String, String> variables, String location) {
    if (node == null) return mapper.createObjectNode();
    if (node.isObject()) {
      ObjectNode object = (ObjectNode) node;
      object
          .properties()
          .forEach(entry -> object.set(entry.getKey(), resolve(entry.getValue(), variables, location)));
      return object;
    }
    if (node.isArray()) {
      ArrayNode array = (ArrayNode) node;
      for (int index = 0; index < array.size(); index++)
        array.set(index, resolve(array.get(index), variables, location));
      return array;
    }
    if (!node.isTextual()) return node;
    Matcher matcher = VARIABLE.matcher(node.textValue());
    StringBuilder resolved = new StringBuilder();
    while (matcher.find()) {
      String value = variables.get(matcher.group(1));
      if (value == null)
        throw new IllegalArgumentException(
            "Unknown seed variable {{" + matcher.group(1) + "}} in " + location);
      matcher.appendReplacement(resolved, Matcher.quoteReplacement(value));
    }
    matcher.appendTail(resolved);
    return TextNode.valueOf(resolved.toString());
  }

  private record SeedDocument(AuthorizationNode authorization) {}

  private record AuthorizationNode(AuthorizationSeedDefinition seed) {}
}
