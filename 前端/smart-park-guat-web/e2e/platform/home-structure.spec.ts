import { test, expect, type Page } from '@playwright/test';
import { mockConsole } from './console-fixture';
import { mockData } from '../../src/views/home/demo/mockData.js';

async function setupHome(page: Page) {
  await mockConsole(page);
  const meta = {
    recordOrigin: 'REAL',
    source: 'INVENTORY',
    quality: 'GOOD',
    sourceUpdatedAt: null,
    generatedAt: '',
  };
  const room = {
    id: '1',
    parkId: '1',
    name: '结构回归机房',
    code: 'R1',
    sceneModelKey: 'room',
    zoneCount: 5,
    cabinetCount: 42,
    registeredDeviceCount: 1,
    meta,
  };
  const zones = mockData.zones.map((zone, i) => ({
    id: String(i + 1),
    code: zone.id,
    name: `${zone.id}区`,
    displayOrder: i,
    meta,
  }));
  const cabinets = mockData.devices.map((cabinet, i) => ({
    id: String(i + 100),
    roomId: '1',
    zoneId: zones.find((z) => z.code === cabinet.zone)!.id,
    zoneCode: cabinet.zone,
    code: cabinet.id,
    name: `${cabinet.id}机柜`,
    sceneModelKey: cabinet.id,
    capacityU: 42,
    registeredDeviceCount: i === 0 ? 1 : 0,
    registrationStatus: 'REGISTERED',
    runtimeStatus: 'UNKNOWN',
    position: cabinet.position,
    gridRow: cabinet.row,
    gridColumn: cabinet.column,
    roomName: room.name,
    meta,
  }));
  const device = {
    id: '1000',
    cabinetId: '100',
    code: 'SRV-1',
    name: '回归服务器',
    type: 'SERVER',
    sceneModelKey: 'regression-server',
    registrationStatus: 'REGISTERED',
    runtimeStatus: 'UNKNOWN',
    uStart: 1,
    uHeight: 2,
    meta,
  };
  const monitoring = {
    roomId: '1',
    generatedAt: '',
    readings: [],
    alarms: [],
    facilities: [],
    events: [],
    nodes: [],
    links: [],
    logs: [],
    controls: [],
  };
  const energy = {
    labels: [],
    totals: [],
    total: null,
    shares: [],
    ranks: [],
    available: false,
    partial: false,
  };
  await page.route('**/api/v1/**', (route) => {
    const pathname = new URL(route.request().url()).pathname;
    let data: unknown;
    if (pathname === '/api/v1/rooms') data = { items: [room], total: 1 };
    else if (pathname === '/api/v1/rooms/1') data = room;
    else if (pathname.endsWith('/scene'))
      data = { roomId: '1', zones, cabinets, devices: [device], meta };
    else if (pathname === '/api/v1/rooms/1/cabinets')
      data = { items: cabinets, total: cabinets.length };
    else if (pathname.endsWith('/monitoring')) data = monitoring;
    else if (pathname.endsWith('/energy')) data = energy;
    else if (pathname === '/api/v1/cabinets/100') data = cabinets[0];
    else if (pathname === '/api/v1/cabinets/100/devices') data = { items: [device], total: 1 };
    else return route.fallback();
    return route.fulfill({ json: { code: 200, data } });
  });
  await page.goto('/home');
  await expect(page.locator('.room-basic-info')).toContainText(room.name);
  await expect(page.locator('.scene-canvas canvas')).toHaveCount(1);
}

test('cockpit switching preserves the scene, card layout and cabinet navigation', async ({
  page,
}) => {
  test.setTimeout(90000);
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await setupHome(page);
  const switchBusiness = (name: string) =>
    page.locator('.action-bottom').getByRole('button', { name, exact: true }).click();
  await expect(page.locator('.home-side--left > .border-card')).toHaveCount(2);
  await expect(page.locator('.home-side--right > .border-card')).toHaveCount(3);
  await page
    .locator('.scene-canvas canvas')
    .evaluate((canvas) => canvas.setAttribute('data-regression-scene', 'original'));
  await switchBusiness('能效管理');
  await expect(page.locator('.home-side--left > .border-card')).toHaveCount(3);
  await expect(page.locator('.home-side--right > .border-card')).toHaveCount(3);
  await expect(page.locator('.energy-overview-card')).toBeVisible();
  const periods = page.getByLabel('能耗趋势时间范围');
  await expect(periods.getByRole('button')).toHaveCount(2);
  await expect(periods.getByRole('button', { name: '7天', exact: true })).toHaveAttribute(
    'aria-pressed',
    'true',
  );
  await expect(periods.getByRole('button', { name: '今日', exact: true })).toHaveCount(0);
  await expect(page.locator('.pue-info')).toContainText('7天平均 PUE');
  const monthRequest = page.waitForRequest(
    (request) =>
      new URL(request.url()).pathname.endsWith('/energy') &&
      new URL(request.url()).searchParams.get('period') === '30d',
  );
  await periods.getByRole('button', { name: '30天', exact: true }).click();
  await monthRequest;
  await expect(periods.getByRole('button', { name: '30天', exact: true })).toHaveAttribute(
    'aria-pressed',
    'true',
  );
  await expect(page.locator('.pue-info')).toContainText('30天平均 PUE');
  await switchBusiness('告警中心');
  await expect(page.locator('.home-side--left > .border-card')).toHaveCount(2);
  await expect(page.locator('.home-side--right > .border-card')).toHaveCount(1);
  await expect(page.locator('.inspection-assistant')).toBeVisible();
  await expect(page.locator('.scene-canvas canvas')).toHaveAttribute(
    'data-regression-scene',
    'original',
  );
  await switchBusiness('资产总览');
  await expect(page.locator('.asset-capacity-card')).toContainText('42');
  await switchBusiness('网络拓扑');
  await expect(page.getByRole('textbox', { name: '搜索网络设备', exact: true })).toBeVisible();
  await switchBusiness('机房运维');
  await expect(page.locator('.room-basic-info')).toBeVisible();
  const group = page.locator('.list-header').filter({ hasText: '机柜' });
  if ((await group.getAttribute('aria-expanded')) !== 'true') await group.click();
  await page.locator('.list-item').filter({ hasText: 'A-01机柜' }).click();
  await expect(page.locator('.cabinet-devices')).toContainText('回归服务器');
  await expect(page.locator('.home-side--left > .border-card')).toHaveCount(3);
  await expect(page.locator('.home-side--right > .border-card')).toHaveCount(3);
  await page.getByRole('button', { name: '重置视角', exact: true }).click();
  await expect(page.locator('.cabinet-devices')).toBeVisible();
  await page.getByRole('button', { name: '返回首页', exact: true }).click();
  await expect(page.locator('.room-basic-info')).toBeVisible();
  await expect(page.locator('.cabinet-devices')).toHaveCount(0);
  await page.screenshot({ path: 'test-results/home-structure-overview.png' });
  expect(errors).toEqual([]);
});
