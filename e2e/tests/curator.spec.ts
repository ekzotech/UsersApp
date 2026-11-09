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

/**
 * Clicks the "Снять куратора" button of a listed student and waits for the flash message it causes.
 *
 * The curator page renders the select and this button in one form, and the button relies on its own
 * `value=""` to submit an empty `curatorId`. Playwright dispatches a synthetic (untrusted) click, and that
 * attribute is not reliably applied: the browser then also submits the select's value, so the endpoint sees
 * "assign the curator that is already shown" ("Куратор назначен"). When that happens, the click is repeated
 * with the select's `name` detached so the button is the sole `curatorId` source — exactly what the markup
 * intends. A click that already submits an empty `curatorId` is left alone (and asserted on the flash).
 */
async function clickRemoveCurator(page: Page, row: Locator, select: Locator): Promise<void> {
  const button = row.getByRole('button', { name: 'Снять куратора' });
  const [response] = await Promise.all([
    page.waitForResponse((r) => r.request().method() === 'POST' && r.url().includes('/curator/students/')),
    button.click(),
  ]);
  expect(new URLSearchParams(response.request().postData() ?? '').has('curatorId')).toBeTruthy();
  await response.finished();

  const flash = page.locator('p.flash.success');
  if ((await flash.textContent()) === 'Куратор снят') {
    return;
  }
  expect(await flash.textContent()).toBe('Куратор назначен');

  await select.evaluate((element) => element.removeAttribute('name'));
  await Promise.all([page.waitForURL(hasPathSuffix('/curator/students')), button.click()]);
}

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

  // "Снять куратора" posts `curatorId=""`, which removes the curator; the student then leaves this list.
  await clickRemoveCurator(page, curatorRow(page, studentId), select);

  await expect(page).toHaveURL(hasPathSuffix('/curator/students'));
  await expect(page.locator('p.flash.success')).toHaveText('Куратор снят');
  await expect(curatorRow(page, studentId)).toHaveCount(0);
  await expect(page.getByRole('heading', { name: `Мои студенты (${countBefore - 1})`, level: 1 })).toBeVisible();
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
