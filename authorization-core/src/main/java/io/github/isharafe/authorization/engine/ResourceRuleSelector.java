package io.github.isharafe.authorization.engine;

import io.github.isharafe.authorization.domain.ProtectedResource;
import io.github.isharafe.authorization.domain.ResourceRule;
import io.github.isharafe.authorization.spi.PermissionMatcher;
import java.util.Comparator;
import java.util.List;

public final class ResourceRuleSelector {
  private ResourceRuleSelector() {}

  public static ResourceRule select(
      List<ResourceRule> rules, ProtectedResource resource, PermissionMatcher matcher) {
    List<ResourceRule> matches =
        rules.stream()
            .filter(rule -> matcher.matches(rule, resource))
            .sorted(comparator(matcher))
            .toList();
    if (matches.isEmpty()) return null;
    ResourceRule best = matches.getFirst();
    for (ResourceRule candidate : matches.subList(1, matches.size())) {
      if (!sameRank(best, candidate, matcher)) break;
      if (best.accessMode() != candidate.accessMode()) {
        throw new AuthorizationConfigurationException(
            "Conflicting resource rules: " + best.code() + ", " + candidate.code());
      }
    }
    return best;
  }

  private static Comparator<ResourceRule> comparator(PermissionMatcher matcher) {
    return (left, right) -> {
      int comparison = matcher.compareSpecificity(left, right);
      if (comparison != 0) return comparison;
      comparison = Integer.compare(right.priority(), left.priority());
      return comparison != 0 ? comparison : left.code().compareTo(right.code());
    };
  }

  private static boolean sameRank(
      ResourceRule left, ResourceRule right, PermissionMatcher matcher) {
    return matcher.compareSpecificity(left, right) == 0 && left.priority() == right.priority();
  }
}
