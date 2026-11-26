# Architecture

- **Ticket:** 1 — "its extension points are documented"
- **Describes:** `main` at `ac256a7`, after ADR [0001](decisions/0001-kotlin-for-new-code.md), [0002](decisions/0002-environment-profiles.md) and [0003](decisions/0003-layered-packages.md)
- **See also:** [inspection.md](inspection.md) — the state before those ADRs; project rules in `CLAUDE.md` (local)

## 1. Runtime

```
Browser ──HTTP :80──▶ NGINX ──▶ app :8080 (Spring Boot) ──JDBC──▶ PostgreSQL 17
                                  ▲
                    @Scheduled jobs run inside the app
```

- Docker Compose project `mandujoa`: `mandujoa_nginx` (the only published port), `mandujoa_app`, `mandujoa_postgres` (volume `mandujoa_postgres-data`). All use `restart: unless-stopped`.
- NGINX forwards every path for `mandujoa.local` and `localhost` to `app:8080`.
- Local runs use Docker Compose only (`docker compose up -d --build`). Postgres has no host port, so running the app directly on the host is not supported (ADR 0002).
- The schema is owned by Flyway (`src/main/resources/db/migration`, V1–V5). Hibernate only validates (`ddl-auto=validate`); the app does not start if entities and schema differ.

## 2. Layers

Top-level packages under `com.example.helloworld` are layers; inside each, sub-packages are by feature (ADR 0003).

| Package | Holds | Today |
|---|---|---|
| `api` | HTTP controllers: request and response only | `HelloController`, `status.DatabaseStatusController` |
| `application` | Use cases, business logic, transactions | `retention.RetentionService`, `status.DatabaseStatusService`, `status.DatabaseStatus` |
| `persistence` | Entities, repositories, SQL | `BaseEntity`; `audit`, `integration`, `organization`, `review` (incl. `ProviderRecordUpserter`), `sync` |
| `provider` | External integrations behind interfaces | empty (planned) |
| `job` | `@Scheduled` entry points | `RetentionScheduler` |
| `config` | Configuration properties and startup checks | `RetentionProperties`, `ProfileSafetyCheck` |

```
api ──▶ application ──▶ persistence
job ──▶ application ──▶ provider (interfaces)
config may be used by any layer
```

`ArchitectureTest.kt` (ArchUnit) enforces these rules on main code (tests are excluded): `api` and `job` are not used by anyone, `application` only by `api` and `job`, `persistence` and `provider` only by `application`. A wrong import fails the build. `provider` is an optional layer: the rule applies as soon as its first class exists.

## 3. Profiles and configuration

| Setting | `dev` | `staging` | `prod` |
|---|---|---|---|
| Database URL and user | in `application-dev.properties` | environment, required | environment, required |
| Database password | `noa` | environment, required, must not be `noa` | environment, required, must not be `noa` |
| Status screen (`app.status.enabled`) | on | off; `APP_STATUS_ENABLED=true` turns it on | off; turning it on is refused |
| Retention job (`app.retention.enabled`) | off | on | on |
| Log level (`com.example.helloworld`) | DEBUG | INFO | INFO |

- `application.properties` holds shared settings and `spring.profiles.default=dev`. `compose.yaml` sets `SPRING_PROFILES_ACTIVE=dev`.
- **`ProfileSafetyCheck`** (Kotlin, registered in `META-INF/spring.factories`) runs before any bean or database connection. In `staging`/`prod` it refuses a missing URL, user or password and the dev password. In `prod` it also refuses `app.status.enabled` true (including when missing) and `app.provider.mode=mock`.
- **Feature switches default to ON when missing** (`@ConditionalOnProperty(..., matchIfMissing = true)`).
- Environment variables:

