import { expect, test } from '@playwright/test';
import { ADMIN_PASSWORD, ADMIN_USERNAME, hasPathSuffix, login } from '../helpers';

/**
 * Login page (templates/login.html) → `#username`, `#password`, the "Войти" button; the catalog
 * (templates/users/list.html) is the authenticated landing page. Credentials, the login flow and the
 * context-path-aware URL matcher come from ../helpers, so an `E2E_ADMIN_*` override or a change to the
 * suffix rule applies to every spec at once.
 */
test.beforeEach(async ({ page }) => {
  // Sessions are per test context; make sure the flow always starts anonymously.
  await page.context().clearCookies();
});

test('anonymous visit /users redirects to /login', async ({ page }) => {
  await page.goto('/users');

  await expect(page).toHaveURL(hasPathSuffix('/login'));
  await expect(page.getByRole('heading', { name: 'Вход', level: 1 })).toBeVisible();
  await expect(page.locator('#username')).toBeVisible();
  await expect(page.locator('#password')).toBeVisible();
});

test('login with admin credentials lands on /users', async ({ page }) => {
  await login(page, ADMIN_USERNAME, ADMIN_PASSWORD);

  await expect(page).toHaveURL(hasPathSuffix('/users'));
  await expect(page.getByRole('heading', { name: 'Пользователи', level: 1 })).toBeVisible();
  await expect(page.getByRole('heading', { name: /Студенты \(\d+\)/ })).toBeVisible();
  await expect(page.getByRole('heading', { name: /Преподаватели \(\d+\)/ })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Выйти' })).toBeVisible();
});

test('wrong password shows error', async ({ page }) => {
  await login(page, ADMIN_USERNAME, 'definitely-not-the-password');

  await expect(page).toHaveURL(hasPathSuffix('/login'));
  await expect(page).toHaveURL((url) => url.searchParams.has('error'));
  await expect(page.getByText('Неверный логин или пароль.')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Пользователи', level: 1 })).toHaveCount(0);
});
