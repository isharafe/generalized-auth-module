package io.github.isharafe.authorization.admin.service;

import io.github.isharafe.authorization.admin.dto.AdminDtos;
import io.github.isharafe.authorization.domain.AccessMode;
import io.github.isharafe.authorization.domain.AuthorizationReason;
import io.github.isharafe.authorization.domain.AuthorizationResult;
import io.github.isharafe.authorization.domain.ProtectedResource;
import io.github.isharafe.authorization.domain.ResourceRule;
import io.github.isharafe.authorization.domain.ResourceType;
import io.github.isharafe.authorization.engine.AuthorizationEngine;
import io.github.isharafe.authorization.persistence.repository.ResourceRuleRepository;
import io.github.isharafe.authorization.security.UrlSecurityPolicy;
import io.github.isharafe.authorization.security.UrlSecurityPolicyDecision;
import io.github.isharafe.authorization.spi.PermissionMatcher;
import io.github.isharafe.authorization.spi.UrlSecurityPolicyContributor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;

@RequiredArgsConstructor
public class UrlResourceInventoryService {
  private static final List<String> AUTHORIZATION_HANDLER_PACKAGES =
      List.of(
          "io.github.isharafe.authorization.admin",
          "io.github.isharafe.authorization.security",
          "io.github.isharafe.authorization.keycloak");
  private static final String SPRING_PACKAGE = "org.springframework";

  private final ObjectProvider<RequestMappingInfoHandlerMapping> handlerMappings;
  private final ResourceRuleRepository rules;
  private final PermissionMatcher matcher;
  private final ObjectProvider<UrlSecurityPolicyContributor> securityPolicyContributors;

  @Transactional(readOnly = true)
  public AdminDtos.Page<AdminDtos.UrlResourceInventoryItem> find(
      String search,
      AdminDtos.UrlCoverageStatus coverage,
      AccessMode accessMode,
      AdminDtos.UrlResourceOrigin origin,
      AdminDtos.UrlEnforcementSource enforcementSource,
      Pageable pageable) {
    List<ResourceRule> enabledRules =
        rules.findByEnabledTrue().stream().map(value -> value.toDomain()).toList();
    Map<String, ResourceRule> rulesByCode =
        enabledRules.stream().collect(Collectors.toMap(ResourceRule::code, Function.identity()));
    AuthorizationEngine snapshotEngine =
        new AuthorizationEngine(
            () -> enabledRules,
            identity -> {
              throw new IllegalStateException("Entitlements are not loaded for route coverage");
            },
            matcher);
    List<UrlSecurityPolicy> securityPolicies = securityPolicies();

    String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
    List<AdminDtos.UrlResourceInventoryItem> items =
        discover().stream()
            .map(value -> evaluate(value, snapshotEngine, rulesByCode, securityPolicies))
            .filter(value -> matchesSearch(value, normalizedSearch))
            .filter(value -> coverage == null || value.coverageStatus() == coverage)
            .filter(value -> accessMode == null || value.accessMode() == accessMode)
            .filter(value -> origin == null || value.origins().contains(origin))
            .filter(
                value ->
                    enforcementSource == null
                        || value.enforcementSource() == enforcementSource)
            .sorted(comparator(pageable))
            .toList();

    int from = Math.min((int) pageable.getOffset(), items.size());
    int to = Math.min(from + pageable.getPageSize(), items.size());
    int totalPages =
        items.isEmpty() ? 0 : (int) Math.ceil((double) items.size() / pageable.getPageSize());
    return new AdminDtos.Page<>(
        items.subList(from, to),
        pageable.getPageNumber(),
        pageable.getPageSize(),
        items.size(),
        totalPages);
  }

  private List<DiscoveredUrl> discover() {
    Map<String, MutableDiscoveredUrl> discovered = new TreeMap<>();
    handlerMappings.orderedStream()
        .forEach(
            mapping ->
                mapping
                    .getHandlerMethods()
                    .forEach(
                        (mappingInfo, handler) ->
                            addMappings(discovered, mappingInfo, handler)));
    return discovered.values().stream().map(MutableDiscoveredUrl::snapshot).toList();
  }

