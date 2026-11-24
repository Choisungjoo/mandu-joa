# Noa HelloWorld

The stack runs in three Docker containers. Spring Boot runs the app, PostgreSQL stores the data, and NGINX proxies browser requests to the app.

## Start with Docker

```powershell
docker compose up --build
```

Open [http://noa/](http://noa/). The Windows hosts file maps `noa` to `127.0.0.1`.

## Stop the stack

```powershell
docker compose down
```

Database data is kept in the `postgres-data` volume, so it survives `docker compose down` and restarts. To erase the database as well, run this:

```powershell
docker compose down --volumes
```

The Spring Boot container listens on port 8080, and PostgreSQL listens on port 5432. Both ports are reachable only inside the Compose network. NGINX is the only service published to Windows, on port 80.

## Database

- **Engine:** PostgreSQL 17. Connection settings are in `compose.yaml` (`SPRING_DATASOURCE_*`). Set `POSTGRES_PASSWORD` in the environment to change the default password.
- **Migrations:** Flyway runs the scripts in `src/main/resources/db/migration` automatically on startup. Hibernate only validates the schema (`ddl-auto=validate`) and never changes it. To change the schema, add a new `V<n>__description.sql` file. Never edit a migration that has already been applied.

| Migration | Tables |
|---|---|
| V1 | `organizations`, `app_users`, `organization_memberships` |
| V2 | `integration_providers`, `connected_accounts`, `business_locations` |
| V3 | `reviews`, `interactions`, `response_drafts`, `response_publications` |
| V4 | `polling_configurations`, `sync_cursors`, `sync_runs`, `sync_errors` |
| V5 | `audit_events` (append-only; a trigger rejects `UPDATE`) |

### Duplicate prevention

Every provider record has a unique key on its provider identifiers:

- connected accounts: `(organization, provider, external account id)`
- locations: `(organization, provider, external location id)`
- reviews: `(location, external review id)`
- interactions: `(location, type, external id)`

Sync code should write through `ProviderRecordUpserter`. It uses `INSERT … ON CONFLICT DO UPDATE`, so repeated or concurrent syncs update the existing row and never insert a second one. Partial unique indexes allow only one open response draft and one in-flight publication per review.

### Retention

`RetentionService` runs daily at 03:30 (`app.retention.cron`). Runs on different instances are serialized with a Postgres advisory lock.

- **Provider content:** reviewer names and photos, ratings, review and interaction text, location contact details and raw payloads. This content is cleared when it has not been re-fetched within the provider's `content_retention_days`, or once its connected account is disconnected. Both providers are seeded with **30 days**. Provider IDs, the rows themselves, and our own drafts and audit trail are kept, and the next sync refills the same row.
- **Operational data:** sync errors are deleted after 90 days, sync runs after 180 days, and audit events after 730 days. These limits are set in `application.properties`.

> The 30-day value is a conservative default, not a verified quote of Google's or Meta's current terms. Check each provider's API policy before launch. Change a provider's limits in a new migration.

## Tests

```powershell
.\mvnw.cmd test
```

Integration tests start a throwaway PostgreSQL container with Testcontainers, so Docker must be running.
