package com.example.authorization.admin.api;

import com.example.authorization.admin.dto.AdminDtos;
import com.example.authorization.config.AuthorizationProperties;
import com.example.authorization.spi.IdentitySynchronizationProvider;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("${authorization.admin.api.base-path:/authorization-admin/api}")
@ConditionalOnProperty(
    prefix = "authorization",
    name = {"enabled", "admin.api.enabled"},
    havingValue = "true",
    matchIfMissing = true)
public class AuthorizationCapabilitiesController {
  private final AuthorizationProperties properties;
  private final IdentitySynchronizationProvider synchronization;

  @GetMapping("/capabilities")
  public AdminDtos.Capabilities capabilities() {
    return new AdminDtos.Capabilities(
        properties.getSource().toUpperCase(Locale.ROOT),
        synchronization.supported(),
        true,
        synchronization.status().provider());
  }
}