  private void addMappings(
      Map<String, MutableDiscoveredUrl> discovered,
      RequestMappingInfo mapping,
      HandlerMethod handler) {
    Set<String> paths = mapping.getPatternValues();
    if (paths.isEmpty()) paths = Set.of("/");
    Set<RequestMethod> requestMethods = mapping.getMethodsCondition().getMethods();
    Set<String> methods =
        requestMethods.isEmpty()
            ? Set.of("*")
            : requestMethods.stream().map(Enum::name).collect(Collectors.toSet());
    Class<?> beanType = ClassUtils.getUserClass(handler.getBeanType());
    String handlerName = beanType.getName() + "#" + handler.getMethod().getName();
    AdminDtos.UrlResourceOrigin origin = origin(beanType.getPackageName());
    for (String path : paths) {
      String normalizedPath = path == null || path.isBlank() ? "/" : path;
      for (String method : methods) {
        String pattern = method + ":" + normalizedPath;
        discovered
            .computeIfAbsent(pattern, ignored -> new MutableDiscoveredUrl(method, normalizedPath))
            .add(origin, handlerName);
      }
    }
  }

  private AdminDtos.UrlResourceInventoryItem evaluate(
      DiscoveredUrl route,
      AuthorizationEngine engine,
      Map<String, ResourceRule> rulesByCode,
      List<UrlSecurityPolicy> securityPolicies) {
    String pattern = route.method() + ":" + route.path();
    UrlSecurityPolicy securityPolicy = matchingSecurityPolicy(pattern, securityPolicies);
    if (securityPolicy == null) return unknownSecurityPolicy(route, pattern);
    if (securityPolicy.decision() != UrlSecurityPolicyDecision.RESOURCE_RULES)
      return securityPolicy(route, pattern, securityPolicy);

    AuthorizationResult result =
        engine.authorize(new ProtectedResource(ResourceType.URL, pattern), null);
    ResourceRule matched =
        result.matchedRuleCode() == null ? null : rulesByCode.get(result.matchedRuleCode());
    AdminDtos.UrlCoverageStatus status =
        result.reason() == AuthorizationReason.NO_MATCHING_RULE
            ? AdminDtos.UrlCoverageStatus.UNMATCHED
            : result.reason() == AuthorizationReason.CONFLICTING_RULES
                || result.reason() == AuthorizationReason.PROVIDER_UNAVAILABLE
                ? AdminDtos.UrlCoverageStatus.INDETERMINATE
                : AdminDtos.UrlCoverageStatus.MATCHED;
    return new AdminDtos.UrlResourceInventoryItem(
        ResourceType.URL,
        route.method(),
        route.path(),
        pattern,
        status,
        matched == null ? null : matched.accessMode(),
        matched == null ? null : matched.code(),
        matched == null ? null : matched.priority(),
        result.reason(),
        AdminDtos.UrlEnforcementSource.RESOURCE_RULE,
        securityPolicy.code(),
        securityPolicy.decision(),
        route.origins(),
        route.handlers());
  }

  private UrlSecurityPolicy matchingSecurityPolicy(
      String pattern, List<UrlSecurityPolicy> securityPolicies) {
    ProtectedResource resource = new ProtectedResource(ResourceType.URL, pattern);
    return securityPolicies.stream()
        .filter(
            policy ->
                matcher.matches(
                    new ResourceRule(
                        policy.code(),
                        ResourceType.URL,
                        policy.pattern(),
                        AccessMode.DENY_ALL,
                        0,
                        true),
                    resource))
        .findFirst()
        .orElse(null);
  }

  private List<UrlSecurityPolicy> securityPolicies() {
    List<UrlSecurityPolicy> policies = new ArrayList<>();
    securityPolicyContributors.orderedStream()
        .map(UrlSecurityPolicyContributor::urlSecurityPolicies)
        .filter(java.util.Objects::nonNull)
        .flatMap(Collection::stream)
        .forEach(
            policy -> {
              matcher.validate(ResourceType.URL, policy.pattern());
              policies.add(policy);
            });
    return policies.stream()
        .sorted(
            Comparator.comparingInt(UrlSecurityPolicy::chainOrder)
                .thenComparingInt(UrlSecurityPolicy::matcherOrder)
                .thenComparing(UrlSecurityPolicy::code))
        .toList();
  }

