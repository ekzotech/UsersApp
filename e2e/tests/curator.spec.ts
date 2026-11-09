import { expect, test, type Locator, type Page } from '@playwright/test';
import {
  createStudent,
  hasPathSuffix,
  login,
  studentData,
  CURATOR_USERNAME,
  CURATOR_PASSWORD,
} from '../helpers';

/**
 * Curator flows (SECURITY MODEL, amendment (6)): `curatorId` is accepted ONLY on `/curator/students`, by a
 * signed-in curator, for the students that curator leads. The teacher profile enrolment is admin-only. `curator1` (SeedProperties) is linked to
 * the first seeded curator teacher; `/curator/students` lists that teacher's students with a per-student
 * `select#curatorId-<id>`.
 */

test.beforeEach(async ({ page }) => {
  await page.context().clearCookies();
});

/** The student id of the first listed student (the select id is `curatorId-<studentId>`). */
async function firstStudentId(page: Page): Promise<string> {
  const select = page.locator('select[id^="curatorId-"]').first();
  await expect(select).toBeVisible();
  return (await select.getAttribute('id'))!.replace('curatorId-', '');
}

/** The row of a listed student: its select carries the student id. */
function curatorRow(page: Page, studentId: string) {
  return page.locator('table.data tbody tr', { has: page.locator(`select#curatorId-${studentId}`) });
}

test('curator saves its own student without changing the curator', async ({ page }) => {
  await login(page, CURATOR_USERNAME, CURATOR_PASSWORD);
  await page.goto('/curator/students');

  const heading = page.getByRole('heading', { name: /^Мои студенты \(\d+\)$/, level: 1 });
  await expect(heading).toBeVisible();

  const studentId = await firstStudentId(page);
  const select = page.locator(`select#curatorId-${studentId}`);
  const currentCuratorId = await select.inputValue();
  const currentCuratorName = (await select.locator('option:checked').innerText()).trim();
  expect(currentCuratorId).not.toBe('');

  // Selecting the already assigned curator and saving is a plain re-assignment to the same person.
  await select.selectOption(currentCuratorId);
  await curatorRow(page, studentId).getByRole('button', { name: 'Сохранить' }).click();

  await expect(page).toHaveURL(hasPathSuffix('/curator/students'));
  await expect(page.locator('p.flash.success')).toHaveText('Куратор назначен');
  await expect(curatorRow(page, studentId).locator('td[data-label="Куратор"]')).toHaveText(currentCuratorName);
});

test('curator removes the curator of one of its students', async ({ page }) => {
  await login(page, CURATOR_USERNAME, CURATOR_PASSWORD);
  await page.goto('/curator/students');

  const heading = page.getByRole('heading', { name: /^Мои студенты \(\d+\)$/, level: 1 });
  const countBefore = Number((await heading.innerText()).match(/\d+/)![0]);
  const studentId = await firstStudentId(page);
  const select = page.locator(`select#curatorId-${studentId}`);

  // "Снять куратора" is its own form and carries no curatorId, so the removal cannot be mistaken for an
  // assignment of the curator selected in the assign form.
  await curatorRow(page, studentId).getByRole('button', { name: 'Снять куратора' }).click();

  await expect(page).toHaveURL(hasPathSuffix('/curator/students'));
  await expect(page.locator('p.flash.success')).toHaveText('Куратор снят');
  await expect(curatorRow(page, studentId)).toHaveCount(0);
  await expect(page.getByRole('heading', { name: `Мои студенты (${countBefore - 1})`, level: 1 })).toBeVisible();
});

test('curator claims a student without a curator', async ({ page }) => {
  // The admin creates a student without a curator; the student is listed under "Студенты без куратора".
  await login(page);
  const student = studentData('Без куратора');
  await createStudent(page, student);

  await page.context().clearCookies();
  await login(page, CURATOR_USERNAME, CURATOR_PASSWORD);
  await page.goto('/curator/students');

  const heading = page.getByRole('heading', { name: /^Мои студенты \(\d+\)$/, level: 1 });
  const countBefore = Number((await heading.innerText()).match(/\d+/)![0]);
  const firstSelect = page.locator('select[id^="curatorId-"]').first();
  await expect(firstSelect).toBeVisible();
  const curatorName = (await firstSelect.locator('option:checked').innerText()).trim();

  // The candidate list is capped at 50 rows, so find the new student through the search instead of
  // relying on its position.
  await page.getByLabel('Найти студента без куратора').fill(student.fullName);
  await page.getByRole('button', { name: 'Найти' }).click();
  await expect(page).toHaveURL(/[?&]find=/);

  const unassignedRow = page.locator('table.data tbody tr', { hasText: student.fullName });
  await expect(unassignedRow).toHaveCount(1);
  await expect(unassignedRow.getByRole('button', { name: 'Взять кураторство' })).toBeVisible();
  await unassignedRow.getByRole('button', { name: 'Взять кураторство' }).click();

  await expect(page).toHaveURL(hasPathSuffix('/curator/students'));
  await expect(page.locator('p.flash.success')).toHaveText('Куратор назначен');
  await expect(page.getByRole('heading', { name: `Мои студенты (${countBefore + 1})`, level: 1 })).toBeVisible();
  const claimedRow = page.locator('table.data tbody tr', { has: page.locator('select[id^="curatorId-"]'), hasText: student.fullName });
  await expect(claimedRow).toHaveCount(1);
  await expect(claimedRow.locator('td[data-label="Куратор"]')).toHaveText(curatorName);
});

test('admin assigns a newly created student to a subject on the teacher profile', async ({ page }) => {
  await login(page);
  const student = studentData('Предмет');
  await createStudent(page, student);

  // Seeded teacher 1 leads "Математика" (course 1) and already has enrolled students, so the competency and
  // limit checks pass for one more student.
  await page.goto('/teachers/1');
  await expect(page.getByRole('heading', { name: 'Математика', level: 3 })).toBeVisible();

  const section = page.locator('section', { has: page.locator('select#studentId-1') });
  const select = section.locator('select#studentId-1');
  await select.selectOption({ label: student.fullName });
  await section.getByRole('button', { name: 'Назначить' }).click();

  await expect(page).toHaveURL(hasPathSuffix('/teachers/1'));
  await expect(page.locator('p.flash.success')).toHaveText('Студент назначен');
  const enrolledRow = page.locator('table.data tbody tr').filter({ hasText: student.fullName });
  await expect(enrolledRow).toHaveCount(1);
  await expect(enrolledRow.locator('td[data-label="Предмет"]')).toHaveText('Математика');
});
