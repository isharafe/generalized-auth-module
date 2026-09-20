import { type FormEvent, useEffect, useState } from "react";
import { AdminApi } from "../api";
import {
  Badge,
  Check,
  Detail,
  Field,
  FormActions,
  Notice,
  PageHeading,
  Search,
  message
} from "../components";
import type {
  AuditEvent,
  AuthorizationDataBundle,
  AuthorizationDataImportResult,
  AuthorizationTestResponse,
  ExternalMapping,
  Page,
  SyncStatus
} from "../types";

function MappingsPage({ api, source }: { api: AdminApi; source: string }) {
  const empty: ExternalMapping = {
    sourceSystem: source === "LDAP" ? "LDAP" : "KEYCLOAK",
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
              <option>ATTRIBUTE</option>
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


export { MappingsPage, ExplainPage, SyncPage, DataTransferPage, AuditPage, AuditTable };
