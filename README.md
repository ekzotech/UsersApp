# UsersApp

Веб-приложение для управления пользователями — студентами и преподавателями. SSR-интерфейс на
Spring MVC + Thymeleaf: вход в систему, каталог с поиском, создание студентов и преподавателей
(с учётной записью), активация/деактивация, кураторство и назначение студентов на предметы.
Данные хранятся в PostgreSQL, схема управляется миграциями Flyway, демо-данные при первом запуске
наполняются через Faker.

Проект написан по TDD (Red → Green): каждое поведение сначала закреплено падающим тестом, затем
реализацией. Тесты трёх уровней: unit/component (Mockito), интеграционные на реальном PostgreSQL
(Testcontainers) и E2E в браузере (Playwright).

---

## Содержание

1. [Стек](#стек)
2. [Что можно сделать в UI](#что-можно-сделать-в-ui)
3. [Требования](#требования)
4. [Быстрый старт: Docker Compose](#быстрый-старт-docker-compose)
5. [Учётные записи](#учётные-записи)
6. [Первый запуск: демо-данные (seed)](#первый-запуск-демо-данные-seed)
7. [Переменные окружения](#переменные-окружения)
8. [Сброс базы](#сброс-базы)
9. [Запуск без Docker](#запуск-без-docker)
10. [Тесты: unit и component](#тесты-unit-и-component)
11. [Тесты: E2E (Playwright)](#тесты-e2e-playwright)
12. [Архитектура](#архитектура)
13. [Схема БД и миграции](#схема-бд-и-миграции)
14. [Процесс разработки](#процесс-разработки)
15. [Известные ограничения](#известные-ограничения)
16. [Частые проблемы](#частые-проблемы)

---

## Стек

| Компонент | Версия / решение |
|---|---|
| Java | 21 (`<java.version>21</java.version>`) |
| Сборка | Maven 3.9 (`mvn`; `./mvnw` в проекте нет) |
| Фреймворк | Spring Boot `3.5.6` (parent) |
| Web / UI | Spring MVC + Thymeleaf, server-side rendering, CSS `src/main/resources/static/css/app.css` (mobile first) |
| Безопасность | Spring Security: form login, сессия, CSRF включён для всех POST-форм, роли `ROLE_ADMIN` / `ROLE_TEACHER`, пароли — BCrypt через delegating `PasswordEncoder` (хранятся с префиксом `{bcrypt}`) |
| Данные | Spring Data JPA (Hibernate), `ddl-auto=validate` — схему создаёт только Flyway |
| Миграции | Flyway + `flyway-database-postgresql` |
| БД | PostgreSQL 17 (`postgres:17-alpine`) |
| Демо-данные | datafaker `2.4.2`, фиксированный `randomSeed=42` |
| Unit/component | JUnit 5, Mockito, Spring MVC Test |
| Тесты на PostgreSQL | Testcontainers `1.20.6` (`postgresql`, `junit-jupiter`, `spring-boot-testcontainers`) |
| E2E | Playwright `@playwright/test@1.56.1`, два проекта: `chromium` и `mobile` (iPhone 13) |
| Контейнеры | Docker Compose: `db`, `app` (+ профиль `e2e`) |

---

## Что можно сделать в UI

Все страницы, кроме `/login`, `/css/**` и `/error`, требуют входа.

| Действие | Где | Кто может |
|---|---|---|
| Вход | `GET /login`; после входа — каталог `/users` | все |
| Выход | кнопка «Выйти» в шапке (`POST /logout`) | все авторизованные |
| Каталог `/users`: студенты (ФИО, email, группа, статус, куратор) и преподаватели (ФИО, email, статус, роль «Куратор»); поиск `?q=` фильтрует список студентов по ФИО или email (подстрока, без учёта регистра) | `GET /users` | все авторизованные; кнопки действий видят только ADMIN |
| Создание студента (новый сразу «Активен») | `GET /students/new`, `POST /students/new` → кнопка «Создать» | ADMIN |
| Создание преподавателя вместе с учётной записью (логин 3–64 символа, пароль ≥ 8 символов, опционально роль «Куратор») | `GET /teachers/new`, `POST /teachers/new` | ADMIN |
| Деактивация/активация студента → «Отчислен» / «Активен» | кнопки в строке каталога → `POST /students/{id}/deactivate` и `POST /students/{id}/activate` | ADMIN |
| Список преподавателей, увольнение/активация → «Уволен» / «Активен», ссылка «Открыть» на карточку | `GET /teachers` (ссылка «Управление преподавателями» в каталоге) | ADMIN |
| Профиль преподавателя: предметы, назначенные студенты, форма «Назначить студента» на предмет | `GET /teachers/{id}` → `POST /teachers/{id}/courses/{courseId}/students` | ADMIN |
| Куратор: «Мои студенты», смена куратора своим студентам, снятие куратора, «Взять кураторство» для студента без куратора; список кандидатов ищется по ФИО (`?find=`) | `GET /curator/students` и POST-эндпоинты под ним | TEACHER-куратор |

Правила, которые проверяет сервер (нарушение — сообщение об ошибке на странице, не 500):

- куратор — только преподаватель с ролью «Куратор» и не уволенный; отчисленному студенту куратора не
  назначить; у студента ровно один куратор или ни одного;
- куратор работает только со своими студентами (чужой студент → HTTP 403) и не может обращаться к
  `/students/**`, `/teachers/**`, `/admin/**` (403); администратор не открывает `/curator/**` (403);
- назначение на предмет: предмет должен быть закреплён за преподавателем, преподаватель не уволен,
  студент не отчислен, у одного студента на одном предмете — ровно один преподаватель, повторное
  назначение отклоняется, и на пару (преподаватель, предмет) — не более **60** студентов;
- несуществующая запись → HTTP 404; нечисловой `id` в пути (`/teachers/abc`) → HTTP 400.

---

## Требования

- **Docker** с Docker Compose v2 (`docker compose ...`). Достаточно для запуска всего приложения (на
  хосте ничего, кроме Docker, не нужно — сборка идёт внутри образа).
- Для запуска и тестов на хосте: **JDK 21**, **Maven 3.9**, **Docker** (Testcontainers поднимает
  контейнер `postgres:17-alpine`; первый раз образ скачивается из сети).
- Для E2E с хоста: **Node.js 18+** (в проекте использовался Node 24) и `npm`.
- Свободный порт **8081** на хосте (на него публикуется приложение).
- Для macOS без Docker Desktop/VMs: Docker Desktop, OrbStack, Colima или Rancher Desktop — любой.

---

## Быстрый старт: Docker Compose

1. (Необязательно) задать свои значения переменных из `.env.example`:

   ```bash
   cp .env.example .env
   ```

   `.env` читает сам `docker compose` (файл в `.gitignore`); без него действуют значения по
   умолчанию из `docker-compose.yml` (`SEED_ENABLED=true`, `APP_ADMIN_PASSWORD=admin12345`). Изменили
   `.env` после того, как база уже наполнена, — `APP_ADMIN_PASSWORD` применится только после сброса
   базы (см. [Сброс базы](#сброс-базы)).

2. Собрать образ и поднять `db` + `app`, дождавшись health-check обоих сервисов:

   ```bash
   docker compose up -d --build --wait
   ```

   `--wait` возвращается, только когда `db` отвечает на `pg_isready`, а `app` — на `GET /login`
   (health-check в `docker-compose.yml`). Первая сборка образа скачивает Maven-зависимости и
   занимает несколько минут; следующие запуски быстрые.

3. Открыть <http://localhost:8081> — редирект на `/login`, войти под
   `admin` / `admin12345` (или под паролем из `APP_ADMIN_PASSWORD`). После входа — каталог `/users`.

Короткий вариант этих же команд — обёртка из репозитория:

```bash
./scripts/task-runner.sh compose-up      # = docker compose up -d --build --wait
./scripts/task-runner.sh compose-down    # = docker compose down (volume с БД сохраняется)
```

`scripts/task-runner.sh` без аргументов печатает справку по всем командам.

### Первый запуск: демо-данные (seed)

При первом запуске на пустом volume происходит две вещи:

1. Flyway применяет миграции `V1__schema.sql` и `V2__seed_courses.sql` — создаёт таблицы и 12 предметов.
2. `SeedRunner` (профиль `seed` включён по умолчанию) наполняет базу через Faker: **≈300 студентов,
   12 преподавателей (6 из них — кураторы), назначения студентов на предметы и две учётные записи**
   (`admin` и `curator1`). В консоли контейнера видно строку
   `Seed done: 12 teachers, 300 students, … enrollments` (`docker compose logs app | grep "Seed done"`),
   а в таблицах — ровно 300 `students`, 12 `teachers`, 2 `users`, 12 `courses` и около 300
   `enrollments` (число зависит от распределения студентов по преподавателям; точное значение
   печатает та же строка лога).

Seed идемпотентен: если в таблице `users` уже есть хотя бы одна запись, повторный старт ничего не
меняет. Чтобы проверить данные внутри контейнера:

```bash
docker compose exec db psql -U usersapp -d usersapp -c "select count(*) from students"
./scripts/task-runner.sh seed-check      # количество строк в students/teachers/enrollments/users/courses
```

Таблицу можно посмотреть и в pgAdmin/DBeaver: сервер `localhost:5432`, база `usersapp`, пользователь
`usersapp`, пароль `usersapp` — но только если порт БД опубликован (см. следующий раздел; по
умолчанию compose его в хост не публикует).

---

## Учётные записи

| Логин | Пароль по умолчанию | Роль | Чем может пользоваться |
|---|---|---|---|
| `admin` | `admin12345` (переопределяется `APP_ADMIN_PASSWORD`) | `ROLE_ADMIN` | каталог, создание/активация студентов и преподавателей, профили преподавателей; `/curator/**` — 403 |
| `curator1` | `password` (создаётся seed'ом, только профиль `seed`) | `ROLE_TEACHER` | каталог `/users` и `/curator/students` |

Пароль администратора seed берёт из `app.seed.admin-password` (`APP_ADMIN_PASSWORD`) **при первом
наполнении** базы. Если поменять переменную на существующем volume, пароль у уже созданной записи не
изменится — нужен `docker compose down -v` (или смена пароля в БД).

Пользователи, созданные через «Создать преподавателя», тоже получают `ROLE_TEACHER`. Преподаватель
без галочки «Куратор» входит в каталог, но `/curator/students` для него — 403.

---

## Переменные окружения

`app` в compose получает переменные из `docker-compose.yml`, значения по умолчанию переопределяются
файлом `.env` (шаблон — `.env.example`) или окружением хоста:

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `SEED_ENABLED` | `true` | `false` — не наполнять базу демо-данными (схема и 12 предметов будут созданы) |
| `APP_ADMIN_PASSWORD` | `admin12345` | пароль учётной записи `admin` при первом seed |

Внутри контейнера приложение читает ещё и стандартный набор:

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `DB_HOST` | `localhost` (в compose — `db`) | хост PostgreSQL |
| `DB_PORT` | `5432` | порт PostgreSQL |
| `DB_NAME` | `usersapp` | имя базы |
| `DB_USER` | `usersapp` | пользователь БД |
| `DB_PASSWORD` | `usersapp` | пароль БД |

Остальные настройки seed (`app.seed.students`, `app.seed.teachers`, `app.seed.curators`,
`app.seed.random-seed`, `app.seed.admin-username`, `app.seed.curator-username`,
`app.seed.curator-password` — см. `seed/SeedProperties.java`) compose **не** прокидывает: формально их
можно передать как аргументы Spring Boot (например, добавив `--app.seed.students=10` в
`command`/`environment` сервиса `app` через override-файл), но проще изменить
`src/main/resources/application.properties`. Исключение — `SEED_ENABLED` и `APP_ADMIN_PASSWORD`: это
именно те переменные, которые читает `application.properties`.

### Отключить seed

```bash
SEED_ENABLED=false docker compose up -d --build --wait
```

или в `.env`:

```dotenv
SEED_ENABLED=false
```

В этом случае база будет пустой: войти не под кем (учётных записей нет), приложение доступно, но
каталог пуст. Включать этот режим имеет смысл, если база наполняется своим способом.

---

## Сброс базы

Volume `usersapp_db-data` переживает `docker compose down`, поэтому «чистый» первый запуск — это:

```bash
docker compose down -v
docker compose up -d --build --wait
```

После этого Flyway прогоняет миграции заново, а seed создаёт ровно 300 студентов, 12 преподавателей,
2 учётные записи и исходные назначения. Это же нужно делать перед демо или длинной серией E2E-прогонов
(см. [E2E](#тесты-e2e-playwright)).

```bash
./scripts/task-runner.sh compose-reset   # = docker compose down -v
```

---

## Запуск без Docker

«Без Docker» здесь означает «приложение на хосте, БД — всё равно в Docker»: без PostgreSQL приложение
не стартует. Приложение запускается на порту **8080** (`server.port` по умолчанию), компоуз публикует
`app` на **8081** — поэтому локальный запуск не конфликтует с уже поднятым стеком (если порт 8080 на
хосте свободен). Первый запуск против пустой базы создаст схему, 12 предметов и полный seed.

```bash
docker compose up -d db                          # только БД
./scripts/task-runner.sh run                     # = mvn -B spring-boot:run → http://localhost:8080
```

`scripts/task-runner.sh run` — это `mvn -B spring-boot:run`; аргументы можно передать напрямую:

```bash
mvn -B spring-boot:run                                                 # DB_* по умолчанию: localhost:5432/usersapp
mvn -B spring-boot:run -Dspring-boot.run.arguments=--server.port=8082  # другой порт
```

Адрес БД задаётся переменными `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` (по умолчанию
`localhost`, `5432`, `usersapp`, `usersapp`, `usersapp`).

**Порт БД в compose не публикуется.** В `docker-compose.yml` у сервиса `db` нет `ports:`, поэтому
`localhost:5432` на хосте не слушает (проверка: `docker compose port db 5432` печатает
`invalid IP:0`). Варианты подключиться:

1. **Проброс порта в отдельном override-файле** (в репозитории его нет, `docker-compose.override.yml`
   compose подхватывает автоматически):

   ```yaml
   # docker-compose.override.yml
   services:
     db:
       ports:
         - "5432:5432"
   ```

   ```bash
   docker compose up -d db
   ./scripts/task-runner.sh run        # приложение на :8080, база на localhost:5432
   ```

   После публикации база доступна и из pgAdmin/DBeaver: сервер `localhost:5432`, база `usersapp`,
   пользователь `usersapp`, пароль `usersapp`. Файл `docker-compose.override.yml` попадает под
   `git status` — не коммитьте его (или добавьте в `.git/info/exclude`).

2. **Своя локальная PostgreSQL 17** (установленная через Homebrew и т. п.) — создайте базу и
   пользователя и передайте адрес переменными:

   ```bash
   # пример для локального сервера: createdb usersapp; пользователь usersapp с паролем usersapp
   DB_HOST=localhost DB_PORT=5432 DB_NAME=usersapp DB_USER=usersapp DB_PASSWORD=usersapp \
     ./scripts/task-runner.sh run
   ```

3. **Разово через временный контейнер** (без compose), если нужен предсказуемый порт:

   ```bash
   docker run -d --name usersapp-db-local -p 5432:5432 \
     -e POSTGRES_DB=usersapp -e POSTGRES_USER=usersapp -e POSTGRES_PASSWORD=usersapp \
     postgres:17-alpine
   ./scripts/task-runner.sh run        # DB_* по умолчанию совпадают с этими значениями
   docker rm -f usersapp-db-local      # убрать БД (данные удалятся)
   ```

Остановить: `Ctrl+C` для приложения; БД — `docker compose stop db` (данные останутся) или
`docker compose down -v` (удалить данные).

---

## Тесты: unit и component

```bash
./scripts/task-runner.sh test          # = mvn -B test, зелёным должен быть весь набор
./scripts/task-runner.sh               # без аргумента — справка (код выхода 2)
```

`mvn test` запускает **и** `*Test`, **и** `*IT` — все классы сразу (так настроен `maven-surefire-plugin`
в `pom.xml`: `<include>**/*Test.java</include>` + `<include>**/*IT.java</include>`). Всего 215
тестовых методов в 28 классах (`grep -rc "@Test" src/test`).

| Слой | Классы | Что проверяет | Что нужно |
|---|---|---|---|
| Unit (чистый Mockito) | `service/*ServiceTest`, `seed/SeedRunnerTest`, `seed/SeedPropertiesTest`, `domain/UserRoleTest` | бизнес-правила сервисов на моках репозиториев (`@ExtendWith(MockitoExtension.class)`, `@Mock`); для seed — на моках `Faker`/репозиториев/`PasswordEncoder` | ничего, кроме JDK |
| Component (`@WebMvcTest`) | `web/*ControllerTest` | контроллеры через MockMvc: валидация, flash-сообщения, редиректы, CSRF, RBAC (сервисы подменены через `@MockitoBean`, `SecurityConfig` подключён через `@Import`); разметка страниц здесь не разбирается | ничего, кроме JDK |
| Интеграция БД (`@DataJpaTest`) | `db/*IT` (база `support/PostgresTestBase`) | запросы, ограничения, маппинг, инварианты БД на настоящем PostgreSQL; Flyway применяет миграции, Hibernate — `validate` | **Docker** и образ `postgres:17-alpine` |
| Полный контекст (`@SpringBootTest`) | `seed/SeedIntegrationIT`, `support/NoSeedInTestContextsTest`, `web/CuratorPageWebTest`, `web/SecurityConfigWebTest`, `db/CuratorPageRenderingIT` | seed при старте и его идемпотентность, ленивые связи при `open-in-view=false`, страницы куратора, правила безопасности | **Docker** и образ `postgres:17-alpine` |

Testcontainers поднимает контейнер `postgres:17-alpine` для каждого DB-класса сам; Docker должен быть
запущен. При первом запуске образ скачивается. Запуск одного класса (или одного метода):

```bash
./scripts/task-runner.sh test-class StudentServiceTest
./scripts/task-runner.sh test-class seed.SeedRunnerTest#seedIsIdempotent
```

Почему в `pom.xml` задан `-Dapi.version=1.44` (в `<argLine>` для surefire): docker-java, которым
Testcontainers общается с Docker, по умолчанию шлёт API версии `1.32`; современные Docker-демоны на
эту версию отвечают `client version 1.32 is too old` и контейнер не стартует. Версия `1.44`
поддерживается Docker 20.10+. Поэтому для DB-тестов `Docker Engine` должен быть новее 20.10 — и
запускать `mvn test` нужно как есть, не переопределяя `argLine` на командной строке.

Если Maven не может писать в `~/.m2` (нестандартная песочница), укажите локальный репозиторий:

```bash
mvn -B -Dmaven.repo.local=/абсолютный/путь/к/локальному/репозиторию test
```

---

## Тесты: E2E (Playwright)

E2E-набор живёт в `e2e/` (TypeScript, `@playwright/test`, конфигурация `e2e/playwright.config.ts`).
Сценарии: `auth.spec.ts` (редирект на `/login`, вход админом, неверный пароль), `users.spec.ts`
(создание студента и преподавателя с логином, деактивация/активация, поиск, ошибки формы),
`curator.spec.ts` (смена и снятие куратора, «Взять кураторство», назначение на предмет из профиля
преподавателя). Каждый тест работает на **двух проектах**: `chromium` (Desktop Chrome) и `mobile`
(iPhone 13 viewport) — итого 26 прогонов.

Нужен запущенный стек (БД + приложение). Три способа запустить набор:

### 1. Через профиль compose (рекомендуется — ничего не нужно устанавливать на хост)

```bash
docker compose up -d --build --wait
docker compose --profile e2e run --rm e2e
```

Сервис `e2e` — образ `mcr.microsoft.com/playwright:v1.56.1-noble` с браузерами, каталог `e2e/`
монтируется в `/work`; внутри выполняются `npm ci` и `npx playwright test` против `http://app:8080`.
Короче:

```bash
./scripts/task-runner.sh e2e          # = docker compose --profile e2e run --rm e2e
./scripts/task-runner.sh e2e-stack    # up -d --build --wait + прогон (то же, что npm run test:stack)
```

`npm run test:stack` делает то же самое, запускается из каталога `e2e/`:

```bash
cd e2e && npm run test:stack
```

### 2. С хоста против запущенного стека

```bash
docker compose up -d --build --wait
cd e2e
npm ci
npx playwright install chromium       # пакет браузеров для Playwright 1.56.1
E2E_BASE_URL=http://localhost:8081 npx playwright test
```

Другие варианты:

```bash
cd e2e && npm test                        # то же, что npx playwright test (baseURL по умолчанию localhost:8081)
cd e2e && npx playwright test tests/users.spec.ts          # один файл
cd e2e && npx playwright test -g "search finds created student by email"   # по имени теста
cd e2e && npx playwright test --project=chromium           # только desktop-проект
cd e2e && npx playwright show-report                       # HTML-отчёт последнего прогона
cd e2e && npm run test:ui                                  # UI-режим (не в CI)
```

Обёртки из корня: `./scripts/task-runner.sh e2e-install` (`npm ci` + `npx playwright install
chromium`), `./scripts/task-runner.sh e2e-local` (`npm test` в `e2e/`).

### Переменные E2E

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `E2E_BASE_URL` | `http://localhost:8081` | адрес приложения (в compose-профиле — `http://app:8080`) |
| `E2E_ADMIN_USERNAME` / `E2E_ADMIN_PASSWORD` | `admin` / `admin12345` | учётная запись администратора |
| `E2E_CURATOR_USERNAME` / `E2E_CURATOR_PASSWORD` | `curator1` / `password` | учётная запись куратора (существует только после seed) |

Если пароль админа задан через `APP_ADMIN_PASSWORD`, передайте его в `E2E_ADMIN_PASSWORD`, иначе
тесты входа упадут.

**Важно:** E2E меняют данные — создают студентов и преподавателей, меняют статусы, снимают куратора,
добавляют назначения. На общий volume это накапливается. Перед демо и перед длинной серией прогонов
начинайте с чистой базы:

```bash
docker compose down -v && docker compose up -d --build --wait
```

Несколько прогонов подряд на одном volume набор переживает: каждый тест создаёт данные с уникальным
суффиксом (`Date.now()`), а проверки считают строки относительно, а не абсолютно (проверено шестью
прогонами подряд).

Единственное, что накапливается, — сценарий «назначить нового студента на предмет» из
`curator.spec.ts`: за прогон он добавляет одну запись в пару (преподаватель 1 + «Математика»). Сид
заполняет эту пару на 13 из 60 (сид даёт каждому преподавателю по 2 предмета, 12–13 студентов на пару),
поэтому лимит «Преподаватель уже ведёт 60 студентов по этому предмету» наступает примерно на 47-м
прогоне на одном volume (и появляется в UI как ошибка на странице преподавателя). Список «Студенты без куратора» растёт, но на тесты больше не влияет: тест
находит нужного студента через поиск по ФИО. Перед демо или длинной серией прогонов делайте
`docker compose down -v` и поднимайте стек заново.

---

## Архитектура

Монолит Spring Boot, слоистая архитектура «сверху вниз»: `web` → `service` → `domain` (сущности и
репозитории); контекст поднимается в `UsersAppApplication`.

```
src/main/java/com/example/usersapp/
├── UsersAppApplication.java        Spring Boot entry point
├── domain/                         сущности и Spring Data-репозитории
│   ├── Student, Teacher, Course, Enrollment, User, UserRole
│   └── *Repository
├── service/                        бизнес-логика и транзакции
│   ├── StudentService, TeacherService, CuratorService, EnrollmentService, CourseService
│   ├── CustomUserDetailsService    пользователи для form login
│   ├── AppConfig                   @ConfigurationProperties(SeedProperties), бин Faker(seed=42)
│   ├── PasswordEncoderConfig       delegating PasswordEncoder (BCrypt)
│   └── *Exception                  нарушения правил и «не найдено»
├── web/                            контроллеры, формы, конфигурация безопасности
│   ├── SecurityConfig              правила доступа, form login, CSRF
│   ├── HomeController              "/" → /users, "/login"
│   ├── UserController              GET /users (каталог + поиск)
│   ├── StudentController           /students/new, /students/{id}/…
│   ├── TeacherController           /teachers, /teachers/new, активация/деактивация
│   ├── TeacherProfileController    /teachers/{id}, назначение студента на предмет
│   ├── CuratorController           /curator/students и действия куратора
│   └── StudentForm, TeacherForm    формы с Jakarta Validation
└── seed/
    ├── SeedProperties              app.seed.* (документируются в разделе «Переменные окружения»)
    └── SeedRunner                  @Profile("seed"), идемпотентный ApplicationRunner
```

```
src/main/resources/
├── application.properties
├── db/migration/V1__schema.sql, V2__seed_courses.sql
├── static/css/app.css
└── templates/
    ├── fragments/layout.html       общий каркас, flash-сообщения, кнопка «Выйти»
    ├── login.html
    ├── users/list.html             каталог + поиск
    ├── students/form.html, teachers/form.html, teachers/list.html, teachers/profile.html
    ├── curator/students.html
    └── error/404.html
```

```
src/test/java/com/example/usersapp/
├── domain/    UserRoleTest, фикстуры
├── service/   *ServiceTest (Mockito)
├── web/       *ControllerTest (@WebMvcTest), *WebTest (@SpringBootTest)
├── db/        *IT (@DataJpaTest на Testcontainers)
├── seed/      SeedRunnerTest, SeedPropertiesTest, SeedIntegrationIT
└── support/   PostgresTestBase, NoSeedInTestContextsTest
```

### Транзакции, `open-in-view=false` и где инициализируются связи

`spring.jpa.open-in-view=false`: сессия Hibernate закрыта, как только метод сервиса завершил
транзакцию. Шаблоны Thymeleaf рендерятся **после** этого, поэтому каждая ленивая связь, которую
читает страница, должна быть загружена внутри транзакции:

- каталог `/users` — `StudentService.findAll` в своей транзакции касается `student.getCurator().getFullName()`;
- карточка преподавателя — `TeacherService.findById` использует `@EntityGraph(attributePaths = "courses")`,
  а `EnrollmentService.studentsOfTeacher` — `EnrollmentRepository.findByTeacherIdOrderByCourseTitleAscStudentFullNameAsc`
  с `@EntityGraph(attributePaths = {"student", "course"})`;
- страница куратора — `CuratorService.studentsOfCurator` проходит по `student.getCurator().getFullName()`.
  Различающий тест на `@EntityGraph` профиля преподавателя —
  `web/CuratorPageWebTest.teacherProfilePageRendersStudentAndCourseNames` (полный контекст с `MockMvc`:
  без `@EntityGraph` у `EnrollmentRepository` он падает с `LazyInitializationException`); для страницы
  куратора — `db/CuratorRepositoryIT.studentsOfCuratorInitialisesCuratorsForRendering`.

Если странице нужна новая ленивая связь, инициализируйте её в сервисе в той же транзакции (или через
`@EntityGraph` в запросе), иначе получите `LazyInitializationException` на рендеринге.

### Исключения и HTTP-статусы

| Исключение | HTTP | Как показывается |
|---|---|---|
| `StudentNotFoundException`, `TeacherNotFoundException`, `CourseNotFoundException` | 404 | страница `error/404.html` |
| `AccessDeniedForCuratorException` | 403 | проверка куратора (чужой студент, не-куратор, уволенный) |
| `CuratorRuleException`, `EnrollmentRuleException` | 200 | flash-ошибка на странице, данные не меняются |
| `DuplicateEmailException`, `DuplicateUsernameException` | 200 | ошибка у поля формы (перерисовка) |
| Неверный тип `id` (`/teachers/abc`) | 400 | стандартная ошибка Spring |

---

## Схема БД и миграции

Миграции лежат в `src/main/resources/db/migration`, применяются Flyway при старте; Hibernate работает в
режиме `validate` и схему не создаёт.

| Таблица | Назначение и ключевые ограничения |
|---|---|
| `teachers` | преподаватель: `full_name`, `email`, `active`, `curator` (роль куратора); уникальный индекс по `lower(email)` |
| `students` | студент: `full_name`, `email`, `group_name`, `active`, `curator_id → teachers(id) ON DELETE SET NULL`; уникальный индекс по `lower(email)` |
| `courses` | справочник предметов: `slug` (уникальный), `title` |
| `teacher_courses` | компетенции: какие предметы закреплены за преподавателем; PK `(teacher_id, course_id)` |
| `enrollments` | назначение студента преподавателю по предмету; уникально `(student_id, teacher_id, course_id)` |
| `users` | учётные записи: `username` (уникальный по `lower`), `password` (`{bcrypt}…`), `role` (`ADMIN`/`TEACHER`), `teacher_id` (не более одной учётки на преподавателя) |

- `V1__schema.sql` — все таблицы, FK и индексы (включая регистронезависимую уникальность email и
  username).
- `V2__seed_courses.sql` — ровно 12 предметов (`Математика`, `Физика`, `Химия`, `Русский язык`,
  `Английский язык`, `Русская литература`, `Английская литература`, `История`, `Философия`,
  `Психология`, `Социология`, `Экономика`) с `id` 1..12.

Новая миграция — новый файл `V3__<имя>.sql`; изменять уже применённые файлы нельзя (Flyway проверяет
контрольные суммы). Полный сброс — `docker compose down -v`.

---

## Процесс разработки

- **TDD**: для каждого поведения сначала пишется падающий тест (Red — вывод фиксируется в отчёте
  задачи), затем минимальная реализация (Green), затем рефакторинг. Коммит делается только при
  зелёном `mvn test` (для E2E-задач — зелёном наборе Playwright).
- **Одна задача — один коммит** в формате Conventional Commits (`feat:`, `fix:`, `test:`, `docs:`,
  `build:`, `chore:`), на английском.
- **Ревью**: каждый коммит ревьюится отдельным агентом до начала следующей задачи; замечания
  («Minors») исправляются в последующих задачах той же фазы.
- **Фазы**: задачи идут на ветке фазы (`phase/0-foundation` … `phase/6-docs` в worktree
  `.worktrees/phase-N`), после всех ревью фаза сливается в `main` merge-коммитом. В `git log
  --first-parent` видны bootstrap-коммиты и merge-коммиты фаз; обычные задачи фазы видны в полной
  истории (`git log --oneline --graph --all`).

| Этап | Ветка | Что появилось | Merge-коммит в `main` |
|---|---|---|---|
| 0. Foundation | `phase/0-foundation` | скелет Spring Boot, `pom.xml`, Flyway V1/V2, compose (db + app), Dockerfile, тест схемы на Testcontainers | `d8f7837` |
| 1. Security | `phase/1-security` | таблица `users`, `CustomUserDetailsService`, form login, роли/CSRF, страница входа | `24d51cc` |
| 2. Users | `phase/2-users` | каталог `/users` с поиском, создание студента/преподавателя с учёткой, активация/деактивация | `63ce2f7` |
| 3. Academic | `phase/3-academic` | кураторство и правила назначения, страница куратора, профиль преподавателя, лимит 60 | `d451ed3` |
| 4. Seed | `phase/4-seed` | Faker-seed на старте (профиль `seed`), идемпотентность, транзакция | `d6a29db` |
| 5. E2E | `phase/5-e2e` | Playwright (chromium + mobile), профиль `e2e` в compose, health-check приложения | `3f1d4c9` |
| 6. Docs | `phase/6-docs` | этот README, `.env.example`, финальная проверка | — |

История слияний:

```bash
git log --first-parent --oneline main        # только merge-коммиты фаз
git log --oneline --graph --all              # вся история вместе с задачей
```

---

## Известные ограничения

- **Seed включён по умолчанию** и ориентирован на демо: база в рабочем окружении должна стартовать с
  `SEED_ENABLED=false` (или `app.seed.enabled=false`), а демо-данные (300 студентов и т. п.) — только
  для локального запуска.
- **Демо-пароли.** `admin/admin12345` и `curator1/password` — публичные значения по умолчанию. Для
  любого окружения, доступного извне, задайте `APP_ADMIN_PASSWORD` и смените пароль куратора.
- **Проверки перед записью «read-then-write».** Правила назначения куратора и назначения на предмет
  проверяются чтением состояния, а затем записи; при одновременных запросах от двух пользователей
  гонка возможна (ограничение на «один преподаватель на предмет у студента» и на 60 студентов).
  Уникальность назначения на пару «студент + преподаватель + предмет» держит индекс БД, но
  «один преподаватель на предмет» и лимит 60 — только сервис.
- **Нечисловые `id`** (`/teachers/abc`) → HTTP 400, а не 404; несуществующий числовой `id` → 404.
- **E2E мутируют данные** (создают записи, меняют статусы), объём данных на volume растёт — сброс
  `docker compose down -v` перед демо/длинной серией прогонов.
- **Куратор назначается только на странице куратора**: администратор не может назначить куратора из
  каталога; студент без куратора получает его через «Взять кураторство» на `/curator/students` (секция
  «Студенты без куратора»: поиск по ФИО через `?find=`; над таблицей подсказка «Показаны первые N
  из M», где при фильтре M — число совпадений (например, «Показаны первые 1 из 1»), а N не больше 50).
- **UI на русском и без постраничного вывода**: каталог и таблицы показываются полностью; только
  список «без куратора» на странице куратора ищется по ФИО и ограничен 50 строками.
- **Seed повторяем только на пустой базе**: он не «досоздаёт» недостающие демо-данные, если в `users`
  уже есть записи.

---

## Частые проблемы

| Симптом | Причина и решение |
|---|---|
| `docker compose up -d --wait` завершается с ошибкой, `app` не healthy | посмотрите `docker compose logs app`; чаще всего — миграция/Flyway против несовместимой БД (`docker compose down -v`) или порт 8081 уже занят: `lsof -i :8081`, затем `docker compose down` |
| `docker compose up -d` — `port is already allocated` | на 8081 уже слушает другой процесс (в том числе второй стек UsersApp из другого каталога) — освободите порт. Добавление порта в override-файл **не** заменяет `"8081:8080"`, а дописывает второй; чтобы переехать, нужен тег `!override`: `services: app: ports: !override ["8099:8080"]` (Compose 2.24+) |
| Приложение в контейнере стартует, но войти не под кем и каталог пуст | seed отключён (`SEED_ENABLED=false`), в базе нет предметов (`Seed skipped: no courses found` в логах) или в `users` уже есть учётные записи (seed не перезаписывает существующую базу); включите seed и выполните `docker compose down -v` |
| В логах `Seed skipped: no courses found` | приложение стартовало раньше, чем Flyway наполнил `courses` (например, миграции были отключены) — проверьте `docker compose logs app` и таблицу `flyway_schema_history` |
| `mvn test` — `client version 1.32 is too old` | `-Dapi.version=1.44` потерян: не переопределяйте `argLine` в командной строке. При `./scripts/task-runner.sh test` он берётся из `pom.xml` и задаётся автоматически |
| `mvn test` — нельзя создать файл в `~/.m2` | песочница не даёт писать в домашний каталог; задайте `-Dmaven.repo.local=<локальный репозиторий>` |
| E2E: `executable doesn't exist …` | не установлены браузеры: `cd e2e && npx playwright install chromium`, либо запускайте через compose-профиль `e2e` |
| E2E: все тесты входа падают на `/login` | неверный `E2E_BASE_URL` (стек не запущен) или пароль админа не совпадает с `E2E_ADMIN_PASSWORD` |
| E2E: «Преподаватель уже ведёт 60 студентов по этому предмету» | данные накопились: `docker compose down -v` и заново поднимите стек |

---

Документ для разработчиков и ревьюеров — [`IMPLEMENTATION.md`](IMPLEMENTATION.md) (договорённости по
тестам, сущностям, шаблонам и seed). План и спецификация — `docs/superpowers/plans/2026-11-09-users-app.md`.
