import type {
  AuthorizationDataBundle,
  AuthorizationDataImportResult,
  Capabilities,
  CurrentUser,
  Page,
  RuntimeConfig
} from "./types";

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly errors: Array<{ field: string; message: string }> = []
  ) {
    super(message);
  }
}

export class AdminApi {
  private refreshInFlight: Promise<boolean> | null = null;

  constructor(
    private readonly basePath: string,
    private readonly security?: RuntimeConfig
  ) {}

  capabilities(): Promise<Capabilities> {
    return this.get<Capabilities>("/capabilities");
  }

  currentUser(): Promise<CurrentUser> {
    return this.get<CurrentUser>("/current-user");
  }

  exportData(): Promise<AuthorizationDataBundle> {
    return this.post<AuthorizationDataBundle>("/data/export");
  }

  replaceData(
    bundle: AuthorizationDataBundle
  ): Promise<AuthorizationDataImportResult> {
    return this.post<AuthorizationDataImportResult>("/data/import", bundle);
  }

  page<T>(
    path: string,
    params: Record<string, string | number | undefined> = {}
  ): Promise<Page<T>> {
    const query = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== "") query.set(key, String(value));
    });
    const suffix = query.size ? `?${query.toString()}` : "";
    return this.get<Page<T>>(path + suffix);
  }

  get<T>(path: string): Promise<T> {
    return this.request<T>(path);
  }

  post<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>(path, {
      method: "POST",
      body: body === undefined ? undefined : JSON.stringify(body)
    });
  }

  put<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>(path, {
      method: "PUT",
      body: body === undefined ? undefined : JSON.stringify(body)
    });
  }

  delete<T>(path: string): Promise<T> {
    return this.request<T>(path, { method: "DELETE" });
  }

  private async request<T>(
    path: string,
    init: RequestInit = {},
    allowRefresh = true
  ): Promise<T> {
    const method = (init.method ?? "GET").toUpperCase();
    const csrfHeaders =
      this.security?.cookieOauth2Enabled &&
      !["GET", "HEAD", "OPTIONS", "TRACE"].includes(method) &&
      this.security.csrfToken &&
      this.security.csrfHeaderName
        ? { [this.security.csrfHeaderName]: this.security.csrfToken }
        : {};
    const response = await fetch(this.basePath + path, {
      ...init,
      credentials: "same-origin",
      headers: {
        Accept: "application/json",
        ...(init.body ? { "Content-Type": "application/json" } : {}),
        ...csrfHeaders,
        ...init.headers
      }
    });
    if (
      response.status === 401 &&
      allowRefresh &&
      this.security?.cookieOauth2Enabled &&
      this.security.refreshEndpoint
    ) {
      if (await this.refreshAuthentication())
        return this.request<T>(path, init, false);
    }
    if (!response.ok) {
      const problem = await response.json().catch(() => ({}));
      throw new ApiError(
        response.status,
        problem.code ?? "AUTHZ_REQUEST_FAILED",
        problem.message ?? `Request failed with HTTP ${response.status}`,
        problem.errors ?? []
      );
    }
    if (response.status === 204) return undefined as T;
    return response.json() as Promise<T>;
  }

  private async refreshAuthentication(): Promise<boolean> {
    if (!this.refreshInFlight) {
      this.refreshInFlight = this.performRefresh().finally(() => {
        this.refreshInFlight = null;
      });
    }
    return this.refreshInFlight;
  }

  private async performRefresh(): Promise<boolean> {
    const security = this.security;
    if (!security?.refreshEndpoint) return false;
    const headers: Record<string, string> = { Accept: "application/json" };
    if (security.csrfHeaderName && security.csrfToken)
      headers[security.csrfHeaderName] = security.csrfToken;
    const response = await fetch(security.refreshEndpoint, {
      method: "POST",
      credentials: "same-origin",
      headers
    });
    if (response.ok) return true;
    if (security.loginUri) window.location.assign(security.loginUri);
    return false;
  }
}
