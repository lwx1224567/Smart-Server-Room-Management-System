import { test, expect, type Page } from '@playwright/test';
import { mockConsole } from './console-fixture';

const sessionKey = 'smart-room-session';
const servicePath = '/src/services/console.ts';

async function apiFailure(page: Page, path: string) {
  return page.evaluate(
    async ({ modulePath, path }) => {
      const { consoleApi } = await import(modulePath);
      try {
        await consoleApi(path);
        return null;
      } catch (error: any) {
        return {
          name: error.name,
          message: error.message,
          status: error.status,
          code: error.code,
          type: error.type,
          fields: error.fields,
          requestId: error.requestId,
          serverTime: error.serverTime,
        };
      }
    },
    { modulePath: servicePath, path },
  );
}

for (const failure of ['503', 'network', 'timeout', 'invalid-json'] as const) {
  test(`session check ${failure} keeps token, blocks protected UI and retries original page`, async ({
    page,
  }) => {
    await mockConsole(page, 'MAINTAINER');
    let unavailable = true;
    let protectedRequests = 0;
    page.on('request', (request) => {
      if (request.url().includes('/api/v1/work-orders')) protectedRequests++;
    });
    if (failure === 'timeout') {
      await page.addInitScript(() => {
        const timeout = AbortSignal.timeout.bind(AbortSignal);
        // Exercise the actual AbortSignal path without a 15-second test delay.
        AbortSignal.timeout = (milliseconds) =>
          timeout(milliseconds === 15000 ? 150 : milliseconds);
      });
    }
    await page.route('**/api/v1/auth/me', async (route) => {
      if (!unavailable) return route.fallback();
      if (failure === 'network') return route.abort('failed');
      if (failure === 'timeout') {
        await new Promise((resolve) => setTimeout(resolve, 500));
        await route.fulfill({ status: 503, json: { code: 503, msg: '稍后重试' } }).catch(() => {});
        return;
      }
      if (failure === 'invalid-json')
        return route.fulfill({ contentType: 'text/html', body: '<html>upstream error</html>' });
      return route.fulfill({ status: 503, json: { code: 503, msg: '认证服务暂时不可用' } });
    });

    await page.goto('/maintenance?tab=orders');
    await expect(page.getByRole('heading', { name: '暂时无法验证登录状态' })).toBeVisible();
    await expect(page).toHaveURL(/\/session-unavailable\?/);
    await expect(page.locator('.maintenance-sidebar')).toHaveCount(0);
    expect(protectedRequests).toBe(0);
    expect(await page.evaluate((key) => sessionStorage.getItem(key), sessionKey)).toBe(
      'initial-test-token',
    );
    expect(
      await page.evaluate(
        async (modulePath) => (await import(modulePath)).auth.checked,
        servicePath,
      ),
    ).toBe(false);
    await expect(page.getByRole('alert')).toContainText(
      failure === 'timeout'
        ? '请求超时'
        : failure === 'network'
          ? '网络连接失败'
          : failure === 'invalid-json'
            ? '服务响应异常'
            : '认证服务暂时不可用',
    );

    // Another failed retry must still block the page and preserve the session.
    await page.getByRole('button', { name: '重新验证' }).click();
    await expect(page.getByRole('button', { name: '重新验证' })).toBeEnabled();
    expect(await page.evaluate((key) => sessionStorage.getItem(key), sessionKey)).toBe(
      'initial-test-token',
    );
    expect(protectedRequests).toBe(0);
    unavailable = false;
    await page.getByRole('button', { name: '重新验证' }).click();
    await expect(page).toHaveURL(/\/maintenance\?tab=orders$/);
    await expect(page.locator('.maintenance-sidebar')).toBeVisible();
  });
}

for (const json of [true, false]) {
  test(`HTTP 401 clears session even with ${json ? 'JSON' : 'HTML'} response`, async ({ page }) => {
    await mockConsole(page, 'MAINTAINER');
    await page.route('**/api/v1/auth/me', (route) =>
      route.fulfill(
        json
          ? { status: 401, json: { code: 401, msg: '登录已失效' } }
          : { status: 401, contentType: 'text/html', body: '<html>unauthorized</html>' },
      ),
    );
    await page.goto('/maintenance');
    await expect(page).toHaveURL(/\/login$/);
    expect(await page.evaluate((key) => sessionStorage.getItem(key), sessionKey)).toBeNull();
    await expect(page.locator('.maintenance-sidebar')).toHaveCount(0);
  });
}

test('403 blocks session verification without deleting token', async ({ page }) => {
  await mockConsole(page);
  await page.route('**/api/v1/auth/me', (route) =>
    route.fulfill({ status: 403, json: { code: 403, msg: '当前操作无权限' } }),
  );
  await page.goto('/user');
  await expect(page.getByRole('heading', { name: '暂时无法验证登录状态' })).toBeVisible();
  expect(await page.evaluate((key) => sessionStorage.getItem(key), sessionKey)).toBe(
    'initial-test-token',
  );
});

for (const status of [400, 409, 503]) {
  test(`API error preserves HTTP ${status} and envelope context`, async ({ page }) => {
    await mockConsole(page);
    await page.route('**/api/v1/error-probe', (route) =>
      route.fulfill({
        status,
        json: {
          code: status,
          msg: '测试业务错误',
          data: null,
          requestId: 'request-stage1a',
          serverTime: '2026-10-17T00:00:00Z',
          error: { type: 'CONTRACT_ERROR', fields: { name: '不能为空' } },
        },
      }),
    );
    await page.goto('/login');
    expect(await apiFailure(page, '/error-probe')).toEqual({
      name: 'ApiError',
      message: '测试业务错误',
      status,
      code: status,
      type: 'CONTRACT_ERROR',
      fields: { name: '不能为空' },
      requestId: 'request-stage1a',
      serverTime: '2026-10-17T00:00:00Z',
    });
    expect(await page.evaluate((key) => sessionStorage.getItem(key), sessionKey)).toBe(
      'initial-test-token',
    );
  });
}

test('non-JSON HTTP failure keeps its status and a readable message', async ({ page }) => {
  await mockConsole(page);
  await page.route('**/api/v1/error-probe', (route) =>
    route.fulfill({ status: 503, contentType: 'text/html', body: '<h1>Unavailable</h1>' }),
  );
  await page.goto('/login');
  expect(await apiFailure(page, '/error-probe')).toMatchObject({
    name: 'ApiError',
    status: 503,
    type: 'HTTP_ERROR',
    message: '服务响应异常，请重试',
  });
});

test('successful JSON API still unwraps and normalizes data', async ({ page }) => {
  await mockConsole(page);
  await page.route('**/api/v1/data-probe', (route) =>
    route.fulfill({
      json: { code: 200, data: { recordOrigin: 'DEMO', source: 'MOCK_IMPORT', value: 0 } },
    }),
  );
  await page.goto('/login');
  const data = await page.evaluate(
    async (modulePath) => (await import(modulePath)).consoleApi('/data-probe'),
    servicePath,
  );
  expect(data).toMatchObject({ recordOrigin: 'REAL', value: 0 });
});
