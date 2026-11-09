import { expect, test, type Page } from '@playwright/test';
import {
  hasPathSuffix,
  login,
  rowByEmail,
  searchUsers,
  studentData,
  teacherData,
  createTeacher,
  uniqueToken,
  type StudentData,
} from '../helpers';

/**
 * Admin flows over the catalog (`/users`) and the two admin-only creation forms. Every test creates its own
 * data with a fresh timestamp suffix, so repeated runs over the same seeded stack stay independent. The
 * specs share one stack, therefore they run serially (see `workers` in playwright.config.ts) and each test
 * starts from a clean browser context, so sessions are per test. Curator-side flows live in curator.spec.ts.
 */

test.beforeEach(async ({ page }) => {
  await page.context().clearCookies();
});

/** Fills `/students/new` with `student`; the default submit button is «Создать» (students/form.html). */
async function fillStudentForm(page: Page, student: StudentData): Promise<void> {
  await page.goto('/students/new');
  await expect(page.getByRole('heading', { name: 'Новый студент', level: 1 })).toBeVisible();
  await page.locator('#fullName').fill(student.fullName);
  await page.locator('#email').fill(student.email);
  await page.locator('#groupName').fill(student.group);
}

/** Submits the student form and lands on the catalog with its flash message. */
async function submitStudent(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Создать' }).click();
  await expect(page).toHaveURL(hasPathSuffix('/users'));
  await expect(page.getByText('Студент создан')).toBeVisible();
}

test('admin creates a student and sees active status', async ({ page }) => {
  await login(page);
  const student = studentData('Активный');

  await fillStudentForm(page, student);
  await page.getByRole('button', { name: 'Создать' }).click();

  await expect(page).toHaveURL(hasPathSuffix('/users'));
  await expect(page.getByText('Студент создан')).toBeVisible();
  const row = rowByEmail(page, student.email);
  await expect(row).toHaveCount(1);
  await expect(row.locator('td[data-label="ФИО"]')).toHaveText(student.fullName);
  await expect(row.locator('td[data-label="Статус"]')).toHaveText('Активен');
  await expect(row.getByRole('button', { name: 'Деактивировать' })).toBeVisible();
});

test('admin creates a teacher with login', async ({ page }) => {
  await login(page);
  const teacher = teacherData('Логин');

  await createTeacher(page, teacher);

  await expect(page.getByText('Преподаватель создан')).toBeVisible();
  const row = rowByEmail(page, teacher.email);
  await expect(row).toHaveCount(1);
  await expect(row.locator('td[data-label="ФИО"]')).toHaveText(teacher.fullName);
  await expect(row.locator('td[data-label="Статус"]')).toHaveText('Активен');

  // The login account created with the teacher can sign in (default landing page is the catalog).
  await page.getByRole('button', { name: 'Выйти' }).click();
  await expect(page).toHaveURL(hasPathSuffix('/login'));
  await login(page, teacher.username, teacher.password);
  await expect(page.getByRole('heading', { name: 'Пользователи', level: 1 })).toBeVisible();
});

test.describe('deactivate / reactivate a student', () => {
  // One student goes through both transitions; the steps are an unbroken sequence on the same state.
  test.describe.configure({ mode: 'serial' });

  const student = studentData('Отчисленный');

  test('admin deactivates student → status Отчислен', async ({ page }) => {
    await login(page);
    await fillStudentForm(page, student);
    await submitStudent(page);

    await rowByEmail(page, student.email).getByRole('button', { name: 'Деактивировать' }).click();

    await expect(page.getByText('Студент отчислен')).toBeVisible();
    const row = rowByEmail(page, student.email);
    await expect(row.locator('td[data-label="Статус"]')).toHaveText('Отчислен');
    await expect(row.locator('td[data-label="ФИО"]')).toHaveText(student.fullName);
  });

  test('deactivated student sees Активировать', async ({ page }) => {
    await login(page);
    await searchUsers(page, student.email);

    const row = rowByEmail(page, student.email);
    await expect(row).toHaveCount(1);
    await expect(row.locator('td[data-label="Статус"]')).toHaveText('Отчислен');
    await expect(row.getByRole('button', { name: 'Деактивировать' })).toHaveCount(0);

    await row.getByRole('button', { name: 'Активировать' }).click();

    await expect(page.getByText('Студент активирован')).toBeVisible();
    const reactivated = rowByEmail(page, student.email);
    await expect(reactivated).toHaveCount(1);
    await expect(reactivated.locator('td[data-label="Статус"]')).toHaveText('Активен');
    await expect(reactivated.getByRole('button', { name: 'Деактивировать' })).toBeVisible();
  });
});

test('search finds created student by email', async ({ page }) => {
  await login(page);

  // `StudentService.findAll(q)` matches a substring of the email, so the search key is a random token that no
  // other row of this volume (seed or an earlier run) can contain; the second student shares nothing with it.
  const token = uniqueToken();
  const first = studentData('Поиск1');
  first.email = `qa-${token}@example.com`;
  const second = studentData('Поиск2');
  await fillStudentForm(page, first);
  await submitStudent(page);
  await fillStudentForm(page, second);
  await submitStudent(page);

  // The unfiltered catalog is seeded with ~300 students; after the search exactly one row remains.
  await expect(page.getByRole('heading', { name: /^Студенты \(\d{2,}\)$/, level: 2 })).toBeVisible();

  await searchUsers(page, token);

  await expect(page.getByRole('heading', { name: 'Студенты (1)', level: 2 })).toBeVisible();
  const studentRows = page.locator('section').first().locator('tbody tr');
  await expect(studentRows).toHaveCount(1);
  const row = rowByEmail(page, first.email);
  await expect(row.locator('td[data-label="ФИО"]')).toHaveText(first.fullName);
  await expect(row.locator('td[data-label="Группа"]')).toHaveText(first.group);
  await expect(rowByEmail(page, second.email)).toHaveCount(0);
});

test('malformed student form keeps typed email and shows field errors', async ({ page }) => {
  await login(page);
  const student = studentData('Брак');

  await page.goto('/students/new');
  await page.locator('#fullName').fill('');
  await page.locator('#email').fill(student.email);
  await page.locator('#groupName').fill(student.group);
  await page.getByRole('button', { name: 'Создать' }).click();

  // The controller re-renders the form (HTTP 200) instead of redirecting, with the submitted values kept.
  await expect(page.getByRole('heading', { name: 'Новый студент', level: 1 })).toBeVisible();
  await expect(page.getByText('Укажите ФИО')).toBeVisible();
  await expect(page.locator('#email')).toHaveValue(student.email);
  await expect(page.locator('#groupName')).toHaveValue(student.group);

  // Nothing was saved: the catalog search for the typed email finds no student row.
  await searchUsers(page, student.email);
  await expect(page.getByRole('heading', { name: 'Студенты (0)', level: 2 })).toBeVisible();
  await expect(page.getByText('Ничего не найдено.').first()).toBeVisible();
});
