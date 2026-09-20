import { useEffect, useState } from "react";
import { AdminApi } from "../api";
import {
  Badge,
  Detail,
  Empty,
  Notice,
  PageHeading,
  Search,
  Status,
  message
} from "../components";
import type {
  Assignment,
  EffectiveEntitlements,
  PermissionGroup,
  Role,
  User
} from "../types";

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


export { UsersPage };
