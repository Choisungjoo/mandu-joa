# 0002. Development, staging and production profiles

- **Status:** Accepted
- **Date:** 2026-11-27
- **Ticket:** 1

## Context

All settings live in one `application.properties`, so development values apply everywhere ([inspection](../inspection.md), risks 2–4):

- the database password `noa` is committed and used as the default;
- the status screen (`/status`, `/api/status/database`) is on and has no login;
- the retention job runs on every instance, including a developer's laptop.

Ticket 1 asks for development, staging and production configuration profiles.

The three environments have different purposes:

| Environment | Where | Who uses it | Data |
|---|---|---|---|
| `dev` | A developer's PC (Docker Compose) | The developer | Fake |
| `staging` | A server, set up like production | The team, testers | Test accounts on the real providers |
| `prod` | A server | Real customers | Real |

Staging is a rehearsal of production. It should behave like production, so problems show up there and not in front of customers.

## Decision

### 1. Spring profiles

- `application.properties` keeps the settings shared by all environments (Flyway, JPA, retention ages and cron).
- `application-dev.properties`, `application-staging.properties` and `application-prod.properties` hold what differs.
- `spring.profiles.default=dev`. A run without `SPRING_PROFILES_ACTIVE` uses `dev`, as today. Staging and production servers always set the profile explicitly.

### 2. Settings per profile

| Setting | `dev` | `staging` | `prod` |
|---|---|---|---|
| Database password | `noa` allowed (in the dev file) | From an environment variable, required | From an environment variable, required |
| Status screen (`app.status.enabled`) | On | Off by default; can be switched on with an environment variable when needed | Off |
| Retention job (`app.retention.enabled`) | Off | On | On |
| Log level for our code | `DEBUG` | `INFO` | `INFO` |
| Data source for providers | Mock | Real providers, test accounts | Real providers, customer accounts |

Notes:

- **Required environment variables:** the staging and prod files reference the password as `${DB_PASSWORD}` with no default. If it is missing, the app fails at startup instead of falling back to a known value.
- **Errors are always logged in full.** At `INFO`, `ERROR` entries still include the stack trace, so "normal logs, detailed critical errors" needs no extra setup.
- **Staging status screen:** when switched on, it is still public until the login ticket lands. Turn it on only when needed, or restrict it to known IPs in NGINX.
- **Data source:** the provider integrations do not exist yet. The rule is recorded now and implemented with the provider ticket as a setting such as `app.provider.mode=mock|live`. Mock-based testing happens locally in `dev`. Staging does not switch to mock.

### 3. Safety checks at startup

- When the `prod` profile is active, the app refuses to start if the status screen is enabled or if mock providers are selected. Environment variables can override properties, so the file alone is not enough to guarantee "off".
- When `staging` or `prod` is active, the app refuses to start if the database password is `noa`.

### 4. Docker Compose

- `compose.yaml` stays the local `dev` setup and sets `SPRING_PROFILES_ACTIVE=dev`.
- **Local runs use Docker Compose only.** The app is started with `docker compose up -d --build`; running it directly on the host (IDE or `mvnw spring-boot:run`) is not supported, because Compose publishes no Postgres port. This keeps the database closed to the host. Tests are unaffected: they use Testcontainers.
  - Rejected for now: a host port bound to `127.0.0.1`, Spring Boot Docker Compose support, and Testcontainers at development time. They can be revisited if faster edit-and-run cycles or debugging on the host become necessary.
- Staging and production Compose files (or overrides) come with the staging deployment ticket. They supply the profile and the secrets through environment variables, never through committed files.

## Alternatives considered

- **Default profile `prod` (fail safe):** rejected for now. Every local run and test would need to set a profile. This can be revisited when the staging server is set up.
- **One properties file with environment variables for everything:** rejected. The differences between environments would be invisible in the repository.
- **Staging switches between mock and real providers:** rejected. Staging should match production; mock testing happens locally.
- **Status screen permanently off in staging:** rejected. It can help diagnose a staging problem, so it is allowed behind an explicit switch.

## Consequences

- The dev experience does not change: `docker compose up` and `.\mvnw.cmd test` work as before.
- Deploying to staging or production without the password, or with dev-only features on, fails loudly at startup.
- `noa` stays in Git as a dev-only value. It must never be used outside `dev`; the startup check enforces that.
- Each new setting must be placed either in the shared file or in each profile file.
- The production profile turns the status screen off, which also covers the "remove before public release" note for production. The screen code itself is still removed or put behind login before public release.
