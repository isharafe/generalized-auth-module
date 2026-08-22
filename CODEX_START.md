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

Then execute **Phase 1 only** from `TASKS.md`.

Generate working code and tests. Run `mvn clean verify`. Stop after Phase 1 and report:

- files created/changed
- tests run
- test result
- assumptions
- any deviations from the specification

Do not start Keycloak or UI implementation during Phase 1 except for creating their Maven module descriptors if required by the parent build.
