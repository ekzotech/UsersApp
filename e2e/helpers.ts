import { expect, type Locator, type Page } from '@playwright/test';

/**
 * Accounts created by the seed profile (`SeedRunner` + `SeedProperties`). The admin credentials can be
 * overridden for a stack started with a different `APP_ADMIN_PASSWORD`.
 */
export const ADMIN_USERNAME = process.env.E2E_ADMIN_USERNAME ?? 'admin';
export const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'admin12345';
/** The one demo curator account the seed creates; it belongs to the first seeded curator teacher. */
export const CURATOR_USERNAME = process.env.E2E_CURATOR_USERNAME ?? 'curator1';
export const CURATOR_PASSWORD = process.env.E2E_CURATOR_PASSWORD ?? 'password';

/**
 * Compares the path part of the current URL with `expected` (e.g. `/curator/students`) so a deployment
 * with a context path (`E2E_BASE_URL=https://host/usersapp`) still works.
 */
export function hasPathSuffix(expected: string) {
  const normalized = expected.startsWith('/') ? expected : `/${expected}`;
  return (url: URL) => url.pathname === normalized || url.pathname.endsWith(normalized);
}

/**
 * A value that is unique for the lifetime of the stack: the compose database volume outlives a single
 * run, and `npm test` may run more than once against it, so every created user needs fresh identifiers.
 */
let lastSuffix = 0;

export function unique(suffix = 'e2e'): string {
  lastSuffix = Math.max(Date.now(), lastSuffix + 1);
  return `${suffix}-${lastSuffix}`;
}

/**
 * A short random marker (`E2E-xxxxxxxx`) for data that must be findable through the catalog's substring
 * search. Unlike `unique()`, it shares no prefix with earlier runs of the same suite or with the seed
 * (`qa-student-<epochMs>@example.com`), so "exactly one row matches this search" is a real claim.
 */
export function uniqueToken(): string {
  return `E2E-${Math.random().toString(36).slice(2, 10)}`;
}

/**
 * Logs in through the form on `/login` (templates/login.html: `#username`, `#password`, "Войти") and waits
 * for the authenticated layout, which renders the "Выйти" button.
 */
export async function login(page: Page, username: string = ADMIN_USERNAME, password: string = ADMIN_PASSWORD): Promise<void> {
  await page.goto('/login');
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.getByRole('button', { name: 'Войти' }).click();
  await expect(page.getByRole('button', { name: 'Выйти' })).toBeVisible();
}

export interface StudentData {
  fullName: string;
  email: string;
  group: string;
}

export interface TeacherData {
  fullName: string;
  email: string;
  username: string;
  password: string;
}

/** Creates a student through `/students/new` (admin only) and returns to the catalog. */
export async function createStudent(page: Page, student: StudentData): Promise<void> {
  await page.goto('/students/new');
  await page.locator('#fullName').fill(student.fullName);
  await page.locator('#email').fill(student.email);
  await page.locator('#groupName').fill(student.group);
  await page.getByRole('button', { name: 'Создать' }).click();
  await expect(page).toHaveURL(hasPathSuffix('/users'));
  await expect(page.getByRole('heading', { name: 'Пользователи', level: 1 })).toBeVisible();
}

/** Creates a teacher with a login account through `/teachers/new` (admin only) and returns to the catalog. */
export async function createTeacher(page: Page, teacher: TeacherData): Promise<void> {
  await page.goto('/teachers/new');
  await page.locator('#fullName').fill(teacher.fullName);
  await page.locator('#email').fill(teacher.email);
  await page.locator('#username').fill(teacher.username);
  await page.locator('#password').fill(teacher.password);
  await page.getByRole('button', { name: 'Создать' }).click();
  await expect(page).toHaveURL(hasPathSuffix('/users'));
  await expect(page.getByRole('heading', { name: 'Пользователи', level: 1 })).toBeVisible();
}

/** Builds unique student data; `fullName` keeps the `E2E` marker so it is easy to find in the UI. */
export function studentData(tag = 'Студент'): StudentData {
  const suffix = unique('student');
  return {
    fullName: `E2E ${tag} ${suffix}`,
    email: `qa-${suffix}@example.com`,
    group: `QA-${suffix.slice(-6)}`,
  };
}

export function teacherData(tag = 'Преподаватель'): TeacherData {
  const suffix = unique('teacher');
  return {
    fullName: `E2E ${tag} ${suffix}`,
    email: `qa-${suffix}@example.com`,
    username: `qa.${suffix}`,
    password: 'password123',
  };
}

// ---------------------------------------------------------------------------------------------------
// Catalog (/users) selectors. The tables carry no ids, so a row is addressed by the cell that holds its
// unique email/text; the catalog renders the student and the teacher table one after another.
// ---------------------------------------------------------------------------------------------------

/** Every row of every catalog table (the catalog renders the student table, then the teacher table). */
function allRows(page: Page): Locator {
  return page.locator('table.data tbody tr');
}

/**
 * The catalog row whose email cell is exactly `email` (student or teacher table). Emails are unique across
 * both tables (case-insensitive unique indexes), so at most one row of the catalog matches; a substring
 * search (`?q=`) can match several, this helper narrows the search result down to the exact row.
 */
export function rowByEmail(page: Page, email: string): Locator {
  return allRows(page).filter({
    has: page.locator('td[data-label="Email"]', { hasText: exactText(email) }),
  });
}

/** Matches the trimmed content of a cell exactly, so `qa-x@example.com` never matches `qa-x2@example.com`. */
function exactText(value: string): RegExp {
  return new RegExp(`^\\s*${value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*$`);
}

/**
 * Searches the catalog (`GET /users?q=`) and waits for the rendered result page. Fields are matched as
 * substrings, so the students section shows every student whose email contains `query` — narrow it down.
 */
export async function searchUsers(page: Page, query: string): Promise<void> {
  await page.goto('/users');
  await page.locator('input[type="search"][name="q"]').fill(query);
  await page.getByRole('button', { name: 'Найти' }).click();
  await expect(page).toHaveURL((url) => url.searchParams.get('q') === query);
  await expect(page.locator('input[type="search"][name="q"]')).toHaveValue(query);
}
