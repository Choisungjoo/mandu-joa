# 0003. Layer-first package structure

- **Status:** Accepted
- **Date:** 2026-11-27
- **Ticket:** 1

## Context

Ticket 1 asks to separate API controllers, application services, persistence, provider integrations and scheduled jobs.

Today the packages under `com.example.helloworld` are split by feature (`review`, `sync`, `audit`, `retention`, `status`, …). Each holds its entities, repositories and services together, a controller sits next to its service, and the only scheduled job lives inside `retention` ([inspection](../inspection.md), section 9).

The provider integrations (Google Business Profile, a mock provider) are the next work. They should land in a clear place, and the core must not depend on any one provider.

## Decision

### 1. Top-level packages are layers

```
com.example.helloworld
├── api/            HTTP controllers: request and response only
├── application/    Use cases and business logic, transactions
├── persistence/    Entities, repositories, SQL (JPA and JdbcTemplate)
├── provider/       External integrations (Google, mock, …), behind interfaces
├── job/            @Scheduled entry points; they only call application services
└── config/         @ConfigurationProperties and cross-cutting configuration
```

Inside each layer, sub-packages are by feature, for example `persistence.review`, `application.retention`.

### 2. Where today's classes go

| Today | New package |
|---|---|
| `HelloController` | `api` |
| `status.DatabaseStatusController` | `api.status` |
| `status.DatabaseStatusService`, `status.DatabaseStatus` | `application.status` |
| `retention.RetentionService` | `application.retention` |
| `retention.RetentionScheduler` | `job` |
| `retention.RetentionProperties` | `config` |
| `persistence.BaseEntity` | `persistence` (unchanged) |
| `organization.*` (entities, repositories) | `persistence.organization` |
| `integration.*` | `persistence.integration` |
| `review.*` entities and repositories | `persistence.review` |
| `review.ProviderRecordUpserter` | `persistence.review` (it is SQL for writes) |
| `sync.*` | `persistence.sync` |
| `audit.*` | `persistence.audit` |
| `HelloWorldApplication` | root package (unchanged, so component scanning still covers everything) |

`provider` stays empty until the provider ticket.

### 3. Dependency rules

```
api ──▶ application ──▶ persistence
job ──▶ application ──▶ provider (interfaces)
```

- `api` and `job` call `application`. They do not use repositories directly.
- `application` uses `persistence` and the `provider` interfaces. It does not depend on `api` or `job`.
- `persistence` and `provider` depend on nothing above them.
- These rules are checked by an architecture test (for example ArchUnit), written in Kotlin per ADR [0001](0001-kotlin-for-new-code.md), so a wrong import fails the build.

### 4. How the move is done

- **Move only.** Files keep their language (Java stays Java, per ADR 0001), names and behaviour. Only `package` lines and imports change. Moves use `git mv`, so file history is kept.
- **One layer per commit**, each followed by `.\mvnw.cmd test`, so a failure points at one move.
- Entity scanning, Flyway and the `@Nullable` getters are not affected. `EntityNullabilityTest` finds entities through the Hibernate metamodel, so it keeps working after the move.
- At the end, the ADR 0001 final check runs: compile without new warnings, all tests, `docker compose up -d --build`, Flyway and Hibernate `validate`, status screen and API.
- A package-level `@NonNullApi` is out of scope; it waits for the ticket author's answer.

## Alternatives considered

- **Feature first, layers inside (`review.api`, `review.application`, …):** rejected. Work on one feature stays in one folder, but the layer boundaries the ticket asks for are less visible, and providers serve several features at once.
- **Keep the current feature packages and only add `provider` and `job`:** rejected. Controllers, services and persistence would stay mixed.
- **Separate Maven modules per layer:** rejected for now. It enforces boundaries at compile time, but adds build complexity for a small codebase. The architecture test gives most of the benefit.

## Consequences

- Each layer has one job, and the provider work has a clear place.
- Working on one feature touches several top-level folders.
- The move changes about 40 files at once (package lines and imports only). Review focuses on "nothing but packages changed", which `git diff -M` shows as renames.
- The architecture test adds one test dependency and keeps the structure from eroding.
