import {
  type FormEvent,
  type ReactNode,
  useEffect,
  useState
} from "react";
import { ApiError, AdminApi } from "./api";
import type {
  Assignment,
  AuditEvent,
  AuthorizationDataBundle,
  AuthorizationDataImportResult,
  AuthorizationTestResponse,
  Capabilities,
  CurrentUser,
  EffectiveEntitlements,
  ExternalMapping,
  Page,
  Permission,
  PermissionGroup,
  ResourceRule,
  Role,
  RuntimeConfig,
  SyncStatus,
  User
} from "./types";

type Route =
  | "dashboard"
  | "users"
  | "roles"
  | "groups"
  | "permissions"
  | "rules"
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
  { route: "rules", label: "Resource rules", eyebrow: "06" },
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
  const candidate = window.location.hash.replace(/^#\/?/, "") as Route;
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
            <CurrentUserSummary user={currentUser} />
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

function CurrentUserSummary({ user }: { user: CurrentUser }) {
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
    case "rules":
      return <RulesPage api={api} />;
    case "mappings":
      return <MappingsPage api={api} />;
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

function Dashboard({
  api,
  capabilities
}: {
  api: AdminApi;
  capabilities: Capabilities;
}) {
  const [counts, setCounts] = useState<Record<string, number>>({});
  const [events, setEvents] = useState<AuditEvent[]>([]);
  const [error, setError] = useState("");

  useEffect(() => {
    Promise.all([
      api.page<User>("/users", { size: 1 }),
      api.page<Role>("/roles", { size: 1 }),
      api.page<PermissionGroup>("/permission-groups", { size: 1 }),
      api.page<Permission>("/permissions", { size: 1 }),
      api.page<ResourceRule>("/resource-rules", { size: 1 }),
      api.page<AuditEvent>("/audit", { size: 6, sort: "timestamp,desc" })
    ])
      .then(([users, roles, groups, permissions, rules, audit]) => {
        setCounts({
          Users: users.totalElements,
          Roles: roles.totalElements,
          Groups: groups.totalElements,
          Permissions: permissions.totalElements,
          Rules: rules.totalElements
        });
        setEvents(audit.content);
      })
      .catch((caught) => setError(message(caught)));
  }, [api]);

  return (
    <>
      <section className="hero-panel">
        <div>
          <p className="eyebrow">Decision system online</p>
          <h2>Local authorization, clearly explained.</h2>
          <p>
            Policy remains application-owned. Identities resolve to local roles,
            groups, and permissions before each decision.
          </p>
        </div>
        <div className="hero-source">
          <small>Current source</small>
          <strong>{capabilities.source}</strong>
          <span>
            {capabilities.identitySynchronization
              ? `Synchronized by ${capabilities.syncProvider}`
              : "Database-managed identities"}
          </span>
        </div>
      </section>
      {error && <Notice tone="error">{error}</Notice>}
      <section className="metric-grid" aria-label="Authorization object counts">
        {Object.entries(counts).map(([label, value]) => (
          <article className="metric-card" key={label}>
            <small>{label}</small>
            <strong>{value}</strong>
            <span>configured</span>
          </article>
        ))}
      </section>
      <section className="panel">
        <PageHeading
          eyebrow="Live trail"
          title="Recent authorization activity"
          description="The newest safe audit events from this application."
        />
        <AuditTable events={events} />
      </section>
    </>
  );
}

type CatalogItem = {
  code: string;
  name: string;
  description?: string | null;
  enabled: boolean;
  version: number;
  permissionGroups?: string[];
  permissions?: string[];
};

function CodedCatalogPage({
  api,
  endpoint,
  title,
  singular,
  relationKey,
  relationLabel
}: {
  api: AdminApi;
  endpoint: string;
  title: string;
  singular: string;
  relationKey: "permissionGroups" | "permissions";
  relationLabel: string;
}) {
  const [search, setSearch] = useState("");
  const [page, setPage] = useState<Page<CatalogItem> | null>(null);
  const [reload, setReload] = useState(0);
  const [error, setError] = useState("");
  const empty = {
    code: "",
    name: "",
    description: "",
    enabled: true,
    relations: "",
    version: undefined as number | undefined
  };
  const [draft, setDraft] = useState(empty);

  useEffect(() => {
    api
      .page<CatalogItem>(endpoint, { search, size: 50, sort: "code,asc" })
      .then(setPage)
      .catch((caught) => setError(message(caught)));
  }, [api, endpoint, search, reload]);

  const edit = (item: CatalogItem) =>
    setDraft({
      code: item.code,
      name: item.name,
      description: item.description ?? "",
      enabled: item.enabled,
      relations: (item[relationKey] ?? []).join(", "),
      version: item.version
    });

  const save = async (event: FormEvent) => {
    event.preventDefault();
    setError("");
    const body = {
      code: draft.code.trim(),
      name: draft.name.trim(),
      description: draft.description.trim() || null,
      enabled: draft.enabled,
      [relationKey]: codes(draft.relations),
      ...(draft.version === undefined ? {} : { version: draft.version })
    };
    try {
      if (draft.version === undefined)
        await api.post<CatalogItem>(endpoint, body);
      else await api.put<CatalogItem>(`${endpoint}/${encodeURIComponent(draft.code)}`, body);
      setDraft(empty);
      setReload((value) => value + 1);
    } catch (caught) {
      setError(message(caught));
    }
  };

  const disable = async (item: CatalogItem) => {
    if (!window.confirm(`Disable ${singular} ${item.code}?`)) return;
    try {
      await api.delete<void>(
        `${endpoint}/${encodeURIComponent(item.code)}?version=${item.version}`
      );
      setReload((value) => value + 1);
    } catch (caught) {
      setError(message(caught));
    }
  };

  return (
    <div className="split-layout">
      <section className="panel list-panel">
        <PageHeading
          eyebrow="Application-owned"
          title={title}
          description={`Create and maintain ${title.toLowerCase()} and their relationships.`}
        />
        <Search value={search} onChange={setSearch} />
        <div className="record-list">
          {page?.content.map((item) => (
            <article className="record" key={item.code}>
              <div>
                <div className="record-title">
                  <code>{item.code}</code>
                  <Status enabled={item.enabled} />
                </div>
                <strong>{item.name}</strong>
                <small>
                  {(item[relationKey] ?? []).length} {relationLabel.toLowerCase()}
                </small>
              </div>
              <div className="row-actions">
                <button className="quiet" onClick={() => edit(item)}>
                  Edit
                </button>
                <button className="danger-link" onClick={() => disable(item)}>
                  Disable
                </button>
              </div>
            </article>
          ))}
          {page?.content.length === 0 && <Empty label={title} />}
        </div>
      </section>
      <section className="panel editor-panel">
        <PageHeading
          eyebrow={draft.version === undefined ? "Create" : "Update"}
          title={draft.version === undefined ? `New ${singular}` : draft.code}
          description="Codes are stable and become read-only after creation."
        />
        {error && <Notice tone="error">{error}</Notice>}
        <form onSubmit={save}>
          <Field label="Code">
            <input
              required
              disabled={draft.version !== undefined}
              value={draft.code}
              onChange={(event) => setDraft({ ...draft, code: event.target.value })}
            />
          </Field>
          <Field label="Name">
            <input
              required
              value={draft.name}
              onChange={(event) => setDraft({ ...draft, name: event.target.value })}
            />
          </Field>
          <Field label="Description">
            <textarea
              rows={3}
              value={draft.description}
              onChange={(event) =>
                setDraft({ ...draft, description: event.target.value })
              }
            />
          </Field>
          <Field
            label={relationLabel}
            hint="Comma-separated stable codes; validated by the server."
          >
            <textarea
              rows={3}
              value={draft.relations}
              onChange={(event) =>
                setDraft({ ...draft, relations: event.target.value })
              }
            />
          </Field>
          <Check
            checked={draft.enabled}
            onChange={(enabled) => setDraft({ ...draft, enabled })}
            label="Enabled"
          />
          <FormActions
            editing={draft.version !== undefined}
            onCancel={() => setDraft(empty)}
          />
        </form>
      </section>
    </div>
  );
}

function PermissionsPage({ api }: { api: AdminApi }) {
  const empty = {
    code: "",
    name: "",
    description: "",
    resourceType: "URL" as "URL" | "UI",
    method: "GET",
    resource: "/api/**",
    enabled: true,
    version: undefined as number | undefined
  };
  const [draft, setDraft] = useState(empty);
  const [search, setSearch] = useState("");
  const [items, setItems] = useState<Permission[]>([]);
  const [reload, setReload] = useState(0);
  const [error, setError] = useState("");

  useEffect(() => {
    api
      .page<Permission>("/permissions", { search, size: 100, sort: "code,asc" })
      .then((value) => setItems(value.content))
      .catch((caught) => setError(message(caught)));
  }, [api, search, reload]);

  const pattern =
    draft.resourceType === "URL"
      ? `${draft.method.toUpperCase()}:${draft.resource}`
      : draft.resource;

  const edit = (item: Permission) => {
    const separator = item.pattern.indexOf(":");
    setDraft({
      code: item.code,
      name: item.name,
      description: item.description ?? "",
      resourceType: item.resourceType,
      method:
        item.resourceType === "URL" ? item.pattern.slice(0, separator) : "GET",
      resource:
        item.resourceType === "URL"
          ? item.pattern.slice(separator + 1)
          : item.pattern,
      enabled: item.enabled,
      version: item.version
    });
  };

  const save = async (event: FormEvent) => {
    event.preventDefault();
    const body = {
      code: draft.code,
      name: draft.name,
      description: draft.description || null,
      resourceType: draft.resourceType,
      pattern,
      enabled: draft.enabled,
      ...(draft.version === undefined ? {} : { version: draft.version })
    };
    try {
      if (draft.version === undefined) await api.post("/permissions", body);
      else
        await api.put(
          `/permissions/${encodeURIComponent(draft.code)}`,
          body
        );
      setDraft(empty);
      setReload((value) => value + 1);
    } catch (caught) {
      setError(message(caught));
    }
  };

  const disable = async (item: Permission) => {
    try {
      await api.delete(
        `/permissions/${encodeURIComponent(item.code)}?version=${item.version}`
      );
      setReload((value) => value + 1);
    } catch (caught) {
      setError(message(caught));
    }
  };

  return (
    <div className="split-layout">
      <section className="panel list-panel">
        <PageHeading
          eyebrow="Keys"
          title="Permissions"
          description="Resource-neutral keys interpreted by their registered strategy."
        />
        <Search value={search} onChange={setSearch} />
        <div className="record-list">
          {items.map((item) => (
            <article className="record" key={item.code}>
              <div>
                <div className="record-title">
                  <code>{item.code}</code>
                  <Badge>{item.resourceType}</Badge>
                  <Status enabled={item.enabled} />
                </div>
                <strong>{item.name}</strong>
                <small className="mono">{item.pattern}</small>
              </div>
              <div className="row-actions">
                <button className="quiet" onClick={() => edit(item)}>
                  Edit
                </button>
                <button className="danger-link" onClick={() => disable(item)}>
                  Disable
                </button>
              </div>
            </article>
          ))}
        </div>
      </section>
      <section className="panel editor-panel">
        <PageHeading
          eyebrow={draft.version === undefined ? "Create key" : "Edit key"}
          title={draft.version === undefined ? "New permission" : draft.code}
          description="URL permissions combine the HTTP method and path pattern."
        />
        {error && <Notice tone="error">{error}</Notice>}
        <form onSubmit={save}>
          <Field label="Code">
            <input
              required
              disabled={draft.version !== undefined}
              value={draft.code}
              onChange={(event) => setDraft({ ...draft, code: event.target.value })}
            />
          </Field>
          <Field label="Name">
            <input
              required
              value={draft.name}
              onChange={(event) => setDraft({ ...draft, name: event.target.value })}
            />
          </Field>
          <Field label="Resource type">
            <select
              value={draft.resourceType}
              onChange={(event) =>
                setDraft({
                  ...draft,
                  resourceType: event.target.value as "URL" | "UI",
                  resource: event.target.value === "URL" ? "/api/**" : "ui.element"
                })
              }
            >
              <option>URL</option>
              <option>UI</option>
            </select>
          </Field>
          {draft.resourceType === "URL" && (
            <Field label="HTTP method">
              <select
                value={draft.method}
                onChange={(event) => setDraft({ ...draft, method: event.target.value })}
              >
                {["GET", "POST", "PUT", "PATCH", "DELETE", "*"].map((method) => (
                  <option key={method}>{method}</option>
                ))}
              </select>
            </Field>
          )}
          <Field label={draft.resourceType === "URL" ? "Path pattern" : "UI identifier"}>
            <input
              required
              value={draft.resource}
              onChange={(event) =>
                setDraft({ ...draft, resource: event.target.value })
              }
            />
          </Field>
          <div className="pattern-preview">
            <small>Permission preview</small>
            <code>{draft.resourceType}:{pattern}</code>
          </div>
          <Check
            checked={draft.enabled}
            onChange={(enabled) => setDraft({ ...draft, enabled })}
            label="Enabled"
          />
          <FormActions
            editing={draft.version !== undefined}
            onCancel={() => setDraft(empty)}
          />
        </form>
      </section>
    </div>
  );
}

function RulesPage({ api }: { api: AdminApi }) {
  const empty = {
    code: "",
    resourceType: "URL" as "URL" | "UI",
    method: "*",
    resource: "/api/**",
    accessMode: "AUTHORIZED" as ResourceRule["accessMode"],
    priority: 0,
    enabled: true,
    version: undefined as number | undefined
  };
  const [draft, setDraft] = useState(empty);
  const [items, setItems] = useState<ResourceRule[]>([]);
  const [search, setSearch] = useState("");
  const [reload, setReload] = useState(0);
  const [error, setError] = useState("");

  useEffect(() => {
    api
      .page<ResourceRule>("/resource-rules", {
        search,
        size: 100,
        sort: "code,asc"
      })
      .then((page) => setItems(page.content))
      .catch((caught) => setError(message(caught)));
  }, [api, search, reload]);

  const edit = (item: ResourceRule) => {
    const separator = item.pattern.indexOf(":");
    setDraft({
      code: item.code,
      resourceType: item.resourceType,
      method:
        item.resourceType === "URL" ? item.pattern.slice(0, separator) : "*",
      resource:
        item.resourceType === "URL"
          ? item.pattern.slice(separator + 1)
          : item.pattern,
      accessMode: item.accessMode,
      priority: item.priority,
      enabled: item.enabled,
      version: item.version
    });
  };

  const save = async (event: FormEvent) => {
    event.preventDefault();
    const body = {
      code: draft.code,
      resourceType: draft.resourceType,
      pattern:
        draft.resourceType === "URL"
          ? `${draft.method}:${draft.resource}`
          : draft.resource,
      accessMode: draft.accessMode,
      priority: draft.priority,
      enabled: draft.enabled,
      ...(draft.version === undefined ? {} : { version: draft.version })
    };
    try {
      if (draft.version === undefined) await api.post("/resource-rules", body);
      else
        await api.put(
          `/resource-rules/${encodeURIComponent(draft.code)}`,
          body
        );
      setDraft(empty);
      setReload((value) => value + 1);
    } catch (caught) {
      setError(message(caught));
    }
  };

  const disable = async (item: ResourceRule) => {
    if (!window.confirm(`Disable resource rule ${item.code}?`)) return;
    try {
      await api.delete(
        `/resource-rules/${encodeURIComponent(item.code)}?version=${item.version}`
      );
      setReload((value) => value + 1);
    } catch (caught) {
      setError(message(caught));
    }
  };

  return (
    <div className="split-layout">
      <section className="panel list-panel">
        <PageHeading
          eyebrow="Locks"
          title="Resource rules"
          description="Most-specific matching rules decide the required access mode."
        />
        <Search value={search} onChange={setSearch} />
        <div className="record-list">
          {items.map((item) => (
            <article className="record" key={item.code}>
              <div>
                <div className="record-title">
                  <code>{item.code}</code>
                  <Badge>{item.resourceType}</Badge>
                  <Badge>{item.accessMode}</Badge>
                  <Status enabled={item.enabled} />
                </div>
                <strong className="mono">{item.pattern}</strong>
                <small>Priority {item.priority}</small>
              </div>
              <div className="row-actions">
                <button className="quiet" onClick={() => edit(item)}>
                  Edit
                </button>
                <button className="danger-link" onClick={() => disable(item)}>
                  Disable
                </button>
              </div>
            </article>
          ))}
        </div>
      </section>
      <section className="panel editor-panel">
        <PageHeading
          eyebrow="Rule editor"
          title={draft.version === undefined ? "New resource rule" : draft.code}
          description="Conflicting rules at equal specificity and priority are rejected."
        />
        {error && <Notice tone="error">{error}</Notice>}
        <form onSubmit={save}>
          <Field label="Code">
            <input
              required
              disabled={draft.version !== undefined}
              value={draft.code}
              onChange={(event) => setDraft({ ...draft, code: event.target.value })}
            />
          </Field>
          <Field label="Resource type">
            <select
              value={draft.resourceType}
              onChange={(event) =>
                setDraft({
                  ...draft,
                  resourceType: event.target.value as "URL" | "UI"
                })
              }
            >
              <option>URL</option>
              <option>UI</option>
            </select>
          </Field>
          {draft.resourceType === "URL" && (
            <Field label="HTTP method">
              <select
                value={draft.method}
                onChange={(event) => setDraft({ ...draft, method: event.target.value })}
              >
                {["*", "GET", "POST", "PUT", "PATCH", "DELETE"].map((method) => (
                  <option key={method}>{method}</option>
                ))}
              </select>
            </Field>
          )}
          <Field label={draft.resourceType === "URL" ? "Path pattern" : "UI identifier"}>
            <input
              required
              value={draft.resource}
              onChange={(event) =>
                setDraft({ ...draft, resource: event.target.value })
              }
            />
          </Field>
          <Field label="Access mode">
            <select
              value={draft.accessMode}
              onChange={(event) =>
                setDraft({
                  ...draft,
                  accessMode: event.target.value as ResourceRule["accessMode"]
                })
              }
            >
              {["PERMIT_ALL", "AUTHENTICATED", "AUTHORIZED", "DENY_ALL"].map(
                (mode) => (
                  <option key={mode}>{mode}</option>
                )
              )}
            </select>
          </Field>
          <Field label="Priority">
            <input
              type="number"
              value={draft.priority}
              onChange={(event) =>
                setDraft({ ...draft, priority: Number(event.target.value) })
              }
            />
          </Field>
          <Check
            checked={draft.enabled}
            onChange={(enabled) => setDraft({ ...draft, enabled })}
            label="Enabled"
          />
          <FormActions
            editing={draft.version !== undefined}
            onCancel={() => setDraft(empty)}
          />
        </form>
      </section>
    </div>
  );
}

function UsersPage({ api }: { api: AdminApi }) {
  const [users, setUsers] = useState<User[]>([]);
  const [roles, setRoles] = useState<Role[]>([]);
  const [groups, setGroups] = useState<PermissionGroup[]>([]);
  const [selected, setSelected] = useState<User | null>(null);
  const [effective, setEffective] = useState<EffectiveEntitlements | null>(null);
  const [search, setSearch] = useState("");
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);

  useEffect(() => {
    Promise.all([
      api.page<User>("/users", { search, size: 100 }),
      api.page<Role>("/roles", { size: 100 }),
      api.page<PermissionGroup>("/permission-groups", { size: 100 })
    ])
      .then(([userPage, rolePage, groupPage]) => {
        setUsers(userPage.content);
        setRoles(rolePage.content);
        setGroups(groupPage.content);
        if (selected) {
          const refreshed = userPage.content.find((user) => user.id === selected.id);
          if (refreshed) setSelected(refreshed);
        }
      })
      .catch((caught) => setError(message(caught)));
  }, [api, search, reload]);

  useEffect(() => {
    if (!selected) return setEffective(null);
    api
      .get<EffectiveEntitlements>(
        `/users/${selected.id}/effective-permissions`
      )
      .then(setEffective)
      .catch((caught) => setError(message(caught)));
  }, [api, selected?.id, selected?.entitlementVersion]);

  const assign = async (kind: "roles" | "permission-groups", code: string) => {
    if (!selected || !code) return;
    try {
      const updated = await api.put<User>(
        `/users/${selected.id}/${kind}/${encodeURIComponent(code)}`
      );
      setSelected(updated);
      setReload((value) => value + 1);
    } catch (caught) {
      setError(message(caught));
    }
  };

  const remove = async (
    kind: "roles" | "permission-groups",
    assignment: Assignment
  ) => {
    if (!selected || assignment.source !== "MANUAL") return;
    try {
      const updated = await api.delete<User>(
        `/users/${selected.id}/${kind}/${encodeURIComponent(
          assignment.code
        )}`
      );
      setSelected(updated);
      setReload((value) => value + 1);
    } catch (caught) {
      setError(message(caught));
    }
  };

  return (
    <div className="split-layout users-layout">
      <section className="panel list-panel">
        <PageHeading
          eyebrow="Stable identities"
          title="Users"
          description="Users are keyed by issuer and subject, never mutable usernames."
        />
        <Search value={search} onChange={setSearch} />
        <div className="record-list">
          {users.map((user) => (
            <button
              className={`user-row ${selected?.id === user.id ? "selected" : ""}`}
              key={user.id}
              onClick={() => setSelected(user)}
            >
              <span className="avatar">
                {(user.username ?? user.subject).slice(0, 2).toUpperCase()}
              </span>
              <span>
                <strong>{user.username ?? user.subject}</strong>
                <small>{user.issuer} / {user.subject}</small>
              </span>
              <Status enabled={user.enabled} />
            </button>
          ))}
        </div>
      </section>
      <section className="panel detail-panel">
        {!selected ? (
          <Empty label="Select a user to inspect assignments" />
        ) : (
          <>
            <PageHeading
              eyebrow="Identity detail"
              title={selected.username ?? selected.subject}
              description={`${selected.issuer} / ${selected.subject}`}
            />
            {error && <Notice tone="error">{error}</Notice>}
            <div className="identity-grid">
              <Detail label="Email" value={selected.email ?? "Not provided"} />
              <Detail
                label="Name"
                value={
                  [selected.firstName, selected.lastName].filter(Boolean).join(" ") ||
                  "Not provided"
                }
              />
              <Detail
                label="Directory ID"
                value={selected.externalDirectoryId ?? "Local identity"}
              />
              <Detail
                label="Entitlement version"
                value={String(selected.entitlementVersion)}
              />
            </div>
            <AssignmentSection
              title="Roles"
              assignments={selected.roles}
              options={roles.map((role) => role.code)}
              onAssign={(code) => assign("roles", code)}
              onRemove={(assignment) => remove("roles", assignment)}
            />
            <AssignmentSection
              title="Permission groups"
              assignments={selected.permissionGroups}
              options={groups.map((group) => group.code)}
              onAssign={(code) => assign("permission-groups", code)}
              onRemove={(assignment) => remove("permission-groups", assignment)}
            />
            <div className="subsection">
              <div>
                <p className="eyebrow">Resolved</p>
                <h3>Effective permissions</h3>
              </div>
              <div className="permission-grid">
                {effective?.permissions.map((permission) => (
                  <article key={permission.code}>
                    <code>{permission.code}</code>
                    <small>{permission.resourceType}:{permission.pattern}</small>
                  </article>
                ))}
                {effective?.permissions.length === 0 && (
                  <p className="muted">No effective permissions.</p>
                )}
              </div>
            </div>
          </>
        )}
      </section>
    </div>
  );
}

function AssignmentSection({
  title,
  assignments,
  options,
  onAssign,
  onRemove
}: {
  title: string;
  assignments: Assignment[];
  options: string[];
  onAssign: (code: string) => void;
  onRemove: (assignment: Assignment) => void;
}) {
  const [choice, setChoice] = useState("");
  return (
    <div className="subsection">
      <div className="subsection-heading">
        <div>
          <p className="eyebrow">Assignments</p>
          <h3>{title}</h3>
        </div>
        <div className="inline-control">
          <select value={choice} onChange={(event) => setChoice(event.target.value)}>
            <option value="">Choose...</option>
            {options.map((option) => (
              <option key={option}>{option}</option>
            ))}
          </select>
          <button
            className="quiet"
            onClick={() => {
              onAssign(choice);
              setChoice("");
            }}
          >
            Assign
          </button>
        </div>
      </div>
      <div className="chip-list">
        {assignments.map((assignment) => (
          <span className="assignment-chip" key={assignment.code}>
            {assignment.code}
            <Badge tone={assignment.source.toLowerCase()}>
              {assignment.source}
            </Badge>
            {assignment.source === "MANUAL" && (
              <button
                aria-label={`Remove ${assignment.code}`}
                onClick={() => onRemove(assignment)}
              >
                x
              </button>
            )}
          </span>
        ))}
      </div>
      {assignments.some((assignment) => assignment.source !== "MANUAL") && (
        <p className="hint">
          Seed and synchronized assignments are read-only in the local admin UI.
        </p>
      )}
    </div>
  );
}

function MappingsPage({ api }: { api: AdminApi }) {
  const empty: ExternalMapping = {
    sourceSystem: "KEYCLOAK",
    authorityType: "GROUP",
    authorityValue: "",
    targetType: "ROLE",
    targetCode: "",
    enabled: true
  };
  const [draft, setDraft] = useState<ExternalMapping>(empty);
  const [items, setItems] = useState<ExternalMapping[]>([]);
  const [search, setSearch] = useState("");
  const [reload, setReload] = useState(0);
  const [error, setError] = useState("");

  useEffect(() => {
    api
      .page<ExternalMapping>("/external-mappings", {
        search,
        size: 100,
        sort: "id,asc"
      })
      .then((page) => setItems(page.content))
      .catch((caught) => setError(message(caught)));
  }, [api, search, reload]);

  const save = async (event: FormEvent) => {
    event.preventDefault();
    try {
      if (draft.id === undefined) await api.post("/external-mappings", draft);
      else await api.put(`/external-mappings/${draft.id}`, draft);
      setDraft(empty);
      setReload((value) => value + 1);
    } catch (caught) {
      setError(message(caught));
    }
  };

  const remove = async (item: ExternalMapping) => {
    try {
      await api.delete(
        `/external-mappings/${item.id}?version=${item.version}`
      );
      setReload((value) => value + 1);
    } catch (caught) {
      setError(message(caught));
    }
  };

  return (
    <div className="split-layout">
      <section className="panel list-panel">
        <PageHeading
          eyebrow="Explicit translation"
          title="External mappings"
          description="External authorities map to local roles or permission groups."
        />
        <Search value={search} onChange={setSearch} />
        <div className="record-list">
          {items.map((item) => (
            <article className="mapping-card" key={item.id}>
              <div>
                <Badge>{item.sourceSystem}</Badge>
                <strong>{item.authorityType} {item.authorityValue}</strong>
                <span>to {item.targetType} <code>{item.targetCode}</code></span>
              </div>
              <div className="row-actions">
                <button className="quiet" onClick={() => setDraft(item)}>
                  Edit
                </button>
                <button className="danger-link" onClick={() => remove(item)}>
                  Delete
                </button>
              </div>
            </article>
          ))}
        </div>
      </section>
      <section className="panel editor-panel">
        <PageHeading
          eyebrow="Authority map"
          title={draft.id ? "Edit mapping" : "New mapping"}
          description="Application permissions remain local and are never imported directly."
        />
        {error && <Notice tone="error">{error}</Notice>}
        <form onSubmit={save}>
          <Field label="Source system">
            <input
              required
              value={draft.sourceSystem}
              onChange={(event) =>
                setDraft({ ...draft, sourceSystem: event.target.value })
              }
            />
          </Field>
          <Field label="Authority type">
            <select
              value={draft.authorityType}
              onChange={(event) =>
                setDraft({ ...draft, authorityType: event.target.value })
              }
            >
              <option>GROUP</option>
              <option>ROLE</option>
            </select>
          </Field>
          <Field label="Authority value">
            <input
              required
              value={draft.authorityValue}
              onChange={(event) =>
                setDraft({ ...draft, authorityValue: event.target.value })
              }
            />
          </Field>
          <Field label="Local target type">
            <select
              value={draft.targetType}
              onChange={(event) =>
                setDraft({
                  ...draft,
                  targetType: event.target.value as ExternalMapping["targetType"]
                })
              }
            >
              <option>ROLE</option>
              <option>PERMISSION_GROUP</option>
            </select>
          </Field>
          <Field label="Local target code">
            <input
              required
              value={draft.targetCode}
              onChange={(event) =>
                setDraft({ ...draft, targetCode: event.target.value })
              }
            />
          </Field>
          <Check
            checked={draft.enabled}
            onChange={(enabled) => setDraft({ ...draft, enabled })}
            label="Enabled"
          />
          <FormActions editing={draft.id !== undefined} onCancel={() => setDraft(empty)} />
        </form>
      </section>
    </div>
  );
}

function ExplainPage({ api }: { api: AdminApi }) {
  const [issuer, setIssuer] = useState("local");
  const [subject, setSubject] = useState("manager");
  const [method, setMethod] = useState("GET");
  const [path, setPath] = useState("/demo/employees");
  const [result, setResult] = useState<AuthorizationTestResponse | null>(null);
  const [error, setError] = useState("");

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    try {
      setResult(
        await api.post<AuthorizationTestResponse>("/authorization-test", {
          identity: { issuer, subject, username: subject },
          resourceType: "URL",
          method,
          path
        })
      );
      setError("");
    } catch (caught) {
      setError(message(caught));
    }
  };

  return (
    <div className="explain-layout">
      <section className="panel editor-panel">
        <PageHeading
          eyebrow="Safe simulation"
          title="Test an authorization decision"
          description="Run the same local decision engine without executing the protected action."
        />
        {error && <Notice tone="error">{error}</Notice>}
        <form onSubmit={submit}>
          <div className="form-grid">
            <Field label="Issuer">
              <input value={issuer} onChange={(event) => setIssuer(event.target.value)} />
            </Field>
            <Field label="Subject">
              <input value={subject} onChange={(event) => setSubject(event.target.value)} />
            </Field>
            <Field label="Method">
              <select value={method} onChange={(event) => setMethod(event.target.value)}>
                {["GET", "POST", "PUT", "PATCH", "DELETE"].map((value) => (
                  <option key={value}>{value}</option>
                ))}
              </select>
            </Field>
            <Field label="Path">
              <input value={path} onChange={(event) => setPath(event.target.value)} />
            </Field>
          </div>
          <button className="primary" type="submit">
            Evaluate decision
          </button>
        </form>
      </section>
      <section className="decision-panel">
        {!result ? (
          <div className="decision-empty">
            <span>?</span>
            <h3>Awaiting a test</h3>
            <p>The matched rule, permission, and assignment path will appear here.</p>
          </div>
        ) : (
          <>
            <p className="eyebrow">Decision result</p>
            <div className={`decision ${result.decision.toLowerCase()}`}>
              {result.decision}
            </div>
            <Detail label="Reason" value={result.reason} />
            <Detail label="Matched rule" value={result.matchedRule ?? "None"} />
            <Detail
              label="Matched permission"
              value={result.matchedPermission ?? "None"}
            />
            <div className="decision-path">
              {result.assignmentPath.map((step, index) => (
                <div key={step}>
                  <span>{index + 1}</span>
                  <code>{step}</code>
                </div>
              ))}
            </div>
          </>
        )}
      </section>
    </div>
  );
}

