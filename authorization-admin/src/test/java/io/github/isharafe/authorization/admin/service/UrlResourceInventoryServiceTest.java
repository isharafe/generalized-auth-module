package io.github.isharafe.authorization.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.isharafe.authorization.admin.dto.AdminDtos;
import io.github.isharafe.authorization.domain.AccessMode;
import io.github.isharafe.authorization.domain.ResourceType;
import io.github.isharafe.authorization.persistence.entity.ResourceRuleEntity;
import io.github.isharafe.authorization.persistence.repository.ResourceRuleRepository;
import io.github.isharafe.authorization.security.DefaultPermissionMatcher;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;

class UrlResourceInventoryServiceTest {
  @Test
  void discoversMappingsAndReportsTheEffectiveRuleAndDefaultDeny() throws Exception {
    UrlResourceInventoryService service =
        service(
            List.of(
                rule("API", "*:/api/**", AccessMode.AUTHENTICATED, 0),
                rule("EMPLOYEE", "GET:/api/employees/{id}", AccessMode.AUTHORIZED, 5)));

    AdminDtos.Page<AdminDtos.UrlResourceInventoryItem> page =
        service.find(
            "",
            null,
            null,
            null,
            PageRequest.of(0, 20, Sort.by("path").ascending()));

    assertThat(page.totalElements()).isEqualTo(2);
    assertThat(page.content())
        .anySatisfy(
            item -> {
              assertThat(item.pattern()).isEqualTo("GET:/api/employees/{id}");
              assertThat(item.coverageStatus()).isEqualTo(AdminDtos.UrlCoverageStatus.MATCHED);
              assertThat(item.matchedRule()).isEqualTo("EMPLOYEE");
              assertThat(item.accessMode()).isEqualTo(AccessMode.AUTHORIZED);
              assertThat(item.origins())
                  .containsExactly(AdminDtos.UrlResourceOrigin.AUTHORIZATION_FRAMEWORK);
            })
        .anySatisfy(
            item -> {
              assertThat(item.pattern()).isEqualTo("POST:/uncovered");
              assertThat(item.coverageStatus()).isEqualTo(AdminDtos.UrlCoverageStatus.UNMATCHED);
              assertThat(item.reason()).isEqualTo(io.github.isharafe.authorization.domain.AuthorizationReason.NO_MATCHING_RULE);
            });
  }

  @Test
  void filtersAndPaginatesTheInventory() throws Exception {
    UrlResourceInventoryService service = service(List.of());

    AdminDtos.Page<AdminDtos.UrlResourceInventoryItem> page =
        service.find(
            "employee",
            AdminDtos.UrlCoverageStatus.UNMATCHED,
            null,
            AdminDtos.UrlResourceOrigin.AUTHORIZATION_FRAMEWORK,
            PageRequest.of(0, 1, Sort.by("method").ascending()));

    assertThat(page.totalElements()).isOne();
    assertThat(page.totalPages()).isOne();
    assertThat(page.content()).extracting(AdminDtos.UrlResourceInventoryItem::method).containsExactly("GET");
  }

  @Test
  void reportsConflictingRulesAsIndeterminate() throws Exception {
    UrlResourceInventoryService service =
        service(
            List.of(
                rule("ALLOW", "GET:/api/employees/{id}", AccessMode.PERMIT_ALL, 0),
                rule("DENY", "GET:/api/employees/{id}", AccessMode.DENY_ALL, 0)));

    AdminDtos.UrlResourceInventoryItem employee =
        service
            .find("employee", null, null, null, PageRequest.of(0, 20))
            .content()
            .getFirst();

    assertThat(employee.coverageStatus())
        .isEqualTo(AdminDtos.UrlCoverageStatus.INDETERMINATE);
    assertThat(employee.reason())
        .isEqualTo(io.github.isharafe.authorization.domain.AuthorizationReason.CONFLICTING_RULES);
    assertThat(employee.matchedRule()).isNull();
  }

  @SuppressWarnings("unchecked")
  private UrlResourceInventoryService service(List<ResourceRuleEntity> enabledRules)
      throws Exception {
    RequestMappingInfoHandlerMapping mapping = mock(RequestMappingInfoHandlerMapping.class);
    Method employee = TestController.class.getDeclaredMethod("employee");
    Method uncovered = TestController.class.getDeclaredMethod("uncovered");
    when(mapping.getHandlerMethods())
        .thenReturn(
            Map.of(
                RequestMappingInfo.paths("/api/employees/{id}").methods(RequestMethod.GET).build(),
                new HandlerMethod(new TestController(), employee),
                RequestMappingInfo.paths("/uncovered").methods(RequestMethod.POST).build(),
                new HandlerMethod(new TestController(), uncovered)));
    ObjectProvider<RequestMappingInfoHandlerMapping> mappings = mock(ObjectProvider.class);
    when(mappings.orderedStream()).thenReturn(Stream.of(mapping));
    ResourceRuleRepository repository = mock(ResourceRuleRepository.class);
    when(repository.findByEnabledTrue()).thenReturn(enabledRules);
    return new UrlResourceInventoryService(mappings, repository, new DefaultPermissionMatcher());
  }

  private ResourceRuleEntity rule(
      String code, String pattern, AccessMode accessMode, int priority) {
    ResourceRuleEntity entity = new ResourceRuleEntity();
    entity.setCode(code);
    entity.setResourceType(ResourceType.URL);
    entity.setPattern(pattern);
    entity.setAccessMode(accessMode);
    entity.setPriority(priority);
    entity.setEnabled(true);
    return entity;
  }

  private static final class TestController {
    @GetMapping("/api/employees/{id}")
    void employee() {}

    void uncovered() {}
  }
}
