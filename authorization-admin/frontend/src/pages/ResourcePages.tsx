import { type FormEvent, useEffect, useState } from "react";
import { AdminApi } from "../api";
import {
  Badge,
  Check,
  Empty,
  Field,
  FormActions,
  Notice,
  PageHeading,
  Search,
  Status,
  message
} from "../components";
import type {
  Page,
  ResourceRule,
  UrlCoverageStatus,
  UrlEnforcementSource,
  UrlResourceInventoryItem,
  UrlResourceOrigin
} from "../types";

type RuleDraft = {
  code: string;
  resourceType: "URL" | "UI";
  method: string;
  resource: string;
  accessMode: ResourceRule["accessMode"];
  priority: number;
  enabled: boolean;
  version?: number;
};

const RESOURCE_WORKSPACES: Array<{
  type: ResourceRule["resourceType"];
  label: string;
  inventory: boolean;
}> = [
  { type: "URL", label: "URLs", inventory: true },
  { type: "UI", label: "UI resources", inventory: false }
];

function ResourcesPage({ api }: { api: AdminApi }) {
  const [resourceType, setResourceType] = useState<"URL" | "UI">("URL");
  const [view, setView] = useState<"coverage" | "rules">("coverage");
  const [prefill, setPrefill] = useState<RuleDraft | null>(null);

  const selectType = (type: "URL" | "UI") => {
    setResourceType(type);
    setView(type === "URL" ? "coverage" : "rules");
    setPrefill(null);
  };

  const createRule = (resource: UrlResourceInventoryItem) => {
    setResourceType("URL");
    setPrefill({
      code: "",
      resourceType: "URL",
      method: resource.method,
      resource: resource.path,
      accessMode: "AUTHORIZED",
      priority: 0,
      enabled: true
    });
    setView("rules");
  };

  return (
    <>
      <section className="resource-workspace-heading">
        <PageHeading
          eyebrow="Protected resources"
          title="Resources"
          description="Compare resources exposed by the application with the rules that govern them."
        />
        <div className="resource-tabs" aria-label="Resource type">
          {RESOURCE_WORKSPACES.map((workspace) => (
            <button
              className={resourceType === workspace.type ? "active" : ""}
              key={workspace.type}
              onClick={() => selectType(workspace.type)}
            >
              {workspace.label}
            </button>
          ))}
        </div>
        <div className="resource-tabs secondary" aria-label="Resource view">
          {resourceType === "URL" && (
            <button
              className={view === "coverage" ? "active" : ""}
              onClick={() => setView("coverage")}
            >
              Coverage
            </button>
          )}
          <button
            className={view === "rules" ? "active" : ""}
            onClick={() => setView("rules")}
          >
            Rules
          </button>
        </div>
      </section>
      {resourceType === "URL" && view === "coverage" ? (
        <UrlCoveragePage api={api} onCreateRule={createRule} />
      ) : (
        <RulesPage
          api={api}
          resourceType={resourceType}
          prefill={prefill}
          onPrefillConsumed={() => setPrefill(null)}
        />
      )}
    </>
  );
}

function coverageLabel(item: UrlResourceInventoryItem): string {
  if (item.enforcementSource === "UNKNOWN") return "Filter-chain policy unknown";
  if (item.coverageStatus === "UNMATCHED") return "Default denied";
  if (item.coverageStatus === "INDETERMINATE") return "Configuration conflict";
  switch (item.accessMode) {
    case "PERMIT_ALL": return "Public";
    case "AUTHENTICATED": return "Sign-in required";
    case "AUTHORIZED": return "Permission required";
    case "DENY_ALL": return "Explicitly denied";
    default: return "Rule matched";
  }
}

