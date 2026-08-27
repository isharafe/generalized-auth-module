package com.example.authorization.admin.ui;

import com.example.authorization.admin.config.AuthorizationAdminProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@RequiredArgsConstructor
public class AdminUiResourceConfiguration implements WebMvcConfigurer {
  private final AuthorizationAdminProperties properties;

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    String basePath = properties.getUi().getBasePath();
    if (basePath == null || basePath.isBlank() || !basePath.startsWith("/"))
      throw new IllegalStateException("authorization.admin.ui.base-path must start with /");
    if (basePath.length() > 1 && basePath.endsWith("/"))
      basePath = basePath.substring(0, basePath.length() - 1);
    registry
        .addResourceHandler(basePath + "/**")
        .addResourceLocations("classpath:/META-INF/resources/authorization-admin/");
  }
}
