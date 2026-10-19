import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
const base = process.env.STAGE1_API_URL || 'http://localhost:8080/api/v1';
const results = [];
async function get(path, status = 200) {
  const response = await fetch(base + path),
    body = await response.json();
  assert.equal(response.status, status, path);
  assert.equal(body.code, status, path);
  assert.equal(typeof body.requestId, 'string');
  assert.ok(body.serverTime.endsWith('Z'));
  if (status !== 200) {
    assert.ok(body.error);
    assert.ok(body.msg);
    assert.ok(!JSON.stringify(body).includes('jdbc:mysql'));
  }
  results.push({ path, status });
  return body.data;
}
const rooms = await get('/rooms'),
  room = await get('/rooms/' + rooms.items[0].id);
const scene = await get('/rooms/' + room.id + '/scene');
assert.equal(new Set(scene.cabinets.map((c) => c.id)).size, 42);
assert.equal(new Set(scene.devices.map((d) => d.id)).size, 129);
for (const row of [...rooms.items, ...scene.zones, ...scene.cabinets, ...scene.devices])
  assert.equal(typeof row.id, 'string');
for (const d of scene.devices) {
  assert.equal(d.runtimeStatus, 'UNKNOWN');
  assert.equal(d.meta.recordOrigin, 'DEMO');
  assert.equal(d.uStart, null);
  assert.equal(d.uHeight, null);
  assert.ok(scene.cabinets.some((c) => c.id === d.cabinetId));
  assert.equal(typeof d.cabinetId, 'string');
}
for (const path of [
  '/rooms/not-an-id',
  '/cabinets/not-an-id',
  '/cabinets/0',
  '/rooms?pageSize=101',
  '/rooms?recordOrigin=ALL',
  '/rooms?sort=password,asc',
])
  await get(path, 400);
for (const path of [
  '/rooms/999999',
  '/rooms/999999/scene',
  '/rooms/999999/cabinets',
  '/cabinets/999999',
  '/cabinets/999999/devices',
])
  await get(path, 404);
await writeFile(
  '../../docs/a13-evidence/api-errors-and-contract.json',
  JSON.stringify(
    {
      checkedAt: new Date().toISOString(),
      results,
      uniqueCabinets: 42,
      uniqueDevices: 129,
      types: Object.fromEntries(
        ['SERVER', 'SWITCH', 'STORAGE', 'OTHER'].map((t) => [
          t,
          scene.devices.filter((d) => d.type === t).length,
        ]),
      ),
    },
    null,
    2,
  ),
);
console.log(
  `PASS: ${results.length} real HTTP checks, 400/404 envelopes, string IDs, 42/129 identities and UNKNOWN/null`,
);
