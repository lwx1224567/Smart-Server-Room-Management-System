import { test, expect } from '@playwright/test';
import { mockConsole } from './console-fixture';

test('plain peripheral zone labels allow direct A-01 picking', async ({ page }) => {
  await mockConsole(page);
  await page.goto('/home');
  await page.evaluate(async () => {
    const vuePath = '/node_modules/.vite/deps/vue.js',
      scenePath = '/src/views/home/scene/RoomScene.vue';
    const host = document.createElement('div');
    host.id = 'zone-picking-test';
    host.style.cssText =
      'position:fixed;inset:20px auto auto 20px;width:900px;height:650px;z-index:99999;background:#061b35';
    document.body.append(host);
    (window as any).zoneScene = (await import(vuePath))
      .createApp((await import(scenePath)).default)
      .mount(host);
  });
  const scene = page.locator('#zone-picking-test');
  await expect(scene.locator('canvas')).toBeVisible();
  await expect(scene.locator('.zone-label')).toHaveCount(5);
  expect(
    await scene
      .locator('.zone-label')
      .first()
      .evaluate((el) => getComputedStyle(el).pointerEvents),
  ).toBe('none');
  await expect(scene.locator('.zone-label button')).toHaveCount(0);
  const point = await page.evaluate(async () => {
    const threePath = '/src/views/home/scene/vendor/three.module.js',
      dataPath = '/src/views/home/demo/mockData.js';
    const THREE = await import(threePath),
      data = (await import(dataPath)).mockData;
    const state = (window as any).zoneScene.getState();
    const device = data.devices.find((d: any) => d.id === 'A-01');
    const rect = document.querySelector('#zone-picking-test canvas')!.getBoundingClientRect();
    const camera = new THREE.PerspectiveCamera(35, rect.width / rect.height, 0.1, 120);
    camera.position.fromArray(state.camera);
    camera.lookAt(new THREE.Vector3(...state.target));
    camera.updateMatrixWorld();
    const p = new THREE.Vector3(device.position[0], 3.2, device.position[2]).project(camera);
    return {
      x: rect.left + (p.x * 0.5 + 0.5) * rect.width,
      y: rect.top + (-p.y * 0.5 + 0.5) * rect.height,
    };
  });
  await page.mouse.move(point.x, point.y);
  await expect(scene.locator('.room-viewport')).toHaveAttribute('data-highlight-device', 'A-01');
  await expect(scene.locator('.device-hover')).toContainText('A-01');
  await scene.screenshot({ path: 'test-results/zone-labels-peripheral.png' });
  // The cabinet roof remains a direct canvas target, without an intercepting overlay.
  expect(
    await page.evaluate((point) => document.elementFromPoint(point.x, point.y)?.tagName, point),
  ).toBe('CANVAS');
  await page.mouse.click(point.x, point.y);
  await expect(scene.locator('.scene-canvas')).toHaveAttribute('data-device', 'A-01');
});
