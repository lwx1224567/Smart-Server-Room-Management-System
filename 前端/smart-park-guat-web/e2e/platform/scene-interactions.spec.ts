import { test, expect } from '@playwright/test';
import { mockConsole } from './console-fixture';

test('typed scene preserves inventory inspection, extraction, doors and disposal', async ({
  page,
}) => {
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await mockConsole(page);
  await page.goto('/home');
  await page.evaluate(async () => {
    const vuePath = '/node_modules/.vite/deps/vue.js';
    const componentPath = '/src/views/home/scene/RoomScene.vue';
    const dataPath = '/src/views/home/demo/mockData.js';
    const vue = await import(vuePath);
    const data = (await import(dataPath)).mockData;
    const host = document.createElement('div');
    host.id = 'scene-interactions-test';
    host.style.cssText =
      'position:fixed;inset:20px auto auto 20px;width:900px;height:650px;z-index:99999';
    document.body.append(host);
    const app = vue.createApp((await import(componentPath)).default, {
      sceneData: {
        devices: data.devices,
        zones: data.zones,
        inventory: [
          {
            id: 'server-1',
            label: '服务器',
            sub: '服务器 · 台账',
            cabinetId: 'A-01',
            layer: 'terminal',
          },
          {
            id: 'switch-1',
            label: '交换机',
            sub: '交换机 · 台账',
            cabinetId: 'A-01',
            layer: 'access',
          },
        ],
        controls: {},
        alarms: {},
        platform: true,
      },
    });
    const scene = app.mount(host);
    (window as any).sceneInteractionsTest = { app, scene };
  });
  const host = page.locator('#scene-interactions-test');
  await expect(host.locator('canvas')).toBeVisible();
  const initial = await page.evaluate(() => {
    const { scene } = (window as any).sceneInteractionsTest;
    scene.inspect('A-01');
    return scene.getState();
  });
  expect(initial.inspected).toBe('A-01');
  expect(initial.inventory).toEqual([
    { id: 'server-1', kind: 'server' },
    { id: 'switch-1', kind: 'ports' },
  ]);
  expect(
    await page.evaluate(() =>
      (window as any).sceneInteractionsTest.scene.extractDevice('server-1'),
    ),
  ).toBe(true);
  await expect(host.locator('#sceneCanvas')).toHaveAttribute('data-extracted-device', 'server-1');
  await expect(host.locator('#sceneCanvas')).toHaveAttribute('data-door-angle', /-1\.91/);
  await page.evaluate(() =>
    (window as any).sceneInteractionsTest.scene.setDeviceDoor('A-01', false),
  );
  await expect(host.locator('#sceneCanvas')).toHaveAttribute('data-door-angle', '0.000');
  await expect(host.locator('#sceneCanvas')).toHaveAttribute('data-extracted-device', '');
  expect(
    await page.evaluate(() => {
      const { scene } = (window as any).sceneInteractionsTest;
      scene.overview();
      return scene.getState().inventory;
    }),
  ).toEqual([]);
  await page.evaluate(() => (window as any).sceneInteractionsTest.app.unmount());
  await expect(host.locator('canvas')).toHaveCount(0);
  expect(errors).toEqual([]);
});
