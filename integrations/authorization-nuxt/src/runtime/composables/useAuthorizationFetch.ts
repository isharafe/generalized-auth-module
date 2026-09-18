import type { FetchOptions } from "ofetch";

export interface UseAuthorizationFetchOptions {
  key?: string;
  request?: FetchOptions;
  lazy?: boolean;
  server?: boolean;
}

export function useAuthorizationFetch<T>(
  path: string,
  options: UseAuthorizationFetchOptions = {}
) {
  const authorization = useAuthorization();
  return useAsyncData<T>(
    options.key ?? `authorization-fetch:${path}`,
    () => authorization.request<T>(path, options.request),
    { lazy: options.lazy, server: options.server }
  );
}
