package com.example.authorization.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
}
