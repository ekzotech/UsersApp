# Bootstrap scaffold

Files delivered by the bootstrap commit (merged into `main` before any phase work):

- `docs/superpowers/plans/2026-11-09-users-app.md` — implementation plan (source of truth for tasks).
- `pom.xml` — Spring Boot 3.5.6 / Java 21 project with web, thymeleaf, data-jpa, security,
  validation, flyway (+ postgresql dialect), postgresql driver, lombok, datafaker;
  test scope: spring-boot-starter-test, spring-security-test, Testcontainers 1.20.6.
- `src/main/resources/db/migration/V1__schema.sql` — canonical PostgreSQL schema:
  `teachers`, `students` (single nullable `curator_id` → teachers), `courses`,
  `teacher_courses` (competencies), `enrollments` (unique student+teacher+course), `users`
  (login accounts: role, optional `teacher_id`); case-insensitive unique indexes on emails/username.
- `src/main/resources/db/migration/V2__seed_courses.sql` — the 12-course reference catalogue.
- `.gitignore`, `.dockerignore` — ignore build output, `.worktrees/`, `.superpowers/`, e2e artifacts.
- `docker-compose.yml` — `db` (postgres:17-alpine, volume, healthcheck) republishing nothing on the
  host, `app` (built from `Dockerfile`) on host port **8081**, wired via `DB_*`/`SEED_ENABLED`/
  `APP_ADMIN_PASSWORD` env vars. No `app` healthcheck yet (added in Task 5.3, after `/login` exists).
- `Dockerfile` — multi-stage: `maven:3.9-eclipse-temurin-21` build → `eclipse-temurin:21-jre-alpine`
  runtime, non-root user, `java -jar /app/app.jar`.

Phase tasks build on top of this scaffold; the scaffold itself is not a plan task and produces no
application code. Every phase task is a single reviewed commit.
