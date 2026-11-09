import { expect, test, type Page } from '@playwright/test';

/**
 * Compares the path part of the current URL with `/login` (or `login`)
 * so a deployment with a context path (`E2E_BASE_URL=https://host/usersapp`) still works.
 */
function hasPathSuffix(path: string) {
  const normalized = path.startsWith('/') ? path : `/${path}`;
  return (url: URL) => url.pathname === normalized || url.pathname.endsWith(normalized);
}

const ADMIN_USERNAME = process.env.E2E_ADMIN_USERNAME ?? 'admin';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'admin12345';

/**
 * The login page (templates/login.html) renders a plain form: `#username`, `#password` and a
 * "Войти" submit button. The catalog (templates/users/list.html) is the authenticated landing page
 * with the `Пользователи` heading.
 */
test.beforeEach(async ({ page }) => {
  // Sessions are per test context; make sure the flow always starts anonymously.
  await page.context().clearCookies();
});

async function login(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/login');
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.getByRole('button', { name: 'Войти' }).click();
}

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
