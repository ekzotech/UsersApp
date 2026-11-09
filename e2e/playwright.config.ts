import { defineConfig, devices } from '@playwright/test';

/**
 * The stack is started outside Playwright (`docker compose up -d --build`), so there is no
 * `webServer` block: the app listens on the compose-published port 8081.
 * `E2E_BASE_URL` points the suite at another deployment (CI, a preview environment).
 */
export default defineConfig({
  testDir: './tests',
  // The stack is seeded once and the specs share that data, so tests must not race each other.
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8081',
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'mobile',
      // `devices['iPhone 13']` carries `defaultBrowserType: 'webkit'`; only Chromium is installed,
      // so pin the browser and keep the iPhone 13 viewport / user agent emulation.
      use: { ...devices['iPhone 13'], defaultBrowserType: 'chromium' },
    },
  ],
});
