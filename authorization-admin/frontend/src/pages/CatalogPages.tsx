import { type FormEvent, useEffect, useId, useState } from "react";
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
  Permission
} from "../types";

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
    relations: [] as string[],
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
      relations: item[relationKey] ?? [],
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
      [relationKey]: draft.relations,
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
          <RelationshipPicker
            key={
              draft.version === undefined
                ? "new"
                : `${draft.code}:${draft.version}`
            }
            api={api}
            endpoint={
              relationKey === "permissionGroups"
                ? "/permission-groups"
                : "/permissions"
            }
            label={relationLabel}
            value={draft.relations}
            onChange={(relations) => setDraft({ ...draft, relations })}
          />
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

function RelationshipPicker({
  api,
  endpoint,
  label,
  value,
  onChange
}: {
  api: AdminApi;
  endpoint: "/permission-groups" | "/permissions";
  label: string;
  value: string[];
  onChange: (value: string[]) => void;
}) {
  const inputId = useId();
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<CatalogItem[]>([]);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [lookupError, setLookupError] = useState("");

  useEffect(() => {
    if (!open) {
      setLoading(false);
      return;
    }

    let cancelled = false;
    setLoading(true);
    setLookupError("");
    const timeout = window.setTimeout(() => {
      api
        .page<CatalogItem>(endpoint, {
          search: query.trim(),
          size: 20,
          sort: "code,asc"
        })
        .then((page) => {
          if (!cancelled) setResults(page.content);
        })
        .catch((caught) => {
          if (!cancelled) {
            setResults([]);
            setLookupError(message(caught));
          }
        })
        .finally(() => {
          if (!cancelled) setLoading(false);
        });
    }, 250);

    return () => {
      cancelled = true;
      window.clearTimeout(timeout);
    };
  }, [api, endpoint, open, query]);

  const add = (item: CatalogItem) => {
    if (!item.enabled || value.includes(item.code)) return;
    onChange([...value, item.code]);
    setQuery("");
    setOpen(false);
  };

  const remove = (code: string) =>
    onChange(value.filter((selected) => selected !== code));

  return (
    <div className="field relationship-picker">
      <label htmlFor={inputId}>{label}</label>
      <div
        className="relationship-search"
        onBlur={(event) => {
          if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false);
        }}
      >
        <input
          id={inputId}
          autoComplete="off"
          placeholder={`Search existing ${label.toLowerCase()}`}
          value={query}
          onFocus={() => setOpen(true)}
          onChange={(event) => {
            setQuery(event.target.value);
            setOpen(true);
          }}
          onKeyDown={(event) => {
            if (event.key === "Escape") setOpen(false);
          }}
        />
        {open && (
          <div className="relationship-results">
            {loading && <p role="status">Searching...</p>}
            {!loading && lookupError && (
              <p className="relationship-error" role="alert">{lookupError}</p>
            )}
            {!loading && !lookupError && results.length === 0 && (
              <p>No matching {label.toLowerCase()}.</p>
            )}
            {!loading && !lookupError && results.length > 0 && (
              <ul>
                {results.map((item) => {
                  const selected = value.includes(item.code);
                  return (
                    <li key={item.code}>
                      <button
                        type="button"
                        disabled={!item.enabled || selected}
                        onClick={() => add(item)}
                        aria-label={`Add ${item.code} ${item.name}`}
                      >
                        <span>
                          <code>{item.code}</code>
                          <strong>{item.name}</strong>
                        </span>
                        <span className={`status ${item.enabled ? "enabled" : "disabled"}`}>
                          {selected ? "Selected" : item.enabled ? "Add" : "Disabled"}
                        </span>
                      </button>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        )}
      </div>
      <div className="relationship-chips" aria-label={`Selected ${label.toLowerCase()}`}>
        {value.map((code) => (
          <span className="assignment-chip" key={code}>
            {code}
            <button
              type="button"
              aria-label={`Remove ${code}`}
              onClick={() => remove(code)}
            >
              x
            </button>
          </span>
        ))}
        {value.length === 0 && (
          <span className="relationship-empty">No {label.toLowerCase()} selected.</span>
        )}
      </div>
      <small>
        Search by code or name. Disabled items remain visible but cannot be added.
      </small>
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
  const permissionCode =
    draft.version === undefined
      ? `${draft.resourceType}:${draft.code}`
      : draft.code;

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
      code: permissionCode,
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
          <Field label={draft.version === undefined ? "Local code" : "Code"}>
            <input
              required
              disabled={draft.version !== undefined}
              pattern="[A-Za-z0-9][A-Za-z0-9_.-]*"
              maxLength={100 - draft.resourceType.length - 1}
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
              disabled={draft.version !== undefined}
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
            <small>Canonical code</small>
            <code>{permissionCode}</code>
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

export { CodedCatalogPage, PermissionsPage };
