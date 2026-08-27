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
  createRoot(document.getElementById("root")!).render(
    <StrictMode>
      <App config={config} api={new AdminApi(config.apiBasePath)} />
    </StrictMode>
  );
}

start().catch((error: Error) => {
  document.getElementById("root")!.innerHTML =
    `<main class="startup-error"><h1>Authorization Console</h1><p>${error.message}</p></main>`;
});
