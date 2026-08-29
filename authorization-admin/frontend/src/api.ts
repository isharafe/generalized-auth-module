import type {
  AuthorizationDataBundle,
  AuthorizationDataImportResult,
  Capabilities,
  CurrentUser,
  Page
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
  constructor(private readonly basePath: string) {}

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

  private async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const response = await fetch(this.basePath + path, {
      ...init,
      credentials: "same-origin",
      headers: {
        Accept: "application/json",
        ...(init.body ? { "Content-Type": "application/json" } : {}),
        ...init.headers
      }
    });
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
}