  private AdminDtos.UrlResourceInventoryItem securityPolicy(
      DiscoveredUrl route, String pattern, UrlSecurityPolicy policy) {
    AccessMode accessMode =
        switch (policy.decision()) {
          case PERMIT_ALL -> AccessMode.PERMIT_ALL;
          case AUTHENTICATED -> AccessMode.AUTHENTICATED;
          case AUTHORIZED -> AccessMode.AUTHORIZED;
          case DENY_ALL -> AccessMode.DENY_ALL;
          case RESOURCE_RULES -> throw new IllegalArgumentException("Resource-rule policy delegated");
        };
    return new AdminDtos.UrlResourceInventoryItem(
        ResourceType.URL,
        route.method(),
        route.path(),
        pattern,
        AdminDtos.UrlCoverageStatus.MATCHED,
        accessMode,
        null,
        null,
        null,
        AdminDtos.UrlEnforcementSource.SECURITY_FILTER_CHAIN,
        policy.code(),
        policy.decision(),
        route.origins(),
        route.handlers());
  }

  private AdminDtos.UrlResourceInventoryItem unknownSecurityPolicy(
      DiscoveredUrl route, String pattern) {
    return new AdminDtos.UrlResourceInventoryItem(
        ResourceType.URL,
        route.method(),
        route.path(),
        pattern,
        AdminDtos.UrlCoverageStatus.INDETERMINATE,
        null,
        null,
        null,
        null,
        AdminDtos.UrlEnforcementSource.UNKNOWN,
        null,
        null,
        route.origins(),
        route.handlers());
  }

  private boolean matchesSearch(AdminDtos.UrlResourceInventoryItem value, String search) {
    if (search.isEmpty()) return true;
    return value.pattern().toLowerCase(Locale.ROOT).contains(search)
        || (value.matchedRule() != null
            && value.matchedRule().toLowerCase(Locale.ROOT).contains(search))
        || (value.matchedSecurityPolicy() != null
            && value.matchedSecurityPolicy().toLowerCase(Locale.ROOT).contains(search))
        || value.handlers().stream().anyMatch(item -> item.toLowerCase(Locale.ROOT).contains(search));
  }

  private Comparator<AdminDtos.UrlResourceInventoryItem> comparator(Pageable pageable) {
    org.springframework.data.domain.Sort.Order order =
        pageable
            .getSort()
            .stream()
            .findFirst()
            .orElse(org.springframework.data.domain.Sort.Order.asc("path"));
    Comparator<AdminDtos.UrlResourceInventoryItem> comparator =
        switch (order.getProperty()) {
          case "method" -> Comparator.comparing(AdminDtos.UrlResourceInventoryItem::method);
          case "coverageStatus" -> Comparator.comparing(value -> value.coverageStatus().name());
          case "accessMode" ->
              Comparator.comparing(
                  value -> value.accessMode() == null ? "" : value.accessMode().name());
          case "origin" ->
              Comparator.comparing(
                  value -> value.origins().stream().map(Enum::name).sorted().findFirst().orElse(""));
          case "enforcementSource" ->
              Comparator.comparing(value -> value.enforcementSource().name());
          default -> Comparator.comparing(AdminDtos.UrlResourceInventoryItem::path);
        };
    if (order.isDescending()) comparator = comparator.reversed();
    return comparator
        .thenComparing(AdminDtos.UrlResourceInventoryItem::path)
        .thenComparing(AdminDtos.UrlResourceInventoryItem::method);
  }

  private AdminDtos.UrlResourceOrigin origin(String packageName) {
    if (AUTHORIZATION_HANDLER_PACKAGES.stream()
        .anyMatch(value -> packageName.equals(value) || packageName.startsWith(value + ".")))
      return AdminDtos.UrlResourceOrigin.AUTHORIZATION_FRAMEWORK;
    if (packageName.startsWith(SPRING_PACKAGE))
      return AdminDtos.UrlResourceOrigin.SPRING_INFRASTRUCTURE;
    return AdminDtos.UrlResourceOrigin.APPLICATION;
  }

  private record DiscoveredUrl(
      String method,
      String path,
      Set<AdminDtos.UrlResourceOrigin> origins,
      Set<String> handlers) {}

  private static final class MutableDiscoveredUrl {
    private final String method;
    private final String path;
    private final Set<AdminDtos.UrlResourceOrigin> origins = new LinkedHashSet<>();
    private final Set<String> handlers = new LinkedHashSet<>();

    private MutableDiscoveredUrl(String method, String path) {
      this.method = method;
      this.path = path;
    }

    private void add(AdminDtos.UrlResourceOrigin origin, String handler) {
      origins.add(origin);
      handlers.add(handler);
    }

    private DiscoveredUrl snapshot() {
      return new DiscoveredUrl(method, path, Set.copyOf(origins), Set.copyOf(handlers));
    }
  }
}
