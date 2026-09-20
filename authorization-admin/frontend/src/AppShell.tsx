import { useEffect, useState } from "react";
import { AdminApi } from "./api";
import { message } from "./components";
import {
  AuditPage,
  CodedCatalogPage,
  Dashboard,
  DataTransferPage,
  ExplainPage,
  MappingsPage,
  PermissionsPage,
  ResourcesPage,
  SyncPage,
  UsersPage
} from "./pages";
import type { Capabilities, CurrentUser, RuntimeConfig } from "./types";

type Route =
  | "dashboard"
  | "users"
  | "roles"
  | "groups"
  | "permissions"
  | "resources"
  | "mappings"
  | "explain"
  | "sync"
  | "audit"
  | "data";

const NAVIGATION: Array<{
  route: Route;
  label: string;
  eyebrow: string;
  capability?: keyof Capabilities;
}> = [
  { route: "dashboard", label: "Overview", eyebrow: "01" },
  { route: "users", label: "Users", eyebrow: "02" },
  { route: "roles", label: "Roles", eyebrow: "03" },
  { route: "groups", label: "Permission groups", eyebrow: "04" },
  { route: "permissions", label: "Permissions", eyebrow: "05" },
  { route: "resources", label: "Resources", eyebrow: "06" },
  {
    route: "mappings",
    label: "External mappings",
    eyebrow: "07",
    capability: "externalAuthorityMapping"
  },
  { route: "explain", label: "Authorization test", eyebrow: "08" },
  {
    route: "sync",
    label: "Synchronization",
    eyebrow: "09",
    capability: "identitySynchronization"
  },
  { route: "audit", label: "Audit", eyebrow: "10" },
  { route: "data", label: "Data transfer", eyebrow: "11" }
];

function routeFromHash(): Route {
  const hashRoute = window.location.hash.replace(/^#\/?/, "");
  const candidate = (hashRoute === "rules" ? "resources" : hashRoute) as Route;
  return NAVIGATION.some((item) => item.route === candidate)
    ? candidate
    : "dashboard";
}

export function App({
  config,
  api
}: {
  config: RuntimeConfig;
  api: AdminApi;
}) {
  const [capabilities, setCapabilities] = useState<Capabilities | null>(null);
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
  const [route, setRoute] = useState<Route>(routeFromHash());
  const [startupError, setStartupError] = useState("");

  useEffect(() => {
    Promise.all([api.capabilities(), api.currentUser()])
      .then(([loadedCapabilities, loadedUser]) => {
        setCapabilities(loadedCapabilities);
        setCurrentUser(loadedUser);
      })
      .catch((error) => setStartupError(message(error)));
  }, [api]);

  useEffect(() => {
    const listener = () => setRoute(routeFromHash());
    window.addEventListener("hashchange", listener);
    return () => window.removeEventListener("hashchange", listener);
  }, []);

  if (startupError)
    return (
      <main className="startup-error">
        <p className="eyebrow">Connection failed</p>
        <h1>Authorization Console</h1>
        <p>{startupError}</p>
      </main>
    );
  if (!capabilities || !currentUser)
    return (
      <main className="loading-screen">
        <div className="brand-mark">A</div>
        <p>Loading authorization controls...</p>
      </main>
    );

  const visibleNavigation = NAVIGATION.filter(
    (item) => !item.capability || Boolean(capabilities[item.capability])
  );
  if (!visibleNavigation.some((item) => item.route === route)) {
    window.location.hash = "#/dashboard";
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark">A</span>
          <div>
            <strong>Authorization</strong>
            <small>Control plane</small>
          </div>
        </div>
        <nav aria-label="Main navigation">
          {visibleNavigation.map((item) => (
            <a
              className={route === item.route ? "active" : ""}
              href={`#/${item.route}`}
              key={item.route}
            >
              <span>{item.eyebrow}</span>
              {item.label}
            </a>
          ))}
        </nav>
        <div className="source-card">
          <span className="status-dot" />
          <div>
            <small>Authority source</small>
            <strong>{capabilities.source}</strong>
          </div>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <div>
            <p className="eyebrow">Application authorization</p>
            <h1>{NAVIGATION.find((item) => item.route === route)?.label}</h1>
          </div>
          <div className="topbar-context">
            <div className="environment">
              <span>LOCAL DECISIONS</span>
              <small>{config.apiBasePath}</small>
            </div>
            <CurrentUserSummary user={currentUser} config={config} />
          </div>
        </header>
        <main className="content">
          <RouteContent
            route={route}
            api={api}
            capabilities={capabilities}
          />
        </main>
      </div>
    </div>
  );
}

function CurrentUserSummary({
  user,
  config
}: {
  user: CurrentUser;
  config: RuntimeConfig;
}) {
  const fullName = [user.firstName, user.lastName].filter(Boolean).join(" ");
  const displayName = fullName || user.username || user.subject;
  const secondary = user.email || user.username || user.subject;
  const initial = displayName.trim().charAt(0).toUpperCase() || "?";

  return (
    <section className="current-user" aria-label="Current logged in user">
      <span className="user-avatar" aria-hidden="true">{initial}</span>
      <div>
        <strong>{displayName}</strong>
        <span>{secondary}</span>
        <small className="identity-key" title={`${user.issuer} · ${user.subject}`}>
          {user.issuer} · {user.subject}
        </small>
      </div>
      {config.cookieOauth2Enabled &&
        config.logoutEndpoint &&
        config.csrfParameterName &&
        config.csrfToken && (
          <form method="post" action={config.logoutEndpoint}>
            <input
              type="hidden"
              name={config.csrfParameterName}
              value={config.csrfToken}
            />
            <button type="submit">
              {config.logoutMode === "OIDC" ? "Sign out everywhere" : "Sign out"}
            </button>
          </form>
        )}
    </section>
  );
}

function RouteContent({
  route,
  api,
  capabilities
}: {
  route: Route;
  api: AdminApi;
  capabilities: Capabilities;
}) {
  switch (route) {
    case "dashboard":
      return <Dashboard api={api} capabilities={capabilities} />;
    case "users":
      return <UsersPage api={api} />;
    case "roles":
      return (
        <CodedCatalogPage
          api={api}
          endpoint="/roles"
          title="Roles"
          singular="role"
          relationKey="permissionGroups"
          relationLabel="Permission groups"
        />
      );
    case "groups":
      return (
        <CodedCatalogPage
          api={api}
          endpoint="/permission-groups"
          title="Permission groups"
          singular="permission group"
          relationKey="permissions"
          relationLabel="Permissions"
        />
      );
    case "permissions":
      return <PermissionsPage api={api} />;
    case "resources":
      return <ResourcesPage api={api} />;
    case "mappings":
      return <MappingsPage api={api} source={capabilities.source} />;
    case "explain":
      return <ExplainPage api={api} />;
    case "sync":
      return <SyncPage api={api} />;
    case "audit":
      return <AuditPage api={api} />;
    case "data":
      return <DataTransferPage api={api} />;
  }
}

