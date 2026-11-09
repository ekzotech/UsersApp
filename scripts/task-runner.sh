#!/usr/bin/env bash
# Builds, lints and runs the user-management app (Spring Boot 3.5, Java 21).
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

usage() {
  cat <<'EOF'
Usage: scripts/task-runner.sh <command>

Commands:
  test                  Full Maven test suite (unit + MockMvc + Testcontainers). Needs Docker.
  test-class CLASS      Run tests for one class, e.g. service.StudentServiceTest
                        (or specify a single method: service.StudentServiceTest#createSavesActiveStudentWithTrimmedFields).
  compile               Compile main + test sources.
  run                   Run the app locally on port 8080 against a local PostgreSQL
                        (DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD env vars, defaults localhost:5432/usersapp).
  compose-up            docker compose up -d --build --wait (db + app on http://localhost:8081).
  compose-down          docker compose down (keeps the database volume).
  compose-reset         docker compose down -v (drops the database volume; next up re-seeds).
  seed-check            Print row counts from the compose database.
  e2e                   Run Playwright E2E suite against a running stack, inside the compose network
                        (docker compose --profile e2e run --rm e2e).
  e2e-local             Run Playwright from the host against http://localhost:8081 (needs stack up
                        and `npm ci` + `npx playwright install` in e2e/).
  e2e-stack             Bring the stack up (build + wait for health) and then run the E2E suite.
  e2e-install           npm ci + npx playwright install chromium (one-time setup for e2e-local).
EOF
}

case "${1:-}" in
  test)         mvn -B test ;;
  test-class)   mvn -B test -Dtest="${2:?class name required}" -Dsurefire.failIfNoSpecifiedTests=false ;;
  compile)      mvn -B -q test-compile ;;
  run)          mvn -B spring-boot:run ;;
  compose-up)   docker compose up -d --build --wait ;;
  compose-down) docker compose down ;;
  compose-reset) docker compose down -v ;;
  seed-check)
    docker compose exec -T db psql -U usersapp -d usersapp -c \
      "select 'students' t, count(*) from students union all select 'teachers', count(*) from teachers union all select 'enrollments', count(*) from enrollments union all select 'users', count(*) from users union all select 'courses', count(*) from courses;" ;;
  e2e)          docker compose --profile e2e run --rm -T e2e ;;
  e2e-local)    (cd e2e && npm test) ;;
  e2e-stack)    docker compose up -d --build --wait && docker compose --profile e2e run --rm -T e2e ;;
  e2e-install)  (cd e2e && npm ci && npx playwright install chromium) ;;
  *)            usage; exit 2 ;;
esac
