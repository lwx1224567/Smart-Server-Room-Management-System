import { test, expect } from '@playwright/test';
import { mockConsole } from './console-fixture';
test('weather uses the same real-provider adapter when server connection is unavailable', async ({
  page,
}) => {
  await mockConsole(page, 'ADMIN');
  await page.route('**/api/v1/weather', (route) =>
    route.fulfill({
      json: {
        code: 200,
        data: { city: '上海', latitude: 31.2304, longitude: 121.4737, available: false },
      },
    }),
  );
  await page.route('https://api.open-meteo.com/v1/forecast?*', (route) =>
    route.fulfill({
      json: { current: { temperature_2m: 23.6, weather_code: 3, time: '2026-10-09T12:00' } },
    }),
  );
  await page.goto('/home');
  await expect(page.locator('.weather')).toContainText('上海 多云 23.6°C');
  await expect(page.locator('.weather')).toHaveAttribute('title', /2026-10-09T12:00/);
});
