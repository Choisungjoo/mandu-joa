# 0001. Kotlin for new code, Java kept for existing code

- **Status:** Accepted
- **Date:** 2026-11-27
- **Ticket:** 1

## Context

The backend is Spring Boot 3.5.16 on Java 17: 40 main source files in 9 packages, plus 3 test classes (11 tests) that run against a real PostgreSQL through Testcontainers.

Ticket 1 asks to keep **Kotlin/Spring Boot as the primary backend**. The code is written in Java today.

Converting all of it now adds no features and risks breaking working, tested code. Kotlin's biggest practical gain for this project is **null safety**: many columns are nullable, and retention clears provider content to `NULL`. So Kotlin should be used where new code is written, and where a `null` problem shows that existing Java code would benefit from it.

## Decision

### 1. Which language

| Case | Language |
|---|---|
| New code (provider integrations, polling engine, new services, new controllers) | Kotlin |
| New JPA entities | Kotlin (see section 3) |
| New tests | Kotlin |
| Changes to an existing Java file, of any size | Java |
| A problem in existing Java code whose cause is **confirmed** to be `null` | Convert that package to Kotlin, then fix (section 2) |

### 2. Converting existing Java code because of a `null` problem

1. Investigate in Java first. Conversion starts only once the cause is confirmed to be `null` handling.
2. Convert **the whole package** that contains the problem, so no new Java/Kotlin boundary is created inside it.
3. Write a **separate ADR** for each package conversion, before converting. It records the problem, the package, and the checks.
4. **Check test coverage first.** If the existing tests do not exercise the package's behaviour, add tests before converting. They must pass against the current Java code, so they can show whether the Kotlin version behaves the same.
5. Then do it in two separate steps, never mixed:
   1. **Convert only.** Behaviour stays the same; the existing tests must pass unchanged. Tests are not edited in this step.
   2. **Fix.** Fix the `null` problem in Kotlin and add a test that reproduces it.
6. **Final check when the conversion is complete:**
   - the project compiles with no new warnings;
   - the full test suite passes (`.\mvnw.cmd test`);
   - the app starts in Docker (`docker compose up -d --build`) and Flyway and Hibernate `validate` succeed;
   - the status screen and its API respond normally.

   If a test fails, first check whether the cause is the environment (for example Docker not running) or the code.
7. If the Kotlin compiler shows more `null` issues during the conversion, write them down and fix them in step 2 or in follow-up work.

### 3. Build setup and conventions

- Maven stays. `kotlin-maven-plugin` compiles `src/main/kotlin` and `src/main/java` together, before `maven-compiler-plugin`, so Kotlin and Java can call each other.
- Compiler plugins:
  - `spring`: opens Spring beans so `@Transactional`, `@Scheduled` and other proxies work.
  - `jpa`: generates the no-arg constructor that JPA needs.
  - `all-open` for `@Entity`, `@MappedSuperclass` and `@Embeddable`, so Hibernate can create lazy-loading proxies.
- Compiler option `-Xjsr305=strict`: Spring's `@Nullable`/`@NonNullApi` annotations become real Kotlin types, so a missed `null` check on a Spring API is a compile error, not a warning. It affects Kotlin code only.
- Dependencies: `kotlin-stdlib`, `kotlin-reflect`, `jackson-module-kotlin`.
- **Kotlin version:** the one managed by Spring Boot 3.5.16 (1.9.25), with no override.
- Conventions:
  - JPA entities are regular classes, not `data class`es. Column nullability matches the Kotlin type.
  - Value types are `data class`es.
  - Constructor injection only.
  - At Java/Kotlin boundaries, values from Java are typed explicitly as nullable or non-null instead of relying on platform types.

## Alternatives considered

- **Convert all Java code to Kotlin now:** rejected. It changes 2,000+ lines with no new features, and puts tested code at risk.
- **Stay Java-only:** rejected. Ticket 1 asks for Kotlin as the primary backend.
- **Convert a file whenever it needs a large change:** rejected. Size alone is not a reason; `null` safety is the gain that justifies a conversion.
- **Convert single files instead of packages:** rejected. A single file creates new Java/Kotlin boundaries inside a package, and `null` protection is weakest at those boundaries.
- **New JPA entities in Java:** rejected to keep one simple rule, "new code is Kotlin". The `jpa` and `all-open` plugins handle the known entity issues, and Hibernate's `validate` check catches mapping mistakes at startup.
- **Kotlin 2.x by overriding the Spring Boot version:** rejected. Spring Boot 3.5 is built and tested with 1.9.25.

## Consequences

- The codebase is mixed Java and Kotlin for a long time. Contributors need to read both.
- Over time, new features and `null` fixes move more code to Kotlin, so Kotlin becomes the primary language without a big-bang rewrite.
- `null` is least safe where Kotlin calls Java. Boundaries need explicit types.
- The Kotlin build setup is added as its own step, before any ticket writes Kotlin code, together with one small Kotlin test that proves Kotlin and Java compile and run together. Build problems are then found apart from feature work.
- Each package conversion has its own ADR, so the history shows why each part moved to Kotlin.