function SyncPage({ api }: { api: AdminApi }) {
  const [status, setStatus] = useState<SyncStatus | null>(null);
  const [issuer, setIssuer] = useState("keycloak");
  const [subject, setSubject] = useState("");
  const [error, setError] = useState("");

  const load = () =>
    api
      .get<SyncStatus>("/sync/status")
      .then(setStatus)
      .catch((caught) => setError(message(caught)));

  useEffect(() => {
    void load();
  }, [api]);

  const run = async (path: string) => {
    try {
      setStatus(await api.post<SyncStatus>(path));
      setError("");
    } catch (caught) {
      setError(message(caught));
    }
  };

  return (
    <section className="panel">
      <PageHeading
        eyebrow="External identity"
        title="Synchronization"
        description="Runtime authorization stays local while identities reconcile in the background."
      />
      {error && <Notice tone="error">{error}</Notice>}
      <div className="sync-status">
        <span className="status-dot" />
        <div>
          <small>{status?.provider ?? "Provider"}</small>
          <strong>{status?.status ?? "Loading..."}</strong>
          <span>{status?.updatedAt ?? "No completed run reported"}</span>
        </div>
      </div>
      <div className="sync-actions">
        <button className="primary" onClick={() => run("/sync/incremental")}>
          Run incremental
        </button>
        <button className="quiet" onClick={() => run("/sync/full")}>
          Run full reconciliation
        </button>
      </div>
      <div className="subsection">
        <h3>Target one identity</h3>
        <div className="inline-control wide">
          <input value={issuer} onChange={(event) => setIssuer(event.target.value)} />
          <input
            placeholder="Subject"
            value={subject}
            onChange={(event) => setSubject(event.target.value)}
          />
          <button
            className="quiet"
            disabled={!subject}
            onClick={() =>
              run(
                `/sync/users/${encodeURIComponent(subject)}?issuer=${encodeURIComponent(
                  issuer
                )}`
              )
            }
          >
            Synchronize
          </button>
        </div>
      </div>
    </section>
  );
}