function UrlCoveragePage({
  api,
  onCreateRule
}: {
  api: AdminApi;
  onCreateRule: (resource: UrlResourceInventoryItem) => void;
}) {
  const [items, setItems] = useState<UrlResourceInventoryItem[]>([]);
  const [search, setSearch] = useState("");
  const [coverage, setCoverage] = useState<"" | UrlCoverageStatus>("");
  const [accessMode, setAccessMode] = useState<"" | ResourceRule["accessMode"]>("");
  const [origin, setOrigin] = useState<"" | UrlResourceOrigin>("");
  const [enforcementSource, setEnforcementSource] = useState<"" | UrlEnforcementSource>("");
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<UrlResourceInventoryItem> | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    setPage(0);
  }, [search, coverage, accessMode, origin, enforcementSource]);

  useEffect(() => {
    api.page<UrlResourceInventoryItem>("/resource-inventory/urls", {
      search,
      coverage,
      accessMode,
      origin,
      enforcementSource,
      page,
      size: 25,
      sort: "path,asc"
    })
      .then((loaded) => {
        setResult(loaded);
        setItems(loaded.content);
        setError("");
      })
      .catch((caught) => setError(message(caught)));
  }, [api, search, coverage, accessMode, origin, enforcementSource, page]);

  return (
    <section className="panel coverage-panel">
      <PageHeading
        eyebrow="Live MVC inventory"
        title="URL coverage"
        description="Effective access combines declared Spring Security policies with resource rules for each controller method and path mapping."
      />
      {error && <Notice tone="error">{error}</Notice>}
      <div className="coverage-summary" aria-label="URL coverage summary">
        <article><small>Matching routes</small><strong>{result?.totalElements ?? 0}</strong></article>
        <article><small>Policies matched on this page</small><strong>{items.filter((item) => item.coverageStatus === "MATCHED").length}</strong></article>
        <article><small>Default denied on this page</small><strong>{items.filter((item) => item.coverageStatus === "UNMATCHED").length}</strong></article>
      </div>
      <div className="coverage-filters">
        <Search value={search} onChange={setSearch} />
        <select aria-label="Coverage" value={coverage} onChange={(event) => setCoverage(event.target.value as "" | UrlCoverageStatus)}>
          <option value="">All coverage</option>
          <option value="MATCHED">Policy matched</option>
          <option value="UNMATCHED">No matching resource rule</option>
          <option value="INDETERMINATE">Indeterminate</option>
        </select>
        <select aria-label="Access mode" value={accessMode} onChange={(event) => setAccessMode(event.target.value as "" | ResourceRule["accessMode"])}>
          <option value="">All access modes</option>
          {(["PERMIT_ALL", "AUTHENTICATED", "AUTHORIZED", "DENY_ALL"] as const).map((mode) => <option key={mode}>{mode}</option>)}
        </select>
        <select aria-label="Origin" value={origin} onChange={(event) => setOrigin(event.target.value as "" | UrlResourceOrigin)}>
          <option value="">All origins</option>
          <option value="APPLICATION">Application</option>
          <option value="AUTHORIZATION_FRAMEWORK">Authorization framework</option>
          <option value="SPRING_INFRASTRUCTURE">Spring infrastructure</option>
        </select>
        <select aria-label="Enforced by" value={enforcementSource} onChange={(event) => setEnforcementSource(event.target.value as "" | UrlEnforcementSource)}>
          <option value="">All enforcement sources</option>
          <option value="SECURITY_FILTER_CHAIN">Security filter chain</option>
          <option value="RESOURCE_RULE">Resource rules</option>
          <option value="UNKNOWN">Unknown</option>
        </select>
      </div>
      <div className="table-scroll">
        <table className="coverage-table">
          <thead><tr><th>Method</th><th>Path</th><th>Effective access</th><th>Enforced by</th><th>Policy / rule</th><th>Origin</th><th /></tr></thead>
          <tbody>
            {items.map((item) => (
              <tr key={item.pattern}>
                <td><Badge>{item.method}</Badge></td>
                <td><code>{item.path}</code><small title={item.handlers.join("\n")}>{item.handlers[0]}</small></td>
                <td><Badge tone={item.coverageStatus === "MATCHED" ? "manual" : item.coverageStatus === "UNMATCHED" ? "seed" : "identity_sync"}>{coverageLabel(item)}</Badge></td>
                <td><small>{item.enforcementSource.replaceAll("_", " ")}</small></td>
                <td>{item.matchedRule ? <><code>{item.matchedRule}</code><small>Priority {item.priority}</small></> : item.matchedSecurityPolicy ? <code>{item.matchedSecurityPolicy}</code> : <span className="muted">No policy metadata</span>}</td>
                <td>{item.origins.map((value) => <small key={value}>{value.replaceAll("_", " ")}</small>)}</td>
                <td>{item.coverageStatus === "UNMATCHED" && <button className="quiet" onClick={() => onCreateRule(item)}>Create rule</button>}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {items.length === 0 && !error && <Empty label="No URL resources match these filters" />}
      </div>
      {result && result.totalPages > 1 && (
        <div className="pagination">
          <button className="quiet" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>Previous</button>
          <span>Page {page + 1} of {result.totalPages}</span>
          <button className="quiet" disabled={page + 1 >= result.totalPages} onClick={() => setPage((value) => value + 1)}>Next</button>
        </div>
      )}
    </section>
  );
}

function RulesPage({
  api,
  resourceType,
  prefill,
  onPrefillConsumed
}: {
  api: AdminApi;
  resourceType: "URL" | "UI";
  prefill: RuleDraft | null;
  onPrefillConsumed: () => void;
}) {
  const empty: RuleDraft = {
    code: "",
    resourceType,
    method: "*",
    resource: resourceType === "URL" ? "/api/**" : "componentIdentifier",
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
    setDraft(empty);
  }, [resourceType]);

  useEffect(() => {
    if (!prefill) return;
    setDraft(prefill);
    onPrefillConsumed();
  }, [prefill]);

  useEffect(() => {
    api
      .page<ResourceRule>("/resource-rules", {
        search,
        resourceType,
        size: 100,
        sort: "code,asc"
      })
      .then((page) => setItems(page.content))
      .catch((caught) => setError(message(caught)));
  }, [api, search, reload, resourceType]);

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
          eyebrow={`${resourceType} locks`}
          title={`${resourceType} resource rules`}
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
            <input value={draft.resourceType} disabled />
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


export { ResourcesPage };
