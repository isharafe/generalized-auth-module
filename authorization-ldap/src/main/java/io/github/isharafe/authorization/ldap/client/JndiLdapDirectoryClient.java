package io.github.isharafe.authorization.ldap.client;

import io.github.isharafe.authorization.ldap.config.AuthorizationLdapProperties;
import io.github.isharafe.authorization.spi.AuthorizationObservation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.time.Duration;
import javax.naming.AuthenticationException;
import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.PartialResultException;
import javax.naming.ServiceUnavailableException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.PagedResultsControl;
import javax.naming.ldap.PagedResultsResponseControl;
import javax.naming.ldap.Rdn;

public final class JndiLdapDirectoryClient implements LdapDirectoryClient {
  private final AuthorizationLdapProperties properties;
  private final AuthorizationObservation observation;

  public JndiLdapDirectoryClient(
      AuthorizationLdapProperties properties, AuthorizationObservation observation) {
    properties.validate();
    this.properties = properties;
    this.observation = observation;
  }

  @Override
  public List<LdapUser> users() {
    return searchUsers(properties.getUser().getSearchFilter()).stream()
        .map(this::toUser)
        .toList();
  }

  @Override
  public Optional<LdapUser> user(String stableId) {
    if (stableId == null || stableId.isBlank()) return Optional.empty();
    String filter =
        "(&"
            + properties.getUser().getSearchFilter()
            + "("
            + properties.getUser().getIdentityAttribute()
            + "="
            + stableIdFilterValue(stableId)
            + "))";
    List<DirectoryEntry> matches = searchUsers(filter);
    if (matches.size() > 1)
      throw new LdapClientException(
          LdapFailureType.INVALID_RESPONSE,
          "LDAP stable identity search returned more than one user");
    return matches.stream().findFirst().map(this::toUser);
  }

  private List<DirectoryEntry> searchUsers(String filter) {
    return search(
        properties.getUser().getBaseDn(),
        filter,
        userAttributeNames(),
        properties.getSync().isPagedResults(),
        "user_search");
  }

  private LdapUser toUser(DirectoryEntry entry) {
    AuthorizationLdapProperties.User user = properties.getUser();
    String id = first(entry.attributes(), user.getIdentityAttribute());
    if (id == null || id.isBlank())
      throw new LdapClientException(
          LdapFailureType.INVALID_RESPONSE,
          "LDAP user " + entry.distinguishedName() + " has no stable identity attribute");

    Set<LdapAuthority> authorities = new LinkedHashSet<>();
    if (!blank(user.getMemberOfAttribute())) {
      for (String groupDn : values(entry.attributes(), user.getMemberOfAttribute())) {
        String authority = groupAuthority(groupDn, null);
        if (!blank(authority)) authorities.add(new LdapAuthority("GROUP", authority));
      }
    }
    for (String name : user.getAuthorityAttributes()) {
      for (String value : values(entry.attributes(), name)) {
        if (!blank(value)) authorities.add(new LdapAuthority("ATTRIBUTE", name + "=" + value));
      }
    }
    authorities.addAll(searchGroupAuthorities(entry.distinguishedName()));

    return new LdapUser(
        id,
        entry.distinguishedName(),
        first(entry.attributes(), user.getUsernameAttribute()),
        first(entry.attributes(), user.getEmailAttribute()),
        first(entry.attributes(), user.getFirstNameAttribute()),
        first(entry.attributes(), user.getLastNameAttribute()),
        enabled(entry.attributes()),
        authorities);
  }

  private Set<LdapAuthority> searchGroupAuthorities(String userDn) {
    AuthorizationLdapProperties.Group group = properties.getGroup();
    if (blank(group.getSearchFilter())) return Set.of();
    String filter = group.getSearchFilter().replace("{0}", escapeFilterValue(userDn));
    Set<LdapAuthority> authorities = new LinkedHashSet<>();
    for (DirectoryEntry entry :
        search(
            group.getBaseDn(),
            filter,
            new String[] {group.getNameAttribute()},
            false,
            "group_search")) {
      String authority = groupAuthority(entry.distinguishedName(), entry.attributes());
      if (!blank(authority)) authorities.add(new LdapAuthority("GROUP", authority));
    }
    return authorities;
  }

  private String groupAuthority(String distinguishedName, Attributes attributes) {
    if (properties.getGroup().isUseDnAsAuthority()) return distinguishedName;
    String fromAttributes =
        attributes == null ? null : first(attributes, properties.getGroup().getNameAttribute());
    if (!blank(fromAttributes)) return fromAttributes;
    try {
      for (Rdn rdn : new LdapName(distinguishedName).getRdns()) {
        if (rdn.getType().equalsIgnoreCase(properties.getGroup().getNameAttribute()))
          return stringValue(rdn.getValue());
      }
    } catch (NamingException exception) {
      throw new LdapClientException(
          LdapFailureType.INVALID_RESPONSE,
          "LDAP group membership contains an invalid distinguished name",
          exception);
    }
    return distinguishedName;
  }

