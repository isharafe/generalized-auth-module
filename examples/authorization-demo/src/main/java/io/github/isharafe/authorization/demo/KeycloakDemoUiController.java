package io.github.isharafe.authorization.demo;

import io.github.isharafe.authorization.domain.ResourceType;
import io.github.isharafe.authorization.security.AuthorizationService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Profile("keycloak-demo")
@RequestMapping("/demo-ui")
public final class KeycloakDemoUiController {
  private static final String EMPLOYEE_DIRECTORY = "employeeDirectory";
  private static final String MANAGER_WORKSPACE = "managerWorkspace";

  private final AuthorizationService authorization;

  public KeycloakDemoUiController(AuthorizationService authorization) {
    this.authorization = authorization;
  }

  @GetMapping(value = "/signed-out", produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  public String signedOut() {
    return """
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>Signed out</title>
          <style>
            :root { color-scheme: light; font-family: Inter, ui-sans-serif, system-ui, sans-serif; }
            * { box-sizing: border-box; }
            body { display: grid; min-height: 100vh; margin: 0; place-items: center; color: #172033;
                   background: radial-gradient(circle at top left, #dff6ef, transparent 38%),
                               linear-gradient(135deg, #f8fbff, #eef2f9); }
            main { width: min(620px, calc(100% - 2rem)); padding: clamp(2rem, 7vw, 5rem);
                   border: 1px solid #d6dfeb; border-radius: 28px; background: rgba(255,255,255,.92);
                   box-shadow: 0 28px 70px rgba(42,58,82,.12); text-align: center; }
            h1 { margin: .25rem 0 1rem; font-size: clamp(2.2rem, 7vw, 4.5rem);
                 line-height: 1; letter-spacing: -.05em; }
            p { color: #59677d; line-height: 1.65; }
            .eyebrow { color: #217267; font-size: .78rem; font-weight: 850; letter-spacing: .14em; }
            a { display: inline-block; margin-top: 1.5rem; padding: .8rem 1.1rem;
                border-radius: 999px; background: #172033; color: white;
                text-decoration: none; font-weight: 750; }
          </style>
        </head>
        <body>
          <main>
            <p class="eyebrow">KEYCLOAK SESSION ENDED</p>
            <h1>Signed out</h1>
            <p>Your local application session and Keycloak single sign-on session have ended.</p>
            <a href="/demo-ui/">Sign in again</a>
          </main>
        </body>
        </html>
        """;
  }

  @GetMapping(value = {"", "/"}, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  public String index(Authentication authentication, CsrfToken csrfToken) {
    StringBuilder cards = new StringBuilder();
    if (authorization.isGranted(authentication, ResourceType.UI, EMPLOYEE_DIRECTORY))
      cards.append(
          card(
              "01",
              "Employee directory",
              "Visible because this user has UI:EMPLOYEE_DIRECTORY.",
              "/demo-ui/employee-directory"));
    if (authorization.isGranted(authentication, ResourceType.UI, MANAGER_WORKSPACE))
      cards.append(
          card(
              "02",
              "Manager workspace",
              "Visible because this user has UI:MANAGER_WORKSPACE.",
              "/demo-ui/manager-workspace"));
    if (cards.isEmpty())
      cards.append(
          """
          <section class="empty">
            <h2>No application workspaces are available</h2>
            <p>The login is valid, but this identity has neither demo UI permission.</p>
          </section>
          """);
    return layout(
        "Authorization demo",
        authentication,
        csrfToken,
        """
        <section class="hero">
          <p class="eyebrow">KEYCLOAK + LOCAL AUTHORIZATION</p>
          <h1>Permission-aware employee workspaces</h1>
          <p>Authentication came from Keycloak. Page visibility is decided from synchronized local
             permissions using the framework's <code>UI</code> resource strategy.</p>
        </section>
        <main class="grid">
        """
            + cards
            + "</main>");
  }

  @GetMapping(value = "/employee-directory", produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  public String employeeDirectory(Authentication authentication, CsrfToken csrfToken) {
    authorization.requireGranted(authentication, ResourceType.UI, EMPLOYEE_DIRECTORY);
    return layout(
        "Employee directory",
        authentication,
        csrfToken,
        """
        <main class="sample employee-directory">
          <p class="eyebrow">UI:EMPLOYEE_DIRECTORY</p>
          <h1>Employee directory</h1>
          <p>This route was rendered only after a server-side UI authorization decision.</p>
          <a class="button" href="/demo-ui/">Back to available pages</a>
        </main>
        """);
  }

  @GetMapping(value = "/manager-workspace", produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  public String managerWorkspace(Authentication authentication, CsrfToken csrfToken) {
    authorization.requireGranted(authentication, ResourceType.UI, MANAGER_WORKSPACE);
    return layout(
        "Manager workspace",
        authentication,
        csrfToken,
        """
        <main class="sample manager-workspace">
          <p class="eyebrow">UI:MANAGER_WORKSPACE</p>
          <h1>Manager workspace</h1>
          <p>Hiding the navigation link is only presentation; this direct route is checked again.</p>
          <a class="button" href="/demo-ui/">Back to available pages</a>
        </main>
        """);
  }

  private String card(String number, String title, String description, String href) {
    return """
        <a class="card" href="%s">
          <span class="card-number">%s</span>
          <h2>%s</h2>
          <p>%s</p>
          <span class="open">Open page &rarr;</span>
        </a>
        """
        .formatted(
            href,
            number,
            HtmlUtils.htmlEscape(title),
            HtmlUtils.htmlEscape(description));
  }

  private String layout(
      String title, Authentication authentication, CsrfToken csrfToken, String content) {
    return """
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>%s</title>
          <style>
            :root { color-scheme: light; font-family: Inter, ui-sans-serif, system-ui, sans-serif; }
            * { box-sizing: border-box; }
            body { margin: 0; min-height: 100vh; color: #172033;
                   background: radial-gradient(circle at top left, #dff6ef, transparent 38%%),
                               linear-gradient(135deg, #f8fbff, #eef2f9); }
            header { display: flex; justify-content: space-between; align-items: center;
                     padding: 1rem clamp(1.25rem, 4vw, 4rem); border-bottom: 1px solid #d9e1ec;
                     background: rgba(255,255,255,.8); backdrop-filter: blur(12px); }
            .brand { font-weight: 800; letter-spacing: -.02em; }
            .identity { display: flex; gap: 1rem; align-items: center; color: #526078; }
            .identity a { color: #245d53; font-weight: 700; }
            .identity form { margin: 0; }
            .identity button { border: 0; background: none; color: #245d53;
                               font: inherit; font-weight: 700; cursor: pointer; }
            .shell { width: min(1040px, calc(100%% - 2rem)); margin: 0 auto; padding: 4rem 0; }
            .hero { max-width: 760px; margin-bottom: 2.5rem; }
            h1 { margin: .25rem 0 1rem; font-size: clamp(2.2rem, 6vw, 4.8rem);
                 line-height: .98; letter-spacing: -.055em; }
            .hero p, .sample p, .card p, .empty p { color: #59677d; line-height: 1.65; }
            .eyebrow { margin: 0; color: #217267 !important; font-size: .78rem;
                       font-weight: 850; letter-spacing: .14em; }
            .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(270px, 1fr));
                    gap: 1.25rem; }
            .card { min-height: 260px; padding: 1.6rem; color: inherit; text-decoration: none;
                    border: 1px solid #d6dfeb; border-radius: 22px; background: rgba(255,255,255,.9);
                    box-shadow: 0 20px 50px rgba(42,58,82,.08); transition: .2s ease; }
            .card:hover { transform: translateY(-4px); border-color: #75a99f;
                          box-shadow: 0 24px 60px rgba(42,58,82,.14); }
            .card-number { display: block; color: #98a4b5; font-size: .8rem; font-weight: 800; }
            .card h2 { margin-top: 3rem; font-size: 1.65rem; }
            .open { display: inline-block; margin-top: 1.2rem; color: #217267; font-weight: 800; }
            .sample, .empty { max-width: 760px; padding: clamp(2rem, 6vw, 5rem);
                              border-radius: 28px; background: rgba(255,255,255,.92);
                              box-shadow: 0 28px 70px rgba(42,58,82,.12); }
            .employee-directory { border-top: 8px solid #39a58f; }
            .manager-workspace { border-top: 8px solid #6769d4; }
            .button { display: inline-block; margin-top: 2rem; padding: .8rem 1.1rem;
                      border-radius: 999px; background: #172033; color: white;
                      text-decoration: none; font-weight: 750; }
            code { padding: .15rem .35rem; border-radius: .35rem; background: #e7edf5; }
          </style>
        </head>
        <body>
          <header>
            <div class="brand">Authorization Demo</div>
            <div class="identity">
              <span>Signed in as %s</span>
              <form method="post" action="/authorization/security/logout"><input type="hidden"
                name="%s" value="%s"><button type="submit">Sign out</button></form>
            </div>
          </header>
          <div class="shell">%s</div>
        </body>
        </html>
        """
        .formatted(
            HtmlUtils.htmlEscape(title),
            HtmlUtils.htmlEscape(displayName(authentication)),
            HtmlUtils.htmlEscape(csrfToken.getParameterName()),
            HtmlUtils.htmlEscape(csrfToken.getToken()),
            content);
  }

  private String displayName(Authentication authentication) {
    if (authentication.getPrincipal() instanceof OAuth2AuthenticatedPrincipal principal) {
      Object preferredUsername = principal.getAttribute("preferred_username");
      if (preferredUsername != null) return String.valueOf(preferredUsername);
    }
    return authentication.getName();
  }
}
