import { useEffect, useState } from "react";
import { AdminApi } from "../api";
import {
  Notice,
  PageHeading,
  message
} from "../components";
import { AuditTable } from "./OperationsPages";
import type {
  AuditEvent,
  Capabilities,
  Permission,
  PermissionGroup,
  ResourceRule,
  Role,
  User
} from "../types";

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

export { Dashboard };
