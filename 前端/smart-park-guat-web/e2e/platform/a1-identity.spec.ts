import { test, expect, type Page } from '@playwright/test';
import { mkdir } from 'node:fs/promises';

async function open(page: Page) {
  await page.goto('/home');
  await expect(page.locator('.inventory-source')).toContainText('演示台账');
}
async function business(page: Page, name: string) {
  await page.locator('.action-bottom').getByRole('button', { name, exact: true }).click();
}
async function choose(page: Page, name: string) {
  const group = page.getByRole('button', { name: /机柜（数据库）/ });
  if ((await group.getAttribute('aria-expanded')) !== 'true') await group.click();
  await page.locator('.list-item').filter({ hasText: name }).click();
}

test('assets project complete inventory counts, types, zones and missing U positions', async ({
  page,
}) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await open(page);
  await business(page, '资产总览');
  await expect(page.locator('.asset-pie')).toHaveAttribute('data-total', '129 台');
  await expect(page.locator('.asset-legend')).toContainText('服务器 72 台');
  await expect(page.locator('.asset-legend')).toContainText('交换机 56 台');
  await expect(page.locator('.asset-legend')).toContainText('存储 1 台');
  await expect(page.locator('.asset-capacity-card')).toContainText('42');
  await expect(page.locator('.asset-capacity-card')).toContainText('未登记');
  await expect(page.locator('.asset-location')).toHaveCount(5);
  await expect(page.locator('.asset-location').nth(0)).toContainText('8 柜 · 27 台');
  await expect(page.locator('.asset-location').nth(1)).toContainText('8 柜 · 24 台');
  await expect(page.locator('.asset-status-card')).toContainText('未采集129');
  await page.locator('.asset-location').first().click();
  await expect(page.locator('#asset-detail')).toContainText('已登记设备6 台');
  await expect(page.locator('#asset-detail')).toContainText('演示台账');
  await expect(page.locator('.room-scene')).toHaveAttribute('data-platform', 'true');
  expect(errors).toEqual([]);
});

test('all nine legacy nodes locate database devices while eight edges remain demonstrations', async ({
  page,
  request,
}) => {
  test.setTimeout(90000);
  const rooms = await (await request.get('http://localhost:8080/api/v1/rooms')).json();
  const snapshot = (
    await (
      await request.get(`http://localhost:8080/api/v1/rooms/${rooms.data.items[0].id}/scene`)
    ).json()
  ).data;
  await open(page);
  await business(page, '网络拓扑');
  for (const key of ['core', 'agg', 'sw1', 'sw2', 'srv1', 'srv2', 'nas', 'srv3', 'srv4']) {
    const device = snapshot.devices.find((d: any) => d.legacyKey === key);
    await page.locator('#topo-search').fill(device.code);
    await expect(page.locator('#topo-detail')).toContainText(`DEVICE ${device.id}`);
    await expect(page.locator('#topo-detail')).toContainText('演示台账');
    await page.locator('#topo-detail .primary').click();
    await expect(page.locator('.cabinet-devices button.selected')).toHaveAttribute(
      'data-device-id',
      device.id,
    );
    await expect(page.locator('.room-scene .scene-canvas')).toHaveAttribute(
      'data-extracted-device',
      device.sceneModelKey,
    );
    await expect(page.locator('.selection-info')).toContainText(device.code);
    await expect(page.locator('.cabinet-devices')).toContainText('演示排列');
    await page.getByRole('button', { name: '返回机房', exact: true }).click();
  }
  await page.locator('.topo-layer').filter({ hasText: '终端层' }).click();
  await expect(page.locator('.topo-node')).toHaveCount(9);
  await expect(page.locator('.topo-edge')).toHaveCount(8);
  // A vertical SVG path has a zero-width bounding box; use its supported keyboard action.
  await page.locator('.topo-edge').first().focus();
  await page.locator('.topo-edge').first().press('Enter');
  await expect(page.locator('#topo-detail')).toContainText('非真实物理链路');
});

test('slow topology location cannot extract a stale device after switching cabinets', async ({
  page,
  request,
}) => {
  const rooms = await (await request.get('http://localhost:8080/api/v1/rooms')).json();
  const snapshot = (
    await (
      await request.get(`http://localhost:8080/api/v1/rooms/${rooms.data.items[0].id}/scene`)
    ).json()
  ).data;
  const id = snapshot.cabinets.find((c: any) => c.code === 'A-02').id;
  await open(page);
  await business(page, '网络拓扑');
  await page.locator('#topo-search').fill('SRV-03');
  await page.route(`**/api/v1/cabinets/${id}*`, async (route) => {
    await new Promise((r) => setTimeout(r, 1200));
    await route.continue();
  });
  await page.locator('#topo-detail .primary').click();
  await page.getByRole('button', { name: '返回机房', exact: true }).click();
  await business(page, '机房运维');
  await choose(page, 'A区 01号机柜');
  await expect(page.locator('.cabinet-devices')).toContainText('A-01 · 已登记 6 台');
  await page.waitForTimeout(1600);
  await expect(page.locator('.cabinet-devices')).toContainText('A-01 · 已登记 6 台');
  await expect(page.locator('.cabinet-devices button.selected')).toHaveCount(0);
  await expect(page.locator('.room-scene .scene-canvas')).toHaveAttribute(
    'data-extracted-device',
    '',
  );
});

