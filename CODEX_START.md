# Codex Start Prompt

Use this as the first instruction after placing these files at repository root.

---

Read `AGENTS.md`, `README.md`, `TASKS.md`, and every Markdown file under `docs/`.

Before writing code, summarize in at most 30 lines:

1. the three reusable Maven modules
2. the DB-only runtime request flow
3. the lock/key authorization model
4. Spring Security integration
5. persistence/Flyway separation
6. seed processing
7. admin API/UI separation
8. optional Keycloak synchronization
9. how the demo proves functionality

Phases 1-4 are implemented. Execute **Phase 5 only** from `TASKS.md`, preserving the existing
DB-backed core, admin API, packaged SPA, and Keycloak synchronization behavior.

Generate working code and tests. Run `./mvnw clean verify`. Stop after Phase 5 and report:

- files created/changed
- tests run
- test result
- assumptions
- any deviations from the specification

Do not start Phase 6 hardening while implementing Phase 5.