const DATA_SECTIONS: Array<{
  key: keyof Pick<
    AuthorizationDataBundle,
    | "permissions"
    | "permissionGroups"
    | "roles"
    | "resourceRules"
    | "users"
    | "externalMappings"
    | "pendingUserAssignments"
  >;
  label: string;
}> = [
  { key: "permissions", label: "Permissions" },
  { key: "permissionGroups", label: "Permission groups" },
  { key: "roles", label: "Roles" },
  { key: "resourceRules", label: "Resource rules" },
  { key: "users", label: "Users" },
  { key: "externalMappings", label: "External mappings" },
  { key: "pendingUserAssignments", label: "Pending assignments" }
];

function DataTransferPage({ api }: { api: AdminApi }) {
  const [bundle, setBundle] = useState<AuthorizationDataBundle | null>(null);
  const [fileName, setFileName] = useState("");
  const [acknowledged, setAcknowledged] = useState(false);
  const [result, setResult] = useState<AuthorizationDataImportResult | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const exportData = async () => {
    setBusy(true);
    setError("");
    try {
      const exported = await api.exportData();
      const blob = new Blob([JSON.stringify(exported, null, 2)], {
        type: "application/json"
      });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `authorization-data-${new Date()
        .toISOString()
        .replace(/[:.]/g, "-")}.json`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    } catch (caught) {
      setError(message(caught));
    } finally {
      setBusy(false);
    }
  };

  const selectFile = async (file?: File) => {
    setBundle(null);
    setResult(null);
    setAcknowledged(false);
    setError("");
    setFileName(file?.name ?? "");
    if (!file) return;
    if (file.size > 10 * 1024 * 1024) {
      setError("The import file must not exceed 10 MB.");
      return;
    }
    try {
      const parsed = JSON.parse(await file.text()) as AuthorizationDataBundle;
      if (
        parsed.formatVersion !== 1 ||
        !parsed.exportedAt ||
        DATA_SECTIONS.some(({ key }) => !Array.isArray(parsed[key]))
      ) {
        throw new Error("This is not a complete version 1 authorization export.");
      }
      setBundle(parsed);
    } catch (caught) {
      setError(
        caught instanceof Error ? caught.message : "The selected file is not valid JSON."
      );
    }
  };

  const replaceData = async () => {
    if (!bundle || !acknowledged) return;
    if (
      !window.confirm(
        "Replace all current authorization data with this file? This cannot be undone."
      )
    )
      return;
    setBusy(true);
    setError("");
    setResult(null);
    try {
      setResult(await api.replaceData(bundle));
    } catch (caught) {
      setError(message(caught));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="data-transfer-layout">
      <section className="panel">
        <PageHeading
          eyebrow="Portable snapshot"
          title="Export authorization data"
          description="Download all portable configuration, users, assignments, and external mappings as a versioned JSON file."
        />
        <p className="muted">
          Audit events, migration history, caches, synchronization runtime state,
          and authentication credentials are not exported.
        </p>
        <button className="primary" disabled={busy} onClick={exportData}>
          {busy ? "Working..." : "Download full export"}
        </button>
      </section>

      <section className="panel destructive-panel">
        <PageHeading
          eyebrow="Destructive operation"
          title="Replace from export"
          description="Upload a complete export file and replace the current authorization data."
        />
        <div className="destructive-warning" role="alert">
          <strong>This is a full replacement.</strong>
          <p>
            Importing permanently deletes all current roles, permission groups,
            permissions, resource rules, users, assignments, pending assignments,
            and external mappings before recreating them from the file.
          </p>
          <p>
            You may lose administrator access if your current identity and admin
            assignments are not present in the import.
          </p>
        </div>
        {error && <Notice tone="error">{error}</Notice>}
        {result && (
          <Notice tone="info">
            Replacement completed: {result.permissions} permissions,{" "}
            {result.permissionGroups} groups, {result.roles} roles, and{" "}
            {result.users} users imported.
          </Notice>
        )}
        <label className="file-picker">
          <span>Select authorization export</span>
          <input
            aria-label="Authorization export file"
            type="file"
            accept="application/json,.json"
            onChange={(event) => void selectFile(event.target.files?.[0])}
          />
          <small>{fileName || "No file selected"}</small>
        </label>
        {bundle && (
          <>
            <div className="bundle-summary" aria-label="Import file contents">
              <div>
                <small>Format</small>
                <strong>Version {bundle.formatVersion}</strong>
                <span>{new Date(bundle.exportedAt).toLocaleString()}</span>
              </div>
              {DATA_SECTIONS.map(({ key, label }) => (
                <div key={key}>
                  <small>{label}</small>
                  <strong>{bundle[key].length}</strong>
                  <span>records</span>
                </div>
              ))}
            </div>
            <label className="destructive-check">
              <input
                type="checkbox"
                checked={acknowledged}
                onChange={(event) => setAcknowledged(event.target.checked)}
              />
              <span>
                I understand that all current authorization data will be deleted
                and replaced by this file.
              </span>
            </label>
            <button
              className="danger-button"
              disabled={!acknowledged || busy}
              onClick={replaceData}
            >
              {busy ? "Replacing data..." : "Delete current data and import"}
            </button>
          </>
        )}
      </section>
    </div>
  );
}

function AuditPage({ api }: { api: AdminApi }) {
  const [page, setPage] = useState<Page<AuditEvent> | null>(null);
  const [currentPage, setCurrentPage] = useState(0);
  const [eventKind, setEventKind] = useState<"" | AuditEvent["eventKind"]>("");
  const [eventType, setEventType] = useState("");
  const [actor, setActor] = useState("");
  const [target, setTarget] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    api
      .page<AuditEvent>("/audit", {
        eventKind,
        eventType,
        actor,
        target,
        page: currentPage,
        size: 50,
        sort: "timestamp,desc"
      })
      .then(setPage)
      .catch((caught) => setError(message(caught)));
  }, [api, eventKind, eventType, actor, target, currentPage]);

  return (
    <section className="panel">
      <PageHeading
        eyebrow="Evidence"
        title="Audit trail"
        description="Safe, filterable decision, administration, and synchronization events."
      />
      {error && <Notice tone="error">{error}</Notice>}
      <div className="filter-bar">
        <select
          aria-label="Event kind"
          value={eventKind}
          onChange={(event) => {
            setEventKind(event.target.value as "" | AuditEvent["eventKind"]);
            setCurrentPage(0);
          }}
        >
          <option value="">All event kinds</option>
          <option value="DECISION">Decisions</option>
          <option value="CHANGE">Changes</option>
        </select>
        <input
          aria-label="Event type"
          placeholder="Event type"
          value={eventType}
          onChange={(event) => {
            setEventType(event.target.value);
            setCurrentPage(0);
          }}
        />
        <input
          aria-label="Actor"
          placeholder="Actor"
          value={actor}
          onChange={(event) => {
            setActor(event.target.value);
            setCurrentPage(0);
          }}
        />
        <input
          aria-label="Target"
          placeholder="Target"
          value={target}
          onChange={(event) => {
            setTarget(event.target.value);
            setCurrentPage(0);
          }}
        />
      </div>
      <AuditTable events={page?.content ?? []} />
      <div className="pagination" aria-label="Audit pagination">
        <button
          className="quiet"
          disabled={currentPage === 0}
          onClick={() => setCurrentPage((value) => value - 1)}
        >
          Previous
        </button>
        <span>
          Page {page ? page.page + 1 : 1} of {Math.max(page?.totalPages ?? 1, 1)}
          {" / "}
          {page?.totalElements ?? 0} events
        </span>
        <button
          className="quiet"
          disabled={!page || page.page + 1 >= page.totalPages}
          onClick={() => setCurrentPage((value) => value + 1)}
        >
          Next
        </button>
      </div>
    </section>
  );
}

