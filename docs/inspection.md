# Repository inspection

- **Ticket:** 1 — "Inspect the existing repository, services, Docker configuration, Maven dependencies, and NGINX routing"
- **Date:** 2026-11-27
- **Inspected state:** commit `a038528` on `main`. The Kotlin build setup (ADR [0001](decisions/0001-kotlin-for-new-code.md)) was in progress in `pom.xml` and is not described here.

The purpose of this document: before changing anything, know where everything is, so a change does not break something by accident.

## 1. Docker Compose services

Compose project `mandujoa` ([compose.yaml](../compose.yaml)).

| Service | Container | Image / build | Ports | Volumes | Restart | Health |
|---|---|---|---|---|---|---|
| `postgres` | `mandujoa_postgres` | `postgres:17-alpine` | `expose 5432` (private) | `mandujoa_postgres-data` → `/var/lib/postgresql/data` | `unless-stopped` | `pg_isready` every 5 s |
| `app` | `mandujoa_app` | `build: .` ([Dockerfile](../Dockerfile)) | `expose 8080` (private) | — | `unless-stopped` | none |
| `nginx` | `mandujoa_nginx` | `nginx:alpine` | **`${NGINX_PORT:-80}:80`** (the only published port) | `./nginx/default.conf` (read-only) | `unless-stopped` | none |

- Start order: `app` waits for `postgres` to be healthy; `nginx` waits for `app` to start (not to be ready).
- Environment for `app`: `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/mandujoa`, user `mandujoa`, password `${POSTGRES_PASSWORD:-noa}`, `SERVER_ADDRESS=0.0.0.0`, `SERVER_PORT=8080`.

## 2. Dockerfile stages

| Stage | Base image | What it does |
|---|---|---|
| `build` | `maven:3.9-eclipse-temurin-17` | Copies `pom.xml` and downloads dependencies (cached layer), then copies `frontend/` and `src/` and runs `mvn package -DskipTests`. This also builds the React status screen. |
| runtime | `eclipse-temurin:17-jre` | Copies `target/helloworld-0.0.1-SNAPSHOT.jar` as `app.jar`, exposes 8080, runs `java -jar /app/app.jar`. |

`.dockerignore` excludes `.git`, `.mvn`, `target`, logs, `frontend/node_modules` and `frontend/dist`.

## 3. Maven dependencies

Parent: `spring-boot-starter-parent` **3.5.16**, Java **17**. Artifact `com.example:helloworld:0.0.1-SNAPSHOT`.

| Dependency | Scope | Purpose |
|---|---|---|
| `spring-boot-starter-web` | compile | Spring MVC, embedded Tomcat, Jackson (HTTP endpoints and JSON) |
| `spring-boot-starter-thymeleaf` | compile | Renders `templates/index.html` for `/` |
| `spring-boot-starter-data-jpa` | compile | Hibernate, Spring Data repositories, transactions, HikariCP |
| `spring-boot-starter-validation` | compile | Bean Validation (`@Min` on retention properties) |
| `flyway-core` | compile | Runs versioned SQL migrations at startup |
| `flyway-database-postgresql` | compile | Flyway support for PostgreSQL |
| `postgresql` | runtime | JDBC driver |
| `spring-boot-starter-test` | test | JUnit 5, AssertJ, MockMvc, Spring test support |
| `spring-boot-testcontainers` | test | `@ServiceConnection` for containers in tests |
| `testcontainers:junit-jupiter` | test | Testcontainers JUnit 5 integration |
| `testcontainers:postgresql` | test | Real PostgreSQL 17 container for tests |

| Plugin | Purpose |
|---|---|
| `spring-boot-maven-plugin` | Packages the executable jar |
| `frontend-maven-plugin` 1.15.1 | Downloads Node v22.12.0 into `target/`, runs `npm ci` and `npm run build` for `frontend/` |

## 4. NGINX routing

[nginx/default.conf](../nginx/default.conf) has one server block.

| `listen` | `server_name` | Location | Upstream | Headers set |
|---|---|---|---|---|
| 80 | `mandujoa.local`, `localhost` | `/` | `http://app:8080` | `Host`, `X-Real-IP`, `X-Forwarded-For`, `X-Forwarded-Proto` |

- No `default_server` rule: as the only block, it also answers requests for **any other host name**.
- No HTTPS. `mandujoa.local` resolves only through the local hosts file.

## 5. HTTP endpoints

| Method | Path | Handler | Does | Enabled by |
|---|---|---|---|---|
| GET | `/` | `HelloController.home` | Renders the Thymeleaf `index.html` placeholder page | always |
| GET | `/status`, `/status/` | `DatabaseStatusController.screen` | Forwards to the bundled React status screen | `app.status.enabled` (default true) |
| GET | `/api/status/database` | `DatabaseStatusController.database` | JSON health snapshot: connection, pool, migrations, tables, retention, sync. `503` when the DB is down, `Cache-Control: no-store` | `app.status.enabled` |
| GET | `/status/assets/**` | static resources | React build output | — |

No authentication on any endpoint. No write endpoints yet.

## 6. Scheduled jobs

| Job | Schedule | Does | Enabled by |
|---|---|---|---|
| `RetentionScheduler.runRetention` → `RetentionService.run` | `app.retention.cron`, default `0 30 3 * * *` (daily 03:30, server time zone) | One transaction, guarded by a PostgreSQL advisory lock: clears expired provider content in `reviews`, `interactions`, `business_locations`; deletes old `sync_errors` (90 d), `sync_runs` (180 d), `audit_events` (730 d) | `app.retention.enabled` (default true) |

## 7. Configuration (`application.properties`)

