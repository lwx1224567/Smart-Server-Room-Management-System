import { test, expect } from '@playwright/test';

// These two tests use a real backend. Never send anonymous requests to admin-only inventory APIs.
const apiBase = process.env.STAGE1_API_BASE_URL || 'http://localhost:8080';
const adminUser = process.env.STAGE1_ADMIN_USER;
const adminPassword = process.env.STAGE1_ADMIN_PASSWORD;
let token = '';
test.skip(
  !adminUser || !adminPassword,
  '真实台账回归需要 STAGE1_ADMIN_USER / STAGE1_ADMIN_PASSWORD',
);
test.beforeEach(async ({ request, page }) => {
  const response = await request.post(`${apiBase}/api/v1/auth/login`, {
    data: { userName: adminUser, password: adminPassword },
  });
  expect(response.ok()).toBe(true);
  token = (await response.json()).data.token;
  await page.addInitScript((token) => sessionStorage.setItem('smart-room-session', token), token);
  await page.route('**/api/v1/**', (route) => {
    const url = new URL(route.request().url());
    return route.continue({ url: `${apiBase}${url.pathname}${url.search}` });
  });
});
test.afterEach(async ({ request }) => {
  if (token)
    await request.post(`${apiBase}/api/v1/auth/logout`, {
      headers: { Authorization: `Bearer ${token}` },
    });
  token = '';
});
test('all 42 scene mappings preserve coordinates and all device models resolve', async ({
  page,
  request,
}) => {
  const headers = { Authorization: `Bearer ${token}` };
  const rooms = await (await request.get(`${apiBase}/api/v1/rooms`, { headers })).json();
  const snapshot = await (
    await request.get(`${apiBase}/api/v1/rooms/${rooms.data.items[0].id}/scene`, { headers })
  ).json();
  await page.goto('/home');
  const result = await page.evaluate(async (snapshot) => {
    const { adaptPlatformScene } = await import('/src/views/home/scene/platform-scene-adapter.ts');
    const { mockData } = await import('/src/views/home/demo/mockData.js');
    const { buildPlatformIdentityIndex } = await import('/src/utils/platform-identity.ts');
    const adapted = adaptPlatformScene(snapshot);
    const identities = buildPlatformIdentityIndex(snapshot);
    return {
      cabinets: adapted.devices.map((c) => ({
        key: c.id,
        id: c.databaseId,
        ref: c.databaseRef,
        position: c.position,
        legacy: mockData.devices.find((v) => v.id === c.id)?.position,
      })),
      devices: adapted.inventory.map((d) => ({
        key: d.id,
        id: d.databaseId,
        cabinet: d.cabinetId,
        cabinetId: d.databaseCabinetId,
        ref: d.databaseRef,
      })),
      topology: ['core', 'agg', 'sw1', 'sw2', 'srv1', 'srv2', 'nas', 'srv3', 'srv4'].map((key) => ({
        key,
        resolution: identities.resolveTopologyNode(key),
      })),
    };
  }, snapshot.data);
  expect(result.cabinets).toHaveLength(42);
  for (const c of result.cabinets) {
    expect(c.legacy).toBeDefined();
    c.position.forEach((v: number, i: number) => expect(v).toBeCloseTo(c.legacy[i], 5));
  }
  expect(result.devices).toHaveLength(129);
  expect(result.devices.filter((d) => d.cabinet === 'A-01')).toHaveLength(6);
  expect(result.devices.filter((d) => d.cabinet === 'A-02')).toHaveLength(3);
  expect(new Set(result.cabinets.map((c) => c.id)).size).toBe(42);
  expect(new Set(result.devices.map((d) => d.id)).size).toBe(129);
  for (const c of result.cabinets)
    expect(c.ref).toEqual({ kind: 'CABINET', cabinetId: c.id, sceneModelKey: c.key });
  for (const d of result.devices) {
    expect(typeof d.id).toBe('string');
    const cabinet = result.cabinets.find((c) => c.id === d.cabinetId)!;
    expect(cabinet).toBeDefined();
    expect(d.ref).toEqual({
      kind: 'DEVICE',
      deviceId: d.id,
      cabinetId: cabinet.id,
      sceneModelKey: d.key,
      cabinetSceneModelKey: cabinet.key,
    });
    expect(d.cabinet).toBe(cabinet.key);
  }
  for (const node of result.topology) {
    expect(node.resolution.state).toBe('LINKED');
    expect(node.resolution.ref).toEqual(result.devices.find((d) => d.key === node.key)!.ref);
  }
});

test('store identities use the full snapshot and become unavailable during reload and failure', async ({
  page,
}) => {
  await page.goto('/home');
  await expect(page.locator('.inventory-source')).toContainText('台账：数据库');
  const before = await page.evaluate(async () => {
    const { usePlatformStore } = await import('/src/stores/platform.ts');
    const store = usePlatformStore();
    await store.load();
    const id = store.scene.devices[0].id;
    const loading = store.load();
    const during = store.identityIndex.resolveDeviceId(id);
    await loading;
    const loaded = store.identityIndex.resolveDeviceId(id);
    const firstCabinet = store.cabinets[0].id;
    await store.selectCabinet(firstCabinet);
    const fullCount = store.scene.devices.filter(
      (d) => store.identityIndex.resolveDeviceId(d.id).state === 'LINKED',
    ).length;
    return { id, during, loaded, fullCount, detailCount: store.devices.length };
  });
  expect(before.during).toMatchObject({ state: 'UNLINKED', reason: 'UNAVAILABLE' });
  expect(before.loaded).toMatchObject({ state: 'LINKED' });
  expect(before.fullCount).toBe(129);
  expect(before.detailCount).toBeLessThan(129);
  await page.route('**/api/v1/rooms?*', (route) => route.abort());
  const failed = await page.evaluate(async (id) => {
    const { usePlatformStore } = await import('/src/stores/platform.ts');
    const store = usePlatformStore();
    await store.load();
    return { resolution: store.identityIndex.resolveDeviceId(id), error: store.error };
  }, before.id);
  expect(failed.error).not.toBe('');
  expect(failed.resolution).toMatchObject({ state: 'UNLINKED', reason: 'UNAVAILABLE' });
  await page.unroute('**/api/v1/rooms?*');
  const recovered = await page.evaluate(async (id) => {
    const { usePlatformStore } = await import('/src/stores/platform.ts');
    const store = usePlatformStore();
    await store.load();
    return store.identityIndex.resolveDeviceId(id);
  }, before.id);
  expect(recovered).toEqual(before.loaded);
});
