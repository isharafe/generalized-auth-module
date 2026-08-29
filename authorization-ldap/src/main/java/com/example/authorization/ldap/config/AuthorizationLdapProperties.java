package com.example.authorization.ldap.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("authorization.ldap")
public class AuthorizationLdapProperties {
  private static final Pattern ATTRIBUTE_NAME =
      Pattern.compile("[A-Za-z][A-Za-z0-9._;-]*");

  private boolean enabled = true;
  private List<String> urls = new ArrayList<>();
  private String baseDn;
  private String bindDn;
  private String bindPassword;
  private String issuer;
  private Duration connectTimeout = Duration.ofSeconds(5);
  private Duration readTimeout = Duration.ofSeconds(15);
  private String referral = "ignore";
  private final User user = new User();
  private final Group group = new Group();
  private final Sync sync = new Sync();

  public void setUrls(List<String> value) {
    urls = value == null ? new ArrayList<>() : new ArrayList<>(value);
  }

  public String resolvedIssuer() {
    if (issuer != null && !issuer.isBlank()) return issuer;
    return normalizedUrls().getFirst() + "/" + baseDn.trim();
  }

  public List<String> normalizedUrls() {
    return urls.stream().map(String::trim).map(this::stripTrailingSlash).toList();
  }

  public void validate() {
    if (urls == null || urls.isEmpty())
      throw new IllegalStateException("authorization.ldap.urls must contain at least one LDAP URL");
    for (String url : normalizedUrls()) {
      if (!(url.startsWith("ldap://") || url.startsWith("ldaps://")))
        throw new IllegalStateException(
            "authorization.ldap.urls entries must use ldap:// or ldaps://");
    }
    required(baseDn, "authorization.ldap.base-dn");
    if (blank(bindDn) != blank(bindPassword))
      throw new IllegalStateException(
          "authorization.ldap.bind-dn and authorization.ldap.bind-password must be configured together");
    positive(connectTimeout, "authorization.ldap.connect-timeout");
    positive(readTimeout, "authorization.ldap.read-timeout");
    if (referral == null
        || !List.of("ignore", "follow", "throw").contains(referral.toLowerCase(Locale.ROOT)))
      throw new IllegalStateException(
          "authorization.ldap.referral must be ignore, follow, or throw");
    required(user.searchFilter, "authorization.ldap.user.search-filter");
    filter(user.searchFilter, "authorization.ldap.user.search-filter");
    attribute(user.identityAttribute, "authorization.ldap.user.identity-attribute", true);
    attribute(user.usernameAttribute, "authorization.ldap.user.username-attribute", false);
    attribute(user.emailAttribute, "authorization.ldap.user.email-attribute", false);
    attribute(user.firstNameAttribute, "authorization.ldap.user.first-name-attribute", false);
    attribute(user.lastNameAttribute, "authorization.ldap.user.last-name-attribute", false);
    attribute(user.memberOfAttribute, "authorization.ldap.user.member-of-attribute", false);
    attribute(user.enabledAttribute, "authorization.ldap.user.enabled-attribute", false);
    user.authorityAttributes.forEach(
        value -> attribute(value, "authorization.ldap.user.authority-attributes", true));
    if (!blank(group.searchFilter)) {
      filter(group.searchFilter, "authorization.ldap.group.search-filter");
      if (!group.searchFilter.contains("{0}"))
        throw new IllegalStateException(
            "authorization.ldap.group.search-filter must contain the user-DN placeholder {0}");
    }
    attribute(group.nameAttribute, "authorization.ldap.group.name-attribute", true);
    if (sync.pageSize < 1 || sync.pageSize > 1000)
      throw new IllegalStateException(
          "authorization.ldap.sync.page-size must be between 1 and 1000");
  }

  private String stripTrailingSlash(String value) {
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  private void positive(Duration value, String property) {
    if (value == null || value.isZero() || value.isNegative())
      throw new IllegalStateException(property + " must be positive");
  }

  private void filter(String value, String property) {
    String trimmed = value.trim();
    if (!trimmed.startsWith("(") || !trimmed.endsWith(")"))
      throw new IllegalStateException(property + " must be an LDAP filter enclosed in parentheses");
  }

  private void attribute(String value, String property, boolean required) {
    if (blank(value)) {
      if (required) throw new IllegalStateException(property + " is required");
      return;
    }
    if (!ATTRIBUTE_NAME.matcher(value).matches())
      throw new IllegalStateException(property + " contains an invalid LDAP attribute name");
  }

  private void required(String value, String property) {
    if (blank(value)) throw new IllegalStateException(property + " is required");
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  @Getter
  @Setter
  public static class User {
    private String baseDn = "";
    private String searchFilter = "(objectClass=person)";
    private String identityAttribute = "entryUUID";
    private boolean identityAttributeBinary;
    private String usernameAttribute = "uid";
    private String emailAttribute = "mail";
    private String firstNameAttribute = "givenName";
    private String lastNameAttribute = "sn";
    private String memberOfAttribute = "memberOf";
    private String enabledAttribute;
    private List<String> enabledValues =
        new ArrayList<>(List.of("true", "active", "enabled", "1"));
    private List<String> authorityAttributes = new ArrayList<>();

    public void setEnabledValues(List<String> value) {
      enabledValues = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }

    public void setAuthorityAttributes(List<String> value) {
      authorityAttributes = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }
  }

  @Getter
  @Setter
  public static class Group {
    private String baseDn = "";
    private String searchFilter = "(member={0})";
    private String nameAttribute = "cn";
    private boolean useDnAsAuthority;
  }

  @Getter
  @Setter
  public static class Sync {
    private boolean enabled = true;
    private int pageSize = 500;
    private boolean pagedResults = true;
    private String fullCron = "-";
    private String incrementalCron = "-";
  }
}
