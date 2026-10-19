import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: '.',
  testMatch: ['e2e/platform/**/*.spec.ts', 'tests/platform/**/*.test.ts'],
  workers: 1,
  timeout: 45000,
  expect: { timeout: 15000 },
  reporter: [['list'], ['json', { outputFile: 'test-results/stage1-results.json' }]],
  use: {
    channel: 'chromium',
    baseURL: process.env.STAGE1_BASE_URL || 'http://127.0.0.1:5174',
    viewport: { width: 1600, height: 1000 },
    reducedMotion: 'reduce',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    launchOptions: { args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] },
  },
});
