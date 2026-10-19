import { test, expect } from '@playwright/test';
import { mkdir } from 'node:fs/promises';

for (const failure of ['503', 'timeout']) {
  test(`inventory ${failure} stays unavailable until a real API retry`, async ({ page }) => {
    test.setTimeout(60000);
    await page.route('**/api/v1/rooms?*', async (route) => {
      if (failure === '503')
        await route.fulfill({
          status: 503,
          json: { code: 503, msg: '台账数据库暂不可用，请重试' },
        });
      else {
        await new Promise((resolve) => setTimeout(resolve, 11000));
        await route.continue().catch(() => {});
      }
    });
    await page.goto('/home');
    await expect(page.locator('.inventory-source')).toContainText(
      failure === '503' ? '台账数据库暂不可用' : '台账API连接失败',
    );
    await expect(page.locator('.room-scene')).toHaveCount(0);
    await page
      .locator('.action-bottom')
      .getByRole('button', { name: '资产总览', exact: true })
      .click();
    await expect(page.locator('.asset-pie')).toHaveAttribute('data-total', '--');
    await expect(page.locator('.asset-location')).toHaveCount(0);
    await page.unroute('**/api/v1/rooms?*');
    await page.getByRole('button', { name: '重试台账查询' }).click();
    await expect(page.locator('.asset-pie')).toHaveAttribute('data-total', '129 台');
    await expect(page.locator('.asset-capacity-card')).toContainText('42');
  });
}

for (const [width, height] of [
  [1920, 1080],
  [1366, 768],
]) {
  test(`existing module read-only rendering at ${width}x${height}`, async ({ page }) => {
    test.setTimeout(90000);
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(e.message));
    // Compatibility inspection must never submit user/login/work-order writes.
    const writes: string[] = [];
    page.on('request', (r) => {
      if (r.url().includes('/api/') && !['GET', 'HEAD', 'OPTIONS'].includes(r.method()))
        writes.push(r.method() + ' ' + new URL(r.url()).pathname);
    });
    await page.setViewportSize({ width, height });
    await mkdir('../../docs/a13-evidence', { recursive: true });
    await page.goto('/home');
    await expect(page.locator('.inventory-source')).toContainText('演示台账');
    for (const [name, key, selector] of [
      ['能效管理', 'energy', '.energy-overview-card'],
      ['告警中心', 'alarm', '.inspection-assistant'],
    ]) {
      await page.locator('.action-bottom').getByRole('button', { name, exact: true }).click();
      await expect(page.locator(selector)).toBeVisible();
      await page.screenshot({ path: `../../docs/a13-evidence/${width}-${key}.png` });
    }
    for (const [path, key, selector] of [
      ['/weibao', 'work-orders', '.work-layout-v2'],
      ['/login', 'login', 'input[type="password"]'],
      ['/user', 'users', '.el-table'],
    ]) {
      await page.goto(path);
      if (key === 'work-orders')
        await expect(page.getByText('全部工单', { exact: true })).toBeVisible();
      else await expect(page.locator(selector)).toBeVisible();
      await page.screenshot({ path: `../../docs/a13-evidence/${width}-${key}.png` });
    }
    expect(errors).toEqual([]);
    expect(writes).toEqual([]);
  });
}