test('failed inventory and failed asset detail do not fall back to mock counts or stale detail', async ({
  page,
}) => {
  await page.route('**/api/v1/**', (route) => route.abort());
  await page.goto('/home');
  await business(page, '资产总览');
  await expect(page.locator('.asset-pie')).toHaveAttribute('data-total', '--');
  await expect(page.locator('.asset-capacity-card')).not.toContainText('248');
  await expect(page.locator('.room-scene')).toHaveCount(0);
  await page.unroute('**/api/v1/**');
  await page.getByRole('button', { name: '重试台账查询' }).click();
  await expect(page.locator('.asset-pie')).toHaveAttribute('data-total', '129 台');
  await page.locator('.asset-location').first().click();
  await expect(page.locator('#asset-detail')).toContainText('已登记设备6 台');
  await page.route('**/api/v1/cabinets/**', (route) => route.abort());
  await page.locator('.asset-location').nth(1).click();
  await expect(page.locator('#asset-detail')).toContainText('台账API连接失败');
  await expect(page.locator('#asset-detail')).not.toContainText('已登记设备6 台');
});

test('unregistered topology identities stay as demos and cannot trigger default locations', async ({
  page,
}) => {
  await page.route('**/api/v1/rooms/*/scene?*', async (route) => {
    const response = await route.fetch(),
      body = await response.json();
    body.data.devices = body.data.devices.filter((d: any) => d.legacyKey !== 'srv3');
    await route.fulfill({ response, json: body });
  });
  await open(page);
  await business(page, '网络拓扑');
  await page.locator('#topo-search').fill('SRV-03');
  await expect(page.locator('#topo-detail')).toContainText('未关联');
  await expect(page.locator('#topo-detail .primary')).toBeDisabled();
  await expect(page.locator('.cabinet-devices')).toHaveCount(0);
});

for (const [width, height] of [
  [1920, 1080],
  [1366, 768],
]) {
  test(`page descriptions and original interactions fit ${width}x${height}`, async ({ page }) => {
    await page.setViewportSize({ width, height });
    await open(page);
    await mkdir('../../docs/a12-evidence', { recursive: true });
    for (const [name, key] of [
      ['资产总览', 'assets'],
      ['网络拓扑', 'topology'],
      ['告警中心', 'assistant'],
    ]) {
      await business(page, name);
      if (key === 'assets') await page.locator('.asset-location').first().click();
      if (key === 'assistant') {
        await page.getByRole('textbox', { name: '巡检助手指令' }).fill('查看 A-02 的状态');
        await page.getByRole('button', { name: '发送', exact: true }).click();
        await expect(page.getByRole('log')).toContainText('模拟功率');
      }
      await page.waitForTimeout(350);
      await page.screenshot({ path: `../../docs/a12-evidence/after-${width}-${key}.png` });
      const overflow = await page.evaluate(() =>
        Array.from(
          document.querySelectorAll(
            '.asset-type-card,.asset-capacity-card,.network-detail-card,.inspection-assistant',
          ),
        )
          .filter((el) => (el as HTMLElement).offsetParent !== null)
          .map((el) => ({
            name: el.className,
            offscreen:
              el.getBoundingClientRect().right > window.innerWidth ||
              el.getBoundingClientRect().bottom > window.innerHeight + 1,
          })),
      );
      expect(overflow.every((item) => !item.offscreen)).toBe(true);
    }
    await page.getByRole('button', { name: '机柜台账详情 ↗', exact: true }).click();
    await expect(page.locator('.cabinet-devices')).toContainText('A-02 · 已登记 3 台');
    await page.locator('.cabinet-devices button').first().click();
    await expect(page.locator('.cabinet-devices')).toContainText('U 位：未登记');
    await expect(page.locator('.cabinet-devices')).toContainText('演示台账');
    await expect(page.getByRole('button', { name: '关闭柜门', exact: true })).toBeVisible();
    await page.getByRole('button', { name: '关闭柜门', exact: true }).click();
    await expect(page.getByRole('button', { name: '打开柜门', exact: true })).toBeVisible();
    await page.screenshot({ path: `../../docs/a12-evidence/after-${width}-cabinet.png` });
  });
}
