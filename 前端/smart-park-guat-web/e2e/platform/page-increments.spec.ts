import { test, expect, type Page } from '@playwright/test';
import { mockData } from '../../src/views/home/demo/mockData.js';
import { mockConsole, loginAs } from './console-fixture';

const demo = mockData as any;
const meta = {
  recordOrigin: 'DEMO',
  source: 'MOCK_IMPORT',
  quality: 'GOOD',
  sourceUpdatedAt: null,
  generatedAt: new Date().toISOString(),
};
const zones = demo.zones.map((z: any, index: number) => ({
  id: String(index + 1),
  code: z.id,
  name: z.id + '区',
  displayOrder: index,
  meta,
}));
const cabinets = demo.devices.map((d: any, index: number) => ({
  id: String(index + 100),
  roomId: '1',
  zoneId: zones.find((z: any) => z.code === d.zone).id,
  zoneCode: d.zone,
  code: d.id,
  name: d.id + '机柜',
  sceneModelKey: d.id,
  capacityU: 42,
  registeredDeviceCount: 1,
  registrationStatus: 'REGISTERED',
  runtimeStatus: 'UNKNOWN',
  position: d.position,
  gridRow: d.row,
  gridColumn: d.column,
  roomName: '验证机房',
  location: '1F',
  meta,
}));
const devices = cabinets.map((c: any, index: number) => ({
  id: String(index + 1000),
  cabinetId: c.id,
  code: `SRV-${index + 1}`,
  name: `服务器 ${index + 1}`,
  type: 'SERVER',
  sceneModelKey: `device-${index}`,
  legacyKey: `device-${index}`,
  registrationStatus: 'REGISTERED',
  runtimeStatus: 'UNKNOWN',
  uStart: null,
  uHeight: null,
  meta,
}));
const room = {
  id: '1',
  parkId: '1',
  parkName: '验证园区',
  code: 'R1',
  name: '验证机房',
  location: '1F',
  sceneModelKey: 'room',
  zoneCount: 5,
  cabinetCount: 42,
  registeredDeviceCount: 42,
  meta,
};
async function setup(page: Page) {
  await mockConsole(page);
  await page.route('**/api/v1/**', (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!/^\/api\/v1\/(rooms|cabinets)(\/|$)/.test(path)) return route.fallback();
    let data: unknown;
    if (path === '/api/v1/rooms') data = { items: [room], total: 1 };
    else if (path === '/api/v1/rooms/1') data = room;
    else if (path.endsWith('/scene')) data = { roomId: '1', zones, cabinets, devices, meta };
    else if (path === '/api/v1/rooms/1/cabinets') data = { items: cabinets, total: 42 };
    else if (/\/cabinets\/\d+\/devices$/.test(path))
      data = { items: devices.filter((d: any) => d.cabinetId === path.split('/')[4]), total: 1 };
    else data = cabinets.find((c: any) => c.id === path.split('/')[4]);
    return route.fulfill({ json: { code: 200, data } });
  });
  await page.goto('/home');
  await expect(page.locator('.scene-canvas canvas')).toHaveCount(1);
}
async function business(page: Page, name: string) {
  await page.locator('.action-bottom').getByRole('button', { name, exact: true }).click();
}

test('facility location, cabinet controls, reset and repeated navigation', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await setup(page);
  await expect(page.locator('.list-section')).toHaveCount(3);
  await page.locator('.list-header').filter({ hasText: '空调' }).click();
  await page.locator('.list-item').filter({ hasText: '精密空调 01' }).click();
  await expect(page.locator('.scene-canvas')).toHaveAttribute('data-facility', 'ac-1');
  await page.locator('.list-header').filter({ hasText: '摄像头' }).click();
  await page.locator('.list-item').filter({ hasText: 'A区摄像头' }).click();
  await expect(page.locator('.scene-canvas')).toHaveAttribute('data-facility', 'zone-a');
  await page.locator('.list-header').filter({ hasText: '机柜' }).click();
  await page.locator('.list-item').filter({ hasText: 'A-01机柜' }).click();
  await expect(page.locator('.cabinet-devices')).toContainText('服务器');
  await page.getByRole('button', { name: '重置视角', exact: true }).click();
  await expect(page.locator('.cabinet-devices')).toBeVisible();
  const power = page.getByRole('switch', { name: /设备电源/ });
  await power.click();
  await expect(page.locator('.scene-canvas')).toHaveAttribute('data-power', 'false');
  await page.getByRole('button', { name: '打开柜门', exact: true }).click();
  const lock = page.getByRole('switch', { name: /柜门门禁/ });
  await lock.click();
  await expect(page.getByText('请先关闭柜门，再锁定门禁')).toBeVisible();
  await expect(lock).toHaveAttribute('aria-checked', 'false');
  await page.getByRole('button', { name: '关闭柜门', exact: true }).click();
  await lock.click();
  await expect(page.getByRole('button', { name: '柜门已锁定', exact: true })).toBeDisabled();
  await business(page, '机房运维');
  await expect(page.locator('.room-basic-info')).toBeVisible();
  await page.locator('.list-item').filter({ hasText: 'A-01机柜' }).click();
  await business(page, '机房运维');
  await expect(page.locator('.cabinet-devices')).toHaveCount(0);
  expect(errors).toEqual([]);
});

