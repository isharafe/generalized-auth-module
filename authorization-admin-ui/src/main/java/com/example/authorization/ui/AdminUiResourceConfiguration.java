package com.example.authorization.ui;

import com.example.authorization.config.AuthorizationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@RequiredArgsConstructor
public class AdminUiResourceConfiguration implements WebMvcConfigurer {
  private final AuthorizationProperties properties;

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    String basePath = properties.getAdmin().getUi().getBasePath();
    if (basePath == null || basePath.isBlank() || !basePath.startsWith("/"))
      throw new IllegalStateException("authorization.admin.ui.base-path must start with /");
    if (basePath.length() > 1 && basePath.endsWith("/"))
      basePath = basePath.substring(0, basePath.length() - 1);
    registry
        .addResourceHandler(basePath + "/**")
        .addResourceLocations("classpath:/META-INF/resources/authorization-admin/");
  }
}