  private boolean enabled(Attributes attributes) {
    AuthorizationLdapProperties.User user = properties.getUser();
    if (blank(user.getEnabledAttribute())) return true;
    List<String> actual = values(attributes, user.getEnabledAttribute());
    if (user.getEnabledValues().isEmpty()) return actual.stream().anyMatch(value -> !value.isBlank());
    Set<String> accepted =
        user.getEnabledValues().stream()
            .map(value -> value.toLowerCase(Locale.ROOT))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    return actual.stream().map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(accepted::contains);
  }

  private String[] userAttributeNames() {
    AuthorizationLdapProperties.User user = properties.getUser();
    LinkedHashSet<String> names = new LinkedHashSet<>();
    add(names, user.getIdentityAttribute());
    add(names, user.getUsernameAttribute());
    add(names, user.getEmailAttribute());
    add(names, user.getFirstNameAttribute());
    add(names, user.getLastNameAttribute());
    add(names, user.getMemberOfAttribute());
    add(names, user.getEnabledAttribute());
    user.getAuthorityAttributes().forEach(value -> add(names, value));
    return names.toArray(String[]::new);
  }

  private void add(Set<String> names, String value) {
    if (!blank(value)) names.add(value);
  }

  private List<DirectoryEntry> search(
      String baseDn,
      String filter,
      String[] returningAttributes,
      boolean paged,
      String operation) {
    LdapContext context = null;
    try {
      context = bind();
      SearchControls controls = new SearchControls();
      controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
      controls.setReturningAttributes(returningAttributes);
      return paged
          ? pagedSearch(context, baseDn, filter, controls, operation)
          : singleSearch(context, baseDn, filter, controls, operation);
    } catch (AuthenticationException exception) {
      throw new LdapClientException(
          LdapFailureType.AUTHENTICATION, "LDAP service bind authentication failed", exception);
    } catch (CommunicationException | ServiceUnavailableException exception) {
      throw new LdapClientException(
          LdapFailureType.CONNECTION, "LDAP directory is unavailable", exception);
    } catch (NamingException | IOException exception) {
      throw new LdapClientException(LdapFailureType.SEARCH, "LDAP directory search failed", exception);
    } finally {
      close(context);
    }
  }

  private List<DirectoryEntry> pagedSearch(
      LdapContext context,
      String baseDn,
      String filter,
      SearchControls controls,
      String operation)
      throws NamingException, IOException {
    List<DirectoryEntry> entries = new ArrayList<>();
    byte[] cookie = null;
    do {
      context.setRequestControls(
          new Control[] {
            new PagedResultsControl(properties.getSync().getPageSize(), cookie, Control.CRITICAL)
          });
      readSearchResults(context, baseDn, filter, controls, entries, operation);
      PageResponse response = pageResponse(context.getResponseControls());
      if (!response.present())
        throw new LdapClientException(
            LdapFailureType.INVALID_RESPONSE,
            "LDAP server did not return the requested paged-results response control");
      cookie = response.cookie();
    } while (cookie != null && cookie.length > 0);
    return entries;
  }

  private List<DirectoryEntry> singleSearch(
      LdapContext context,
      String baseDn,
      String filter,
      SearchControls controls,
      String operation)
      throws NamingException {
    List<DirectoryEntry> entries = new ArrayList<>();
    readSearchResults(context, baseDn, filter, controls, entries, operation);
    return entries;
  }

  private void readSearchResults(
      LdapContext context,
      String baseDn,
      String filter,
      SearchControls controls,
      List<DirectoryEntry> entries,
      String operation)
      throws NamingException {
    long started = System.nanoTime();
    String metricResult = "success";
    NamingEnumeration<SearchResult> results = null;
    try {
      results = context.search(baseDn == null ? "" : baseDn, filter, controls);
      while (results.hasMore()) {
        SearchResult result = results.next();
        entries.add(new DirectoryEntry(distinguishedName(result, baseDn), result.getAttributes()));
      }
    } catch (PartialResultException exception) {
      if (!properties.getReferral().equalsIgnoreCase("ignore")) {
        metricResult = "search_failure";
        throw exception;
      }
    } catch (NamingException exception) {
      metricResult = ldapResult(exception);
      throw exception;
    } finally {
      close(results);
      observation.recordExternalRequest(
          "ldap",
          operation,
          "SEARCH",
          metricResult,
          Duration.ofNanos(System.nanoTime() - started));
    }
  }

