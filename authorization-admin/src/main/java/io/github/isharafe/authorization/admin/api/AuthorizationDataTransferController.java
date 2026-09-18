package io.github.isharafe.authorization.admin.api;

import io.github.isharafe.authorization.admin.dto.AuthorizationDataBundle;
import io.github.isharafe.authorization.admin.dto.AuthorizationDataImportResult;
import io.github.isharafe.authorization.admin.service.AuthorizationDataTransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
public class AuthorizationDataTransferController {
  private final AuthorizationDataTransferService service;

  @PostMapping("/data/export")
  public ResponseEntity<AuthorizationDataBundle> exportData(Authentication authentication) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentDisposition(
        ContentDisposition.attachment().filename("authorization-data.json").build());
    return ResponseEntity.ok().headers(headers).body(service.exportData(authentication));
  }

  @PostMapping("/data/import")
  public AuthorizationDataImportResult importData(
      @Valid @RequestBody AuthorizationDataBundle bundle, Authentication authentication) {
    return service.replaceData(bundle, authentication);
  }
}
