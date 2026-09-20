package io.github.isharafe.authorization.admin.api;

import static io.github.isharafe.authorization.admin.api.AdminPageableFactory.create;

import io.github.isharafe.authorization.admin.dto.AdminDtos;
import io.github.isharafe.authorization.admin.service.UrlResourceInventoryService;
import io.github.isharafe.authorization.domain.AccessMode;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("${authorization.admin.api.base-path:/authorization-admin/api}")
@ConditionalOnProperty(
    prefix = "authorization",
    name = {"enabled", "admin.api.enabled"},
    havingValue = "true",
    matchIfMissing = true)
public class UrlResourceInventoryController {
  private static final Set<String> SORTS =
      Set.of("path", "method", "coverageStatus", "accessMode", "origin", "enforcementSource");
  private final UrlResourceInventoryService service;

  @GetMapping("/resource-inventory/urls")
  public AdminDtos.Page<AdminDtos.UrlResourceInventoryItem> urls(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "path,asc") String sort,
      @RequestParam(defaultValue = "") String search,
      @RequestParam(required = false) AdminDtos.UrlCoverageStatus coverage,
      @RequestParam(required = false) AccessMode accessMode,
      @RequestParam(required = false) AdminDtos.UrlResourceOrigin origin,
      @RequestParam(required = false) AdminDtos.UrlEnforcementSource enforcementSource) {
    return service.find(
        search,
        coverage,
        accessMode,
        origin,
        enforcementSource,
        create(page, size, sort, "path", SORTS));
  }
}
