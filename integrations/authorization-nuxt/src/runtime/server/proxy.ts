import {
  createError,
  defineEventHandler,
  getProxyRequestHeaders,
  proxyRequest
} from "h3";

interface ProxyConfig {
  backendBaseUrl: string;
  publicBaseUrl: string;
  apiProxyPrefix: string;
}

export default defineEventHandler(async (event) => {
  const config = useRuntimeConfig(event).authorizationNuxt as ProxyConfig;
  const target = proxyTarget(config, event.path);
  const headers = getProxyRequestHeaders(event);
  delete headers.host;
  delete headers["x-forwarded-host"];
  delete headers["x-forwarded-proto"];
  delete headers["x-forwarded-port"];

  if (config.publicBaseUrl) {
    const publicUrl = validatedBaseUrl(config.publicBaseUrl, "publicBaseUrl");
    headers["x-forwarded-host"] = publicUrl.host;
    headers["x-forwarded-proto"] = publicUrl.protocol.slice(0, -1);
    headers["x-forwarded-port"] = publicUrl.port
      || (publicUrl.protocol === "https:" ? "443" : "80");
  }

  return proxyRequest(event, target, {
    headers,
    fetchOptions: { redirect: "manual" }
  });
});

export function proxyTarget(config: ProxyConfig, requestPath: string): string {
  const backend = validatedBaseUrl(config.backendBaseUrl, "backendBaseUrl");
  let upstreamPath = requestPath;
  if (upstreamPath.startsWith(config.apiProxyPrefix)) {
    upstreamPath = upstreamPath.slice(config.apiProxyPrefix.length) || "/";
  }
  if (!upstreamPath.startsWith("/") || upstreamPath.startsWith("//") || upstreamPath.includes("\\")) {
    throw createError({ statusCode: 400, statusMessage: "Invalid authorization proxy path" });
  }
  const pathname = upstreamPath.split(/[?#]/, 1)[0] ?? upstreamPath;
  try {
    if (pathname.split("/").some((segment) => decodeURIComponent(segment) === "..")) {
      throw new Error("parent traversal");
    }
  } catch {
    throw createError({ statusCode: 400, statusMessage: "Invalid authorization proxy path" });
  }
  return new URL(upstreamPath, backend.origin).toString();
}

function validatedBaseUrl(value: string, name: string): URL {
  if (!value) {
    throw createError({ statusCode: 503, statusMessage: `${name} is not configured` });
  }
  let parsed: URL;
  try {
    parsed = new URL(value);
  } catch {
    throw createError({ statusCode: 503, statusMessage: `${name} is invalid` });
  }
  if (!new Set(["http:", "https:"]).has(parsed.protocol)
    || parsed.username || parsed.password
    || parsed.pathname !== "/" || parsed.search || parsed.hash) {
    throw createError({ statusCode: 503, statusMessage: `${name} is invalid` });
  }
  return parsed;
}
