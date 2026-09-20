import { type ReactNode } from "react";
import { ApiError } from "./api";

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

function message(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.errors.length)
      return error.errors.map((item) => `${item.field}: ${item.message}`).join("; ");
    return `${error.code}: ${error.message}`;
  }
  return error instanceof Error ? error.message : "Unexpected request failure";
}

export { Badge, Check, Detail, Empty, Field, FormActions, Notice, PageHeading, Search, Status, message };
