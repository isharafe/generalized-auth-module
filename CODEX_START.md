# Codex Start Prompt

Use this as the first instruction after placing these files at repository root.

---

Read `AGENTS.md`, `README.md`, `TASKS.md`, and every Markdown file under `docs/`.

Before writing code, summarize in at most 30 lines:

1. the four reusable Maven modules
2. the DB-only runtime request flow
3. the lock/key authorization model
4. Spring Security integration
5. persistence/Flyway separation
6. seed processing
7. admin API/UI separation
8. optional Keycloak and LDAP synchronization
9. event-driven refresh, observability, and multi-instance behavior
10. how the demo proves functionality

Phases 1-7 in `TASKS.md` are implemented. Treat them as the tested baseline. Execute only the
maintenance or feature request supplied with this prompt, preserving existing DB-backed core,
admin API/SPA, Keycloak, LDAP, event-refresh, and production-hardening behavior unless the request
explicitly changes it.

Generate working code and tests. Run `./mvnw clean verify`. Stop after Phase 5 and report:

- files created/changed
- tests run
- test result
- assumptions
- any deviations from the specification

Do not reimplement completed phases or expand the requested scope without approval.
