import { test, expect } from '@playwright/test';
test('database types drive reusable cabinet models, refresh and switching', async ({ page }) => {
  test.setTimeout(90000);
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.goto('/home');
  await expect(page.locator('.zone-label')).toHaveCount(5);
  async function select(name: string) {
    const group = page.getByRole('button', { name: '机柜（数据库）', exact: false });
    if ((await group.getAttribute('aria-expanded')) !== 'true') await group.click();
    await page.locator('.list-item').filter({ hasText: name }).click();
    await expect(page.locator('.cabinet-devices')).toContainText('已登记 3 台');
  }
  async function models() {
    return page.evaluate(() => {
      const el = document.querySelector('.room-scene[data-platform="true"]') as any;
      return el?.__vueParentComponent?.exposed.getState()?.inventory || [];
    });
  }
  await select('A区 03号机柜');
  await expect
    .poll(async () => (await models()).map((m: any) => m.kind))
    .toEqual(['server', 'server', 'server']);
  await page.reload();
  await expect
    .poll(async () => (await models()).map((m: any) => m.kind))
    .toEqual(['server', 'server', 'server']);
  await page.getByRole('button', { name: '返回机房', exact: true }).click();
  expect(await models()).toEqual([]);
  await select('A区 04号机柜');
  await expect
    .poll(async () => (await models()).map((m: any) => m.kind))
    .toEqual(['ports', 'ports', 'ports']);
  await page.getByRole('button', { name: '返回机房', exact: true }).click();
  await select('A区 05号机柜');
  await expect
    .poll(async () => (await models()).map((m: any) => m.kind))
    .toEqual(['server', 'server', 'ports']);
  await expect(page.locator('.room-scene')).toHaveCount(1);
  // A valid cabinet with no returned units must show no decorative fake devices.
  await page.getByRole('button', { name: '返回机房', exact: true }).click();
  await page.route(/\/api\/v1\/cabinets\/\d+\/devices(?:\?|$)/, async (route) => {
    const response = await route.fetch();
    const body = await response.json();
    body.data.items = [];
    body.data.total = 0;
    await route.fulfill({ response, json: body });
  });
  const group = page.getByRole('button', { name: '机柜（数据库）', exact: false });
  if ((await group.getAttribute('aria-expanded')) !== 'true') await group.click();
  await page.locator('.list-item').filter({ hasText: 'A区 03号机柜' }).click();
  await expect(page.locator('.cabinet-devices')).toContainText('暂无已登记设备');
  await expect.poll(models).toEqual([]);
  expect(errors).toEqual([]);
});
