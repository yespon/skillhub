import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  timeout: process.env.CI ? 90_000 : 45_000,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: Number(process.env.PLAYWRIGHT_WORKERS ?? 1),
  reporter: 'html',
  use: {
    baseURL: 'http://localhost:3000',
    trace: 'on-first-retry',
    screenshot: 'on',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    command: 'pnpm preview --port 3000',
    url: 'http://localhost:3000',
    reuseExistingServer: true,
    timeout: 120000,
  },
})
