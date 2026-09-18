package io.github.isharafe.authorization.security;

import io.github.isharafe.authorization.config.AuthorizationProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

public final class OAuth2RefreshTokenRevokingLogoutHandler implements LogoutHandler {
  private static final Logger log =
      LoggerFactory.getLogger(OAuth2RefreshTokenRevokingLogoutHandler.class);

  private final AuthorizationProperties.CookieOauth2 properties;
  private final ClientRegistrationRepository registrations;
  private final AuthorizationTokenCookies cookies;
  private final RestClient restClient;

  public OAuth2RefreshTokenRevokingLogoutHandler(
      AuthorizationProperties properties,
      ClientRegistrationRepository registrations,
      AuthorizationTokenCookies cookies) {
    this(properties, registrations, cookies, RestClient.create());
  }

  OAuth2RefreshTokenRevokingLogoutHandler(
      AuthorizationProperties properties,
      ClientRegistrationRepository registrations,
      AuthorizationTokenCookies cookies,
      RestClient restClient) {
    this.properties = properties.getSecurity().getCookieOauth2();
    this.registrations = registrations;
    this.cookies = cookies;
    this.restClient = restClient;
  }

  @Override
  public void logout(
      HttpServletRequest request,
      HttpServletResponse response,
      Authentication authentication) {
    if (!properties.getLogout().isRevokeRefreshToken()) return;
    String token = cookies.read(request, properties.getRefreshTokenCookie());
    if (token == null) return;
    ClientRegistration registration =
        registrations.findByRegistrationId(properties.getRegistrationId());
    Object endpoint =
        registration == null
            ? null
            : registration
                .getProviderDetails()
                .getConfigurationMetadata()
                .get("revocation_endpoint");
    if (!(endpoint instanceof String uri) || uri.isBlank()) {
      log.debug("OIDC provider does not advertise a token revocation endpoint");
      return;
    }

    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("token", token);
    body.add("token_type_hint", "refresh_token");
    body.add("client_id", registration.getClientId());
    RestClient.RequestBodySpec call =
        restClient.post().uri(uri).contentType(MediaType.APPLICATION_FORM_URLENCODED);
    if (ClientAuthenticationMethod.CLIENT_SECRET_BASIC.equals(
        registration.getClientAuthenticationMethod())) {
      call.headers(
          headers ->
              headers.setBasicAuth(registration.getClientId(), registration.getClientSecret()));
    } else if (ClientAuthenticationMethod.CLIENT_SECRET_POST.equals(
        registration.getClientAuthenticationMethod())) {
      body.add("client_secret", registration.getClientSecret());
    }
    try {
      call.body(body).retrieve().toBodilessEntity();
    } catch (RuntimeException failure) {
      log.warn("Refresh-token revocation failed; local logout will still complete");
    }
  }
}
