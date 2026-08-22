package com.example.authorization.admin.api;

import com.example.authorization.admin.dto.AdminDtos;
import com.example.authorization.config.AuthorizationProperties;
import com.example.authorization.spi.IdentitySynchronizationProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("${authorization.admin.api.base-path:/authorization-admin/api}")
@ConditionalOnProperty(
    prefix = "authorization.admin.api",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AuthorizationCapabilitiesController {
  private final AuthorizationProperties properties;
  private final IdentitySynchronizationProvider synchronization;

  @GetMapping("/capabilities")
  public AdminDtos.Capabilities capabilities() {
    return new AdminDtos.Capabilities(
        properties.getSource().toUpperCase(),
        synchronization.supported(),
        true,
        synchronization.status().provider());
  }
}