| Variable | Where |
|---|---|
| `SPRING_PROFILES_ACTIVE` | every server |
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` | staging, prod |
| `SPRING_DATASOURCE_PASSWORD` or `DB_PASSWORD` | staging, prod |
| `APP_STATUS_ENABLED` | staging only |
| `POSTGRES_PASSWORD`, `NGINX_PORT` | `compose.yaml` only |

## 4. Languages

- New code, new entities and new tests are Kotlin. Existing Java stays Java; a package moves to Kotlin only for a confirmed `null` problem, with its own ADR (ADR 0001).
- `src/main/kotlin` and `src/main/java` compile together (Kotlin 1.9.25, as managed by Spring Boot 3.5.16; `spring`, `jpa` and `all-open` compiler plugins).
- Nullable getters on Java entities carry Spring's `@Nullable`. `EntityNullabilityTest` compares them with the database columns.

## 5. Project rules every change follows

From `CLAUDE.md`:

- **Audit:** user actions are written to `audit_events` with `AuditEvent.byUser(...)` or `AuditEvent.bySystem(...)` (`persistence.audit`). The table is append-only.
- **Failures:** recorded in the database (for example `sync_errors`) and shown on a screen, not only logged.
- **AI drafts** are published only after a person approves them (`response_drafts`).
- **Secrets** never go into code or Git; they come from environment variables or a secret store (`connected_accounts.credential_ref` holds a reference only).
- **Visible result:** a feature is done when its result can be seen on a screen.

## 6. Extension points

### Add a provider integration (planned)

1. Add a row to `integration_providers` with a new migration (`V<n>__….sql`), including `content_retention_days` and `disconnected_purge_days` (both used by retention and the status screen).
2. Put the integration in `provider/<name>`, behind an interface the `application` layer uses. Only `application` may depend on `provider`.
3. The provider returns plain data (no entities). The `application` service maps it and writes it through `ProviderRecordUpserter`, so deduplication on provider IDs keeps working. `provider` must not use `persistence`.
4. Credentials: store only a reference in `connected_accounts.credential_ref`; tokens come from the secret store.
5. Mock vs real data: a setting such as `app.provider.mode=mock|live` is **planned, not implemented** — only the prod refusal of `mock` exists in `ProfileSafetyCheck`. Mock runs locally in `dev`; staging and prod use real providers.
6. Record sync failures in `sync_errors` and show them on a screen.

### Add a scheduled job

1. Put the `@Scheduled` class in `job/`. It only calls an `application` service.
2. Give it a switch `app.<job>.enabled` and set it **explicitly in all three profile files** (a missing switch means ON).
3. If two instances must not run it at once, take a PostgreSQL advisory lock inside the application service's transaction, as `RetentionService` does (`pg_try_advisory_xact_lock` with a constant key). The job class stays a thin trigger.
4. Record the outcome and failures in the database and show them on a screen. Do not copy retention's current outcome handling: it keeps the last run in memory only ([inspection](inspection.md), risk 1).

### Add an endpoint

1. Put the controller in `api/<feature>`. It calls `application` services only, never repositories.
2. Record user actions with `AuditEvent.byUser(...)`.
3. No authentication exists yet; anything sensitive must stay off in staging and prod until the login ticket.

### Add a table or column

1. Add a new migration `V<n>__….sql`. Never edit an applied migration.
2. Add or change the entity in `persistence/<feature>`. New entities are Kotlin (ADR 0001); changes to existing Java entities stay Java.
3. Java entity: mark nullable getters with Spring's `@Nullable`; `EntityNullabilityTest` checks them.
4. **Known gap:** `EntityNullabilityTest` does not support Kotlin entities yet. Kotlin nullable types carry `org.jetbrains.annotations.Nullable`, which reflection cannot see. With the first Kotlin entity, extend the test to use `kotlin-reflect` (`returnType.isMarkedNullable`) for Kotlin classes.
5. Hibernate `validate` must still pass at startup.

### Add a setting

1. Shared value → `application.properties`. Environment-specific value → all three profile files.
2. Secrets → environment variables only.
3. A value that matters for safety → assert it in `ProfilePropertiesTest`. A value prod must forbid → add it to `ProfileSafetyCheck`.

### Add a screen

- Server-rendered pages: Thymeleaf templates in `src/main/resources/templates`, returned by a controller in `api/` (today `/` from `HelloController`).
- The status screen is React + TypeScript in `frontend/`, built by Maven into the jar at `/status/`. It must be removed or put behind login before public release.

### Add a test

- New tests are Kotlin in `src/test/kotlin`.
- Tests that need the database use `@Import(PostgresTestConfiguration::class)`: a real PostgreSQL 17 through Testcontainers, so Docker must be running. `PostgresTestConfiguration` is a Java class in the root test package; Kotlin tests import `com.example.helloworld.PostgresTestConfiguration`, as `EntityNullabilityTest` does.

## 7. Checks before merging

`.\mvnw.cmd clean test` passes with no new compiler warnings, then `docker compose up -d --build` starts the app, Flyway and Hibernate `validate` succeed, and `/status/` and `/api/status/database` respond (ADR 0001 final check). The only known build warning is an HTTP 401 for Flyway's own GitHub package repository, which is harmless.
