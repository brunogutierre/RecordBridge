import { defineConfig, devices } from '@playwright/test';

/** Starts the Spring Boot app, which serves the Angular build and the API on one origin. */
export default defineConfig({
  testDir: 'e2e',
  fullyParallel: false,
  workers: 1,
  reporter: process.env['CI'] ? 'github' : 'list',
  use: { baseURL: 'http://localhost:8187', trace: 'retain-on-failure' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: '../../gradlew bootRun',
    cwd: '..',
    url: 'http://localhost:8187/api/tasks',
    timeout: 300_000,
    reuseExistingServer: !process.env['CI'],
  },
});
