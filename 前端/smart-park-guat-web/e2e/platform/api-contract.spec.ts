import { test, expect } from '@playwright/test';
import { mockConsole } from './console-fixture';

test('processing record uses PATCH and failed JSON requests expire the shared session', async ({
  page,
}) => {
  await mockConsole(page, 'MAINTAINER');
  let saved = false;
  await page.route('**/api/v1/work-orders/WO-CONTRACT/processing-record', async (route) => {
    expect(route.request().method()).toBe('PATCH');
    expect(route.request().postDataJSON()).toEqual({ process: '检查风扇' });
    saved = true;
    await route.fulfill({ json: { code: 200, data: { id: 'WO-CONTRACT', process: '检查风扇' } } });
  });
  await page.goto('/maintenance');
  const value = await page.evaluate(async () => {
    const modulePath = '/src/services/work-orders.ts';
    const api = await import(modulePath);
    return api.orderAction({ id: 'WO-CONTRACT' }, 'save', { process: '检查风扇' });
  });
  expect(saved).toBe(true);
  expect(value.process).toBe('检查风扇');
  await page.route('**/api/v1/rooms/1/alarms', (route) =>
    route.fulfill({ status: 401, json: { code: 401, msg: '会话已失效', data: null } }),
  );
  const message = await page.evaluate(async () => {
    const modulePath = '/src/api/platform/client.ts';
    const api = await import(modulePath);
    try {
      await api.getInventory('/rooms/1/alarms');
    } catch (error) {
      return (error as Error).message;
    }
  });
  expect(message).toBe('会话已失效');
  await expect
    .poll(() => page.evaluate(() => sessionStorage.getItem('smart-room-session')))
    .toBeNull();
});

test('management inventory consumes paginated camelCase rows without losing later pages', async ({
  page,
}) => {
  await mockConsole(page);
  const pages: string[] = [];
  await page.route('**/api/v1/admin/rooms?*', async (route) => {
    const pageNumber = new URL(route.request().url()).searchParams.get('page')!;
    pages.push(pageNumber);
    const items =
      pageNumber === '1'
        ? Array.from({ length: 100 }, (_, index) => ({
            id: index + 1,
            parkId: 1,
            sceneModelKey: `room-${index + 1}`,
          }))
        : [{ id: 101, parkId: 1, sceneModelKey: 'room-101' }];
    await route.fulfill({
      json: { code: 200, data: { items, total: 101, page: Number(pageNumber), pageSize: 100 } },
    });
  });
  await page.goto('/home');
  const rooms = await page.evaluate(async () => {
    const modulePath = '/src/api/platform/integrations.ts';
    return (await import(modulePath)).integrationApi.inventory('rooms');
  });
  expect(rooms).toHaveLength(101);
  expect(rooms[100].sceneModelKey).toBe('room-101');
  expect(pages).toEqual(['1', '2']);
});
