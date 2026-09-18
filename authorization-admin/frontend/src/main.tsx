import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { AdminApi } from "./api";
import { App } from "./App";
import type { RuntimeConfig } from "./types";
import "./styles.css";

async function start() {
  const response = await fetch("./config", {
    credentials: "same-origin",
    headers: { Accept: "application/json" }
  });
  if (!response.ok) throw new Error("Unable to load authorization UI configuration");
  const config = (await response.json()) as RuntimeConfig;
  if (config.cookieOauth2Enabled && config.csrfEndpoint) {
    const csrfResponse = await fetch(config.csrfEndpoint, {
      credentials: "same-origin",
      headers: { Accept: "application/json" }
    });
    if (!csrfResponse.ok) throw new Error("Unable to initialize CSRF protection");
    const csrf = (await csrfResponse.json()) as {
      token: string;
      headerName: string;
      parameterName: string;
    };
    config.csrfToken = csrf.token;
    config.csrfHeaderName = csrf.headerName;
    config.csrfParameterName = csrf.parameterName;
  }
  createRoot(document.getElementById("root")!).render(
    <StrictMode>
      <App config={config} api={new AdminApi(config.apiBasePath, config)} />
    </StrictMode>
  );
}

start().catch((error: Error) => {
  document.getElementById("root")!.innerHTML =
    `<main class="startup-error"><h1>Authorization Console</h1><p>${error.message}</p></main>`;
});