| Key | Value | Note |
|---|---|---|
| `spring.application.name` | `helloworld` | |
| `server.address` / `server.port` | `localhost` / `8080` | Compose overrides the address to `0.0.0.0` |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/mandujoa` | Compose overrides the host to `postgres` |
| `spring.datasource.username` / `password` | `mandujoa` / `noa` | Password committed to Git |
| `spring.flyway.enabled` / `locations` | `true` / `classpath:db/migration` | V1–V5 |
| `spring.jpa.hibernate.ddl-auto` | `validate` | App refuses to start if entities and schema differ |
| `spring.jpa.open-in-view` | `false` | |
| `spring.jpa.properties.hibernate.jdbc.time_zone` | `UTC` | |
| `app.retention.enabled` / `cron` | `true` / `0 30 3 * * *` | |
| `app.retention.sync-run-days` / `sync-error-days` / `audit-event-days` | `180` / `90` / `730` | |
| `app.status.enabled` | `true` | Comment says to disable before public exposure |

There are **no profiles**: the same values apply in every environment.

## 8. Frontend

- React 18.3.1 + TypeScript 5.6.3, built with Vite 5.4.11, in `frontend/`.
- Built by Maven (section 3) into `target/classes/static/status`, so it ships **inside the jar** and is served at `/status/`. There is no separate frontend server.
- `npm run dev` runs Vite with a proxy from `/api` to `localhost:8080` for local frontend work.
- The page polls `/api/status/database` every 15 s.

## 9. Code and data layout

- Packages under `com.example.helloworld` are split **by feature**: `organization`, `integration`, `review`, `sync`, `audit`, `retention`, `status`, `persistence`. Each package holds its entities, repositories and services together. There is no separate layer for controllers, application services, persistence, provider integrations or jobs yet.
- Database: PostgreSQL 17, Flyway migrations V1–V5, 16 tables including `flyway_schema_history`.
- Tests: 3 classes, 11 tests (`PersistenceIntegrationTest` 9, `DatabaseStatusApiTest` 2), all against a real PostgreSQL via Testcontainers. Docker must be running.

## 10. Extension points

| To add… | Where | Notes |
|---|---|---|
| A table or column | New `V<n>__*.sql` in `db/migration` + entity | Never edit an applied migration; `validate` must still pass |
| A provider (Google, mock, …) | New row in `integration_providers` (migration); code via the provider interface (planned) | Records are written through `ProviderRecordUpserter` to keep deduplication |
| A scheduled job | New `@Scheduled` bean with an `app.<job>.enabled` switch | Follow the retention pattern: advisory lock, record the outcome |
| An endpoint | New `@Controller` / `@RestController` | NGINX forwards every path to the app; no auth yet |
| A screen | `frontend/` (status screen) or a new frontend | Built into the jar by Maven |
| A setting | `application.properties` + an env var override in `compose.yaml` | No profiles yet |
| Kotlin code | `src/main/kotlin` once the build setup lands | Rules in ADR 0001 |

## 11. Risks

1. **Retention outcomes live only in memory.** `RetentionService` keeps its last result and error in a field. A restart erases them, so a failed run can go unnoticed. This breaks the project rule that failures are recorded in the database and shown on screen.
2. **The status screen and API are public with no login.** They expose table names, sizes, versions and errors. `app.status.enabled` is `true` by default, in every environment.
3. **A database password is committed.** `noa` is in `application.properties` and is the fallback in `compose.yaml`. Fine for a laptop, unsafe if the same files are deployed.
4. **No environment profiles.** Development values (password, status screen on, `server.address=localhost`) are the defaults everywhere. Staging and production would need to override each one by hand.
5. **NGINX answers every host name.** Without a `default_server` that rejects unknown hosts, any name pointed at the server reaches the app. There is also no HTTPS yet.
6. **No `.gitattributes`.** Files are checked out with CRLF on Windows. Shell scripts such as `mvnw` can fail on a Linux server (`/bin/sh^M`).
7. **Retention runs in one transaction.** Fine with today's empty tables, but with many rows it holds locks for a long time and a late failure rolls everything back. It should move to batches before real data grows.
8. **The 30-day provider retention is a placeholder.** It must be checked against each provider's published policy before public release.
9. **Names are out of sync.** The Maven artifact and app name are still `helloworld`, and the Dockerfile hard-codes `helloworld-0.0.1-SNAPSHOT.jar`. Renaming the artifact without updating the Dockerfile breaks the image build.
10. **The `app` container has no health check.** `nginx` starts as soon as `app` starts, so the first requests can fail while Spring Boot boots.

## 12. Working functionality check

Ticket 1 requires that existing services keep working.

| Check | Result |
|---|---|
| Stack running (`docker compose ls`) | 3 containers up |
| `http://mandujoa.local/` and `/api/status/database` | 200 |
| `.\mvnw.cmd test` with the in-progress Kotlin `pom.xml` (after the `mandujoa` rename) | BUILD SUCCESS, 11/11 tests pass, Kotlin 1.9.25 |
| Full ADR 0001 final check (warnings, Docker rebuild, Flyway/Hibernate validate, `/status/`, API) | *Pending — runs after the Kotlin setup step is approved* |

Known harmless warning: `Could not transfer metadata … flyway-community-db-support (maven.pkg.github.com): HTTP 401`. The repository is declared in Flyway's own parent POM (`flyway-parent` 11.7.2, pulled in by `flyway-core`), not in this project or a local `settings.xml`. GitHub Packages requires authentication, so the metadata request fails and Maven falls back to Maven Central; the build succeeds. It predates the Kotlin setup.
