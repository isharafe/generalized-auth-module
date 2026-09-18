package io.github.isharafe.authorization.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("authorization")
public class AuthorizationProperties {
  private boolean enabled = true;
  private String source = "database";
  private String defaultDecision = "DENY";
  private final Database database = new Database();
  private final Seed seed = new Seed();
  private final Cache cache = new Cache();
  private final IdentityEvents identityEvents = new IdentityEvents();
  private final DistributedInvalidation distributedInvalidation = new DistributedInvalidation();
  private final Security security = new Security();
  private final UiApi uiApi = new UiApi();

  @Getter
  public static class Database {
    private final Migration migration = new Migration();
  }

  @Getter
  @Setter
  public static class Migration {
    private boolean enabled = true;
    private String location = "classpath:db/authorization/migration";
    private String historyTable = "authorization_flyway_schema_history";
  }

  @Getter
  @Setter
  public static class Seed {
    private boolean enabled = true;
    private boolean failOnError = true;
    private List<String> locations = new ArrayList<>();

    public void setLocations(List<String> value) {
      locations = value == null ? new ArrayList<>() : value;
    }
  }

  @Getter
  public static class Cache {
    private final CacheRegion resourceRules = new CacheRegion(Duration.ofMinutes(30));
    private final CacheRegion entitlements = new CacheRegion(Duration.ofMinutes(5));
  }

  @Getter
  @Setter
  public static class CacheRegion {
    private boolean enabled = true;
    private Duration ttl;

    public CacheRegion() {}

    private CacheRegion(Duration ttl) {
      this.ttl = ttl;
    }
  }

  @Getter
  @Setter
  public static class IdentityEvents {
    private Duration processingTimeout = Duration.ofMinutes(5);
  }

  @Getter
  @Setter
  public static class DistributedInvalidation {
    private boolean enabled;
    private Duration pollInterval = Duration.ofSeconds(1);
    private Duration retention = Duration.ofHours(24);
    private int batchSize = 500;
    private String instanceId = UUID.randomUUID().toString();
  }

  @Getter
  @Setter
  public static class UiApi {
    private boolean enabled = true;
    private String endpoint = "/authorization/ui/permissions";
  }

  @Getter
  public static class Security {
    private final CookieOauth2 cookieOauth2 = new CookieOauth2();
  }

  @Getter
  @Setter
  public static class CookieOauth2 {
    private boolean enabled;
    private String registrationId;
    private String loginSuccessUri = "/";
    private String csrfEndpoint = "/authorization/security/csrf";
    private String refreshEndpoint = "/authorization/security/token/refresh";
    private String logoutEndpoint = "/authorization/security/logout";
    private final TokenCookie accessTokenCookie =
        new TokenCookie("AUTHORIZATION_ACCESS_TOKEN", "/");
    private final TokenCookie refreshTokenCookie =
        new TokenCookie("AUTHORIZATION_REFRESH_TOKEN", "/authorization/security");
    private final TokenCookie idTokenCookie =
        new TokenCookie("AUTHORIZATION_ID_TOKEN", "/authorization/security/logout");
    private final CsrfCookie csrf = new CsrfCookie();
    private final Logout logout = new Logout();
  }

  @Getter
  @Setter
  public static class TokenCookie {
    private String name;
    private String path;
    private boolean secure = true;
    private String sameSite = "Lax";

    public TokenCookie() {}

    private TokenCookie(String name, String path) {
      this.name = name;
      this.path = path;
    }
  }

  @Getter
  @Setter
  public static class CsrfCookie {
    private String name = "XSRF-TOKEN";
    private String headerName = "X-XSRF-TOKEN";
    private boolean secure = true;
    private String sameSite = "Lax";
  }

  @Getter
  @Setter
  public static class Logout {
    private LogoutMode mode = LogoutMode.LOCAL;
    private boolean revokeRefreshToken = true;
    private String postLogoutRedirectUri = "{baseUrl}/";
  }

  public enum LogoutMode {
    LOCAL,
    OIDC
  }
}
