import { test, expect, type Page } from '@playwright/test';
async function overview(page: Page) {
  await page.goto('/home');
  await expect(page.getByText('台账：数据库', { exact: false })).toBeVisible();
  await expect(page.locator('.room-scene[data-platform="true"] .scene-canvas canvas')).toHaveCount(
    1,
  );
}
async function selectCabinet(page: Page, name: string) {
  const header = page.getByRole('button', { name: /机柜（数据库）/ });
  if ((await header.getAttribute('aria-expanded')) !== 'true') await header.click();
  await page.locator('.list-item').filter({ hasText: name }).click();
}
test('real API inventory, details, empty state and refresh restoration', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await overview(page);
  await expect(page.locator('.zone-label')).toHaveCount(5);
  await selectCabinet(page, 'A区 01号机柜');
  await expect(page.locator('.cabinet-devices')).toContainText('已登记 6 台');
  await expect(page.locator('.device-status')).toContainText('未采集');
  await expect(page.locator('.cabinet-devices button')).toHaveCount(6);
  await expect(page.locator('.location-row').nth(1)).toContainText('研发大厦 1F 机房');
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({ path: 'test-results/stage1-cabinet-a01.png' });
  const cabinetRequest = page.waitForRequest((r) => /\/api\/v1\/cabinets\/\d+$/.test(r.url()));
  await page.reload();
  await cabinetRequest;
  await expect(page.locator('.cabinet-devices')).toContainText('A-01 · 已登记 6 台');
  await page.getByRole('button', { name: '返回机房', exact: true }).click();
  await expect(page.locator('.room-basic-info')).toContainText('智慧园区');
  await selectCabinet(page, 'A区 02号机柜');
  await expect(page.locator('.cabinet-devices')).toContainText('已登记 3 台');
  await page.getByRole('button', { name: '返回机房', exact: true }).click();
  await selectCabinet(page, 'E区 09号机柜');
  await expect(page.locator('.cabinet-devices')).toContainText('已登记 3 台');
  expect(errors).toEqual([]);
});
test('API failure cannot fall back to successful mock inventory', async ({ page }) => {
  await page.route('**/api/v1/**', (route) => route.abort());
  await page.goto('/home');
  await expect(page.getByRole('alert')).toContainText('台账API连接失败');
  await expect(page.locator('.room-scene[data-platform="true"]')).toHaveCount(0);
  await expect(page.locator('.list-item')).toHaveCount(0);
  await page.unroute('**/api/v1/**');
  await page.getByRole('button', { name: '重试台账查询' }).click();
  await expect(page.locator('.room-scene[data-platform="true"] .zone-label')).toHaveCount(5);
});
test('invalid saved cabinet restores overview after database reload', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('platform-stage1-cabinet', '999999'));
  await overview(page);
  await expect(page.locator('.room-basic-info')).toContainText('研发大厦 1F 机房');
  await expect(page.locator('.cabinet-devices')).toHaveCount(0);
  expect(await page.evaluate(() => sessionStorage.getItem('platform-stage1-cabinet'))).toBeNull();
});
test('slow old cabinet request cannot override newer selection', async ({ page, request }) => {
  const rooms = await (await request.get('http://localhost:8080/api/v1/rooms')).json();
  const snapshot = await (
    await request.get('http://localhost:8080/api/v1/rooms/' + rooms.data.items[0].id + '/scene')
  ).json();
  const id = snapshot.data.cabinets.find((c: { code: string }) => c.code === 'A-01').id;
  await overview(page);
  await page.route('**/api/v1/cabinets/' + id + '*', async (route) => {
    await new Promise((r) => setTimeout(r, 1500));
    await route.continue();
  });
  await selectCabinet(page, 'A区 01号机柜');
  await page.getByRole('button', { name: '返回机房', exact: true }).click();
  await selectCabinet(page, 'A区 02号机柜');
  await expect(page.locator('.cabinet-devices')).toContainText('A-02 · 已登记 3 台');
  await expect
    .poll(async () => page.locator('.cabinet-devices').innerText(), { timeout: 4000 })
    .toContain('A-02');
  await page.waitForTimeout(1800);
  await expect(page.locator('.cabinet-devices')).toContainText('A-02 · 已登记 3 台');
});
test('assistant mock workflow remains isolated from inventory and missing scene actions are explicit', async ({
  page,
}) => {
  await overview(page);
  await page
    .locator('.action-bottom')
    .getByRole('button', { name: '告警中心', exact: true })
    .click();
  const send = async (command: string) => {
    await page.getByRole('textbox', { name: '巡检助手指令' }).fill(command);
    await page.getByRole('button', { name: '发送', exact: true }).click();
  };
  await send('查看 A-02 的状态');
  await expect(page.getByRole('log')).toContainText('模拟进风温度');
  await send('打开 A-02 的柜门');
  await expect(page.getByRole('log')).toContainText('未接通场景动作');
  await page.getByRole('button', { name: '按方案安排处理', exact: true }).click();
  await expect(page.getByRole('log')).toContainText('已确认 A-02 告警');
  await page.getByRole('button', { name: '检查完成', exact: true }).click();
  await page.getByRole('button', { name: '复测并归档', exact: true }).click();
  await expect(page.getByRole('log')).toContainText('模拟复测已通过');
  await page.getByRole('button', { name: '机柜台账详情 ↗', exact: true }).click();
  await expect(page.locator('.cabinet-devices')).toContainText('A-02 · 已登记 3 台');
  await expect(page.locator('.device-status')).toContainText('未采集');
  await expect(page.locator('.selection-info')).toContainText('进风温度过高（视觉演示）');
  await expect(page.locator('.room-scene')).toHaveCount(1);
});
test('existing module pages and models still render', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await overview(page);
  await page.screenshot({ path: 'test-results/stage1-overview.png' });
  await page.getByRole('button', { name: '能效管理', exact: true }).click();
  await expect(page.getByText('PUE 指标', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '告警中心', exact: true }).click();
  await expect(page.getByRole('region', { name: '小维巡检对话' })).toBeVisible();
  await page.getByRole('button', { name: '资产总览', exact: true }).click();
  await expect(page.locator('.room-scene .scene-canvas canvas')).toHaveCount(1);
  await page.getByRole('button', { name: '网络拓扑', exact: true }).click();
  await expect(page.locator('#topo-svg')).toBeVisible();
  await page.goto('/weibao');
  await expect(page.getByText('全部工单', { exact: true })).toBeVisible();
  await page.goto('/login');
  await expect(page.locator('input[type="password"]')).toBeVisible();
  await page.goto('/user');
  await expect(page.locator('.el-table')).toBeVisible();
  expect(errors).toEqual([]);
});
test('actual Three.js pointer selection and animations remain active', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await overview(page);
  const host = page.locator('.room-scene[data-platform="true"] .scene-canvas');
  await expect(host).toHaveAttribute('data-rack-count', '42');
  const rotation = await host.getAttribute('data-base-rotation');
  await expect.poll(() => host.getAttribute('data-base-rotation')).not.toBe(rotation);
  const point = await page.evaluate(async () => {
    const THREE = await import('/src/views/home/scene/vendor/three.module.js');
    const el = document.querySelector('.room-scene[data-platform="true"]') as any;
    const state = el.__vueParentComponent.exposed.getState();
    const rect = el.querySelector('.scene-canvas canvas').getBoundingClientRect();
    const camera = new THREE.PerspectiveCamera(35, rect.width / rect.height, 0.1, 120);
    camera.position.fromArray(state.camera);
    camera.lookAt(new THREE.Vector3(...state.target));
    camera.updateMatrixWorld();
    // Aim at the front panel, below the existing clickable E-zone label.
    const p = new THREE.Vector3(6, 1.3, 4.35).project(camera);
    return {
      x: rect.left + (p.x * 0.5 + 0.5) * rect.width,
      y: rect.top + (-p.y * 0.5 + 0.5) * rect.height,
    };
  });
  await page.mouse.click(point.x, point.y);
  await expect(page.locator('.cabinet-devices')).toContainText('E-09 · 已登记 3 台');
  await expect(page.locator('.room-scene .scene-canvas')).toHaveAttribute('data-device', 'E-09');
  await page.getByRole('button', { name: '返回机房', exact: true }).click();
  await expect(page.locator('.zone-label')).toHaveCount(5);
});