  private LdapContext bind() throws NamingException {
    long started = System.nanoTime();
    String result = "success";
    try {
      return new InitialLdapContext(environment(), null);
    } catch (NamingException exception) {
      result = ldapResult(exception);
      throw exception;
    } finally {
      observation.recordExternalRequest(
          "ldap",
          "service_bind",
          "BIND",
          result,
          Duration.ofNanos(System.nanoTime() - started));
    }
  }

  private String ldapResult(NamingException exception) {
    if (exception instanceof AuthenticationException) return "authentication";
    if (exception instanceof CommunicationException || exception instanceof ServiceUnavailableException)
      return "connection";
    return "search_failure";
  }

  private String distinguishedName(SearchResult result, String searchBase) {
    try {
      return result.getNameInNamespace();
    } catch (UnsupportedOperationException exception) {
      String relative = result.getName();
      String configuredBase = properties.getBaseDn();
      if (!blank(searchBase)) relative = relative + "," + searchBase;
      return blank(relative) ? configuredBase : relative + "," + configuredBase;
    }
  }

  private PageResponse pageResponse(Control[] controls) {
    if (controls == null) return new PageResponse(false, null);
    for (Control control : controls) {
      if (control instanceof PagedResultsResponseControl response)
        return new PageResponse(true, response.getCookie());
    }
    return new PageResponse(false, null);
  }

  private Hashtable<String, Object> environment() {
    Hashtable<String, Object> environment = new Hashtable<>();
    environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
    environment.put(
        Context.PROVIDER_URL,
        String.join(
            " ",
            properties.normalizedUrls().stream()
                .map(url -> url + "/" + properties.getBaseDn().trim())
                .toList()));
    environment.put(Context.REFERRAL, properties.getReferral().toLowerCase(Locale.ROOT));
    environment.put(
        "com.sun.jndi.ldap.connect.timeout",
        Long.toString(properties.getConnectTimeout().toMillis()));
    environment.put(
        "com.sun.jndi.ldap.read.timeout", Long.toString(properties.getReadTimeout().toMillis()));
    if (!blank(properties.getBindDn())) {
      environment.put(Context.SECURITY_AUTHENTICATION, "simple");
      environment.put(Context.SECURITY_PRINCIPAL, properties.getBindDn());
      environment.put(Context.SECURITY_CREDENTIALS, properties.getBindPassword());
    }
    return environment;
  }

  private String first(Attributes attributes, String name) {
    List<String> values = values(attributes, name);
    return values.isEmpty() ? null : values.getFirst();
  }

  private List<String> values(Attributes attributes, String name) {
    if (attributes == null || blank(name)) return List.of();
    Attribute attribute = attributes.get(name);
    if (attribute == null) return List.of();
    List<String> values = new ArrayList<>();
    NamingEnumeration<?> all = null;
    try {
      all = attribute.getAll();
      while (all.hasMore()) values.add(stringValue(all.next()));
      return List.copyOf(values);
    } catch (NamingException exception) {
      throw new LdapClientException(
          LdapFailureType.INVALID_RESPONSE,
          "LDAP attribute " + name + " could not be read",
          exception);
    } finally {
      close(all);
    }
  }

  private String stringValue(Object value) {
    if (value instanceof byte[] bytes)
      return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    return value == null ? "" : value.toString();
  }

  static String escapeFilterValue(String value) {
    return escapeFilterBytes(value.getBytes(StandardCharsets.UTF_8));
  }

  private String stableIdFilterValue(String value) {
    if (!properties.getUser().isIdentityAttributeBinary()) return escapeFilterValue(value);
    try {
      return escapeFilterBytes(Base64.getUrlDecoder().decode(value));
    } catch (IllegalArgumentException exception) {
      throw new LdapClientException(
          LdapFailureType.INVALID_RESPONSE,
          "LDAP binary stable identity is not valid base64url",
          exception);
    }
  }

  private static String escapeFilterBytes(byte[] bytes) {
    StringBuilder escaped = new StringBuilder();
    for (byte current : bytes) {
      int unsigned = current & 0xff;
      if (unsigned < 0x20
          || unsigned >= 0x7f
          || unsigned == '('
          || unsigned == ')'
          || unsigned == '*'
          || unsigned == '\\')
        escaped.append('\\').append(String.format(Locale.ROOT, "%02x", unsigned));
      else escaped.append((char) unsigned);
    }
    return escaped.toString();
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private void close(Context context) {
    if (context == null) return;
    try {
      context.close();
    } catch (NamingException ignored) {
      // Preserve the original client result or failure.
    }
  }

  private void close(NamingEnumeration<?> values) {
    if (values == null) return;
    try {
      values.close();
    } catch (NamingException ignored) {
      // Preserve the original client result or failure.
    }
  }

  private record DirectoryEntry(String distinguishedName, Attributes attributes) {}

  private record PageResponse(boolean present, byte[] cookie) {}
}
