# Implementation notes (for implementers and reviewers)

The requirements live in `docs/superpowers/plans/2026-11-09-users-app.md`. This file only records
cross-cutting decisions every task must follow.

## Stack

- Java 21, Spring Boot 3.5.6, Maven (`./mvnw` is not provided — use `mvn`).
- Spring MVC + Thymeleaf, Spring Security (form login, delegating `PasswordEncoder` that stores
  `{bcrypt}…` hashes, CSRF on), Spring Data JPA, Flyway, Lombok, datafaker 2.4.2.
- Stored password hashes must come from `PasswordEncoder.encode(...)`; a delegating encoder rejects
  bare `$2a$…` hashes, so tests build hashes via `encode()` or a `{bcrypt}`-prefixed literal.
- DB: PostgreSQL (compose image `postgres:17-alpine`; tests use Testcontainers).

## Commands

Use `scripts/task-runner.sh` (see `--help`). Typical implements: `test-class`, then `test`.
Docker must be running for Testcontainers (@DataJpaTest / @SpringBootTest integration tests).

## Test layers

| Layer | Where | Notes |
|-------|-------|-------|
| Unit / component (Mockito) | `*ServiceTest`, `*ControllerTest` (`@WebMvcTest`) | Repos mocked with `@Mock`; controllers mock services with `@MockitoBean` (Boot 3.4+). `@WebMvcTest` slices need `@Import(SecurityConfig.class)` + `@WithMockUser` (roles `ADMIN`/`TEACHER`) — security is part of the spec. |
| DB integration | `db/*IT` | `@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")` + `@AutoConfigureTestDatabase(replace = NONE)` + `@ServiceConnection` `PostgreSQLContainer<>("postgres:17-alpine")`. Flyway runs migrations. |
| Full context | `*IT` with `@SpringBootTest` | Same container pattern; add `@MockitoBean CustomUserDetailsService` where login data is irrelevant. |

DB test classes end with `@DirtiesContext(classMode = AFTER_CLASS)` (the base `PostgresTestBase` already
carries it) because Testcontainers restarts a shared static `@Container` for each test class while Spring
caches a context that would still point at the previous class's dead port. A plain `@SpringBootTest`
class that declares its **own** static `@Container` must repeat that annotation
(`SecurityConfigWebTest` does), otherwise identical cross-class failures appear once more
`@SpringBootTest` ITs exist.

Surefire runs `*Test`; failsafe-style `*IT` classes are bound into the `test` phase by the
`maven-surefire-plugin` configuration (see pom `<includes>`), so `mvn test` runs everything.

## Conventions

- Entities: package `com.example.usersapp.domain`; Lombok `@Getter @Setter @NoArgsConstructor(access = PROTECTED)`;
  `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` with `@EqualsAndHashCode.Include` on `id`.
  Never use Lombok `@Data`/`@ToString` on entities with bidirectional associations — use
  `@ToString(exclude = ...)`.
- Many-to-many `Teacher.courses` must be fetched with `@EntityGraph(attributePaths = "courses")`
  wherever rules read competencies (service `findById` calls).
- Services are `@Service` with constructor injection and `@Transactional` on mutating methods.
  Business-rule violations throw named runtime exceptions (`*RuleException` → flash error or 400,
  `*NotFoundException` → `@ResponseStatus(NOT_FOUND)`), never raw `IllegalStateException` reaching the UI as 500.
- Forms: `web/*Form` classes with Jakarta Validation; error messages in Russian; controller
  re-renders the form (HTTP 200) with `BindingResult` on validation errors, duplicates use
  `bindingResult.rejectValue(...)`.
- Templates: `layout` fragment in `templates/fragments/layout.html`; every POST form contains the
  CSRF input rendered by Thymeleaf (`th:action` does it automatically — keep it that way); UI text in Russian.
- Seed profile is `seed`; `SeedRunner` is `@Profile("seed")` and idempotent.
- Tests must assert behavior through public interfaces; no assertions on private state.