function AuditTable({ events }: { events: AuditEvent[] }) {
  return (
    <div className="table-scroll">
      <table>
        <thead>
          <tr>
            <th>Time</th>
            <th>Kind</th>
            <th>Event</th>
            <th>Actor</th>
            <th>Target</th>
            <th>Result</th>
          </tr>
        </thead>
        <tbody>
          {events.map((event) => (
            <tr key={event.id}>
              <td>{new Date(event.timestamp).toLocaleString()}</td>
              <td>
                <Badge tone={event.eventKind.toLowerCase()}>{event.eventKind}</Badge>
              </td>
              <td><code>{event.eventType}</code></td>
              <td>{event.actorSubject ?? "anonymous"}</td>
              <td>{event.target ?? event.ruleCode ?? "-"}</td>
              <td>
                {event.decision ? (
                  <Badge tone={event.decision.toLowerCase()}>{event.decision}</Badge>
                ) : (
                  event.action ?? "-"
                )}
              </td>
            </tr>
          ))}
          {events.length === 0 && (
            <tr>
              <td colSpan={6} className="empty-cell">No audit events match these filters.</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

function PageHeading({
  eyebrow,
  title,
  description
}: {
  eyebrow: string;
  title: string;
  description: string;
}) {
  return (
    <div className="page-heading">
      <p className="eyebrow">{eyebrow}</p>
      <h2>{title}</h2>
      <p>{description}</p>
    </div>
  );
}

function Search({
  value,
  onChange
}: {
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <label className="search">
      <span aria-hidden="true">Search</span>
      <input
        aria-label="Search"
        placeholder="Search by code or name"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}

function Field({
  label,
  hint,
  children
}: {
  label: string;
  hint?: string;
  children: ReactNode;
}) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
      {hint && <small>{hint}</small>}
    </label>
  );
}

function Check({
  checked,
  onChange,
  label
}: {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label: string;
}) {
  return (
    <label className="check">
      <input
        type="checkbox"
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
      />
      <span>{label}</span>
    </label>
  );
}

function FormActions({
  editing,
  onCancel
}: {
  editing: boolean;
  onCancel: () => void;
}) {
  return (
    <div className="form-actions">
      <button className="primary" type="submit">
        {editing ? "Save changes" : "Create"}
      </button>
      {editing && (
        <button className="quiet" type="button" onClick={onCancel}>
          Cancel
        </button>
      )}
    </div>
  );
}

function Status({ enabled }: { enabled: boolean }) {
  return <span className={`status ${enabled ? "enabled" : "disabled"}`}>{enabled ? "Enabled" : "Disabled"}</span>;
}

function Badge({
  children,
  tone = ""
}: {
  children: ReactNode;
  tone?: string;
}) {
  return <span className={`badge ${tone}`}>{children}</span>;
}

function Notice({
  children,
  tone
}: {
  children: ReactNode;
  tone: "error" | "info";
}) {
  return <div className={`notice ${tone}`}>{children}</div>;
}

function Empty({ label }: { label: string }) {
  return (
    <div className="empty">
      <span aria-hidden="true">A</span>
      <p>{label}</p>
    </div>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div className="detail">
      <small>{label}</small>
      <strong>{value}</strong>
    </div>
  );
}

function codes(value: string): string[] {
  return value
    .split(",")
    .map((code) => code.trim())
    .filter(Boolean);
}

function message(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.errors.length)
      return error.errors.map((item) => `${item.field}: ${item.message}`).join("; ");
    return `${error.code}: ${error.message}`;
  }
  return error instanceof Error ? error.message : "Unexpected request failure";
}
