package io.github.isharafe.authorization.admin.api;

import io.github.isharafe.authorization.admin.dto.AdminDtos;
import io.github.isharafe.authorization.admin.service.UrlResourceInventoryService;
import io.github.isharafe.authorization.domain.AccessMode;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    if (page < 0) throw AdminApiException.validation("page must be zero or greater");
    if (size < 1 || size > 100)
      throw AdminApiException.validation("size must be between 1 and 100");
    String[] parts = sort == null ? new String[0] : sort.split(",", 2);
    String property = parts.length > 0 && SORTS.contains(parts[0]) ? parts[0] : "path";
    Sort.Direction direction =
        parts.length > 1 && "desc".equalsIgnoreCase(parts[1])
            ? Sort.Direction.DESC
            : Sort.Direction.ASC;
    return service.find(
        search,
        coverage,
        accessMode,
        origin,
        enforcementSource,
        PageRequest.of(page, size, Sort.by(direction, property)));
  }
}