test('energy classification shares the selected range and top two with the trend', async ({
  page,
}) => {
  await setup(page);
  await business(page, '能效管理');
  await expect(page.locator('.mix-legend')).toContainText('监控');
  await page.getByRole('button', { name: '30天', exact: true }).click();
  await expect(page.locator('.mix').locator('[role="img"]')).toHaveAttribute(
    'aria-label',
    /30天分类能耗/,
  );
  const result = await page.evaluate(async () => {
    const { energyData } = await import('/src/views/home/cockpit/energy/energy-data.ts');
    const data = energyData.value;
    return {
      total: data.total,
      sum: data.shares.reduce((sum, item) => sum + item.consumption, 0),
      top: data.topTwo.map((item) => item.name),
    };
  });
  expect(result.sum).toBe(result.total);
  for (const name of result.top) await expect(page.locator('.trend-legend')).toContainText(name);
});

test('asset counts, cabinet return, demo playback and topology search', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await setup(page);
  await business(page, '资产总览');
  await expect(page.locator('.asset-legend')).toContainText('机柜 42 台');
  await expect(page.locator('.asset-legend')).toContainText('摄像头（演示） 5 台');
  await expect(page.locator('.asset-legend')).toContainText('空调（演示） 10 台');
  await expect(page.locator('.demo-badge')).toContainText('非实时监控');
  await expect
    .poll(() => page.locator('video').evaluate((video) => (video as HTMLVideoElement).readyState))
    .toBeGreaterThanOrEqual(2);
  await expect(page.locator('.room-scene .zone-label').first()).toBeVisible();
  await page.locator('.room-scene').screenshot({ path: 'test-results/asset-scene-only.png' });
  await page.locator('.asset-location').first().click();
  await expect(page.locator('#asset-detail')).toContainText('机柜编号');
  await page.locator('.asset-return').click();
  await expect(page.locator('#asset-detail')).toContainText('点击机柜');
  await business(page, '网络拓扑');
  await page.locator('#topo-search').fill('no-such-device');
  await expect(page.locator('#topo-tip')).toContainText('未找到匹配设备');
  expect(errors).toEqual([]);
});

test('assistant order reaches maintenance, survives reload and cannot fake real retest', async ({
  page,
}) => {
  await setup(page);
  await business(page, '告警中心');
  const row = page.locator('.active-alarms article').filter({ hasText: 'A-02' });
  await row.getByRole('button', { name: '创建工单', exact: true }).click();
  await expect(page.getByRole('log')).toContainText('已创建工单');
  await page.getByRole('button', { name: '按方案安排处理', exact: true }).click();
  await expect(page.getByRole('log')).toContainText('不会重复创建');
  await loginAs(page, 'MAINTAINER');
  await page
    .locator('.maintenance-sidebar')
    .getByRole('button', { name: '工单中心', exact: false })
    .click();
  await page.getByRole('button', { name: '待领取工单', exact: true }).click();
  await page.getByRole('button', { name: '详情', exact: true }).click();
  await page.getByRole('button', { name: '领取工单', exact: true }).click();
  await page.getByRole('button', { name: '开始处理', exact: true }).click();
  await page.getByRole('textbox', { name: '处理过程及措施' }).fill('检查风道并清理滤网');
  await page.getByRole('textbox', { name: '处理结果' }).fill('已完成清理，等待温度数据');
  await page.getByRole('button', { name: '提交复测', exact: true }).click();
  await expect(page.getByRole('dialog', { name: '工单详情' })).toContainText('待复测');
  await page.reload();
  await expect(page.locator('tbody')).toContainText('待复测');
  await loginAs(page, 'ADMIN');
  await business(page, '告警中心');
  await page.getByRole('textbox', { name: '巡检助手指令' }).fill('复测并归档 A-02');
  await page.getByRole('button', { name: '发送', exact: true }).click();
  await expect(page.getByRole('log')).toContainText('真实监测数据接口未接入');
  await expect(page.locator('.active-alarms')).toContainText('待复测');
});

test('five pages stay usable at 1366x768 and manual order creation is preserved', async ({
  page,
}) => {
  await page.setViewportSize({ width: 1366, height: 768 });
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await setup(page);
  for (const [name, selector, file] of [
    ['机房运维', '.device-list', 'operations'],
    ['能效管理', '.energy-card', 'energy'],
    ['告警中心', '.inspection-assistant', 'alarms'],
    ['资产总览', '.asset-legend', 'assets'],
    ['网络拓扑', '#topo-svg', 'topology'],
  ]) {
    await business(page, name!);
    await expect(page.locator(selector!).first()).toBeVisible();
    if (name === '资产总览') {
      await expect
        .poll(() =>
          page
            .locator('.scene-canvas')
            .evaluate((element) => element.getBoundingClientRect().height),
        )
        .toBeGreaterThan(300);
      await expect
        .poll(() => page.locator('.scene-canvas').getAttribute('data-draw-calls'))
        .not.toBeNull();
      await expect(page.locator('.room-scene .zone-label').first()).toBeVisible();
      await page.locator('.room-scene').screenshot({ path: 'test-results/asset-scene-1366.png' });
    }
    await page.screenshot({ path: `test-results/page-${file}-1366.png` });
  }
  await page.goto('/home?business=alarm&orders=all');
  await page.getByRole('button', { name: '新建工单', exact: true }).click();
  await page.getByRole('textbox', { name: '工单标题' }).fill('人工报修验证');
  await page.getByRole('textbox', { name: '工单设备或位置' }).fill('A-01');
  await page.getByRole('button', { name: '创建', exact: true }).click();
  await expect(page.locator('tbody')).toContainText('人工报修验证');
  await page.reload();
  await expect(page.locator('tbody')).toContainText('人工报修验证');
  expect(errors).toEqual([]);
});
