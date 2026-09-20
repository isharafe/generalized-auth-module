package io.github.isharafe.authorization.admin.api;

import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

final class AdminPageableFactory {
  private AdminPageableFactory() {}

  static Pageable create(
      int page,
      int size,
      String requestedSort,
      String defaultProperty,
      Set<String> allowedProperties) {
    if (page < 0) throw AdminApiException.validation("page must be zero or greater");
    if (size < 1 || size > 100)
      throw AdminApiException.validation("size must be between 1 and 100");
    String[] parts = requestedSort == null ? new String[0] : requestedSort.split(",", 2);
    String property =
        parts.length > 0 && allowedProperties.contains(parts[0]) ? parts[0] : defaultProperty;
    Sort.Direction direction =
        parts.length > 1 && "desc".equalsIgnoreCase(parts[1])
            ? Sort.Direction.DESC
            : Sort.Direction.ASC;
    return PageRequest.of(page, size, Sort.by(direction, property));
  }
}
