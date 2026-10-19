import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
const base = process.env.STAGE1_API_URL || 'http://localhost:8080/api/v1';
const results = [];
async function get(path) {
  const response = await fetch(base + path);
  const body = await response.json();
  assert.equal(response.status, 200, path);
  assert.equal(body.code, 200);
  assert.equal(typeof body.requestId, 'string');
  assert.ok(body.serverTime.endsWith('Z'));
  results.push({ path, status: response.status });
  return body.data;
}
const rooms = await get('/rooms');
assert.equal(rooms.total, 1);
const room = await get('/rooms/' + rooms.items[0].id);
assert.equal(room.cabinetCount, 42);
assert.equal(room.zoneCount, 5);
assert.equal(room.registeredDeviceCount, 129);
const scene = await get('/rooms/' + room.id + '/scene');
assert.equal(scene.cabinets.length, 42);
assert.equal(scene.zones.length, 5);
assert.equal(scene.devices.length, 129);
const cabinets = await get('/rooms/' + room.id + '/cabinets?pageSize=100');
assert.equal(cabinets.total, 42);
const grouped = {};
for (const cabinet of scene.cabinets) {
  assert.equal(typeof cabinet.id, 'string');
  assert.equal(cabinet.runtimeStatus, 'UNKNOWN');
  const detail = await get('/cabinets/' + cabinet.id);
  const devices = await get('/cabinets/' + cabinet.id + '/devices');
  assert.equal(detail.roomId, room.id);
  assert.equal(detail.usedU, null);
  assert.equal(detail.telemetry.runtimeStatus, 'UNKNOWN');
  assert.equal(detail.telemetry.powerState, 'UNKNOWN');
  assert.equal(detail.telemetry.networkState, 'UNKNOWN');
  assert.equal(detail.telemetry.temperature.value, null);
  assert.equal(detail.telemetry.power.value, null);
  const expected = cabinet.code === 'A-01' ? 6 : 3;
  assert.equal(devices.total, expected);
  assert.equal(detail.registeredDeviceCount, expected);
  for (const d of devices.items) {
    assert.equal(d.cabinetId, cabinet.id);
    assert.equal(typeof d.id, 'string');
    assert.equal(d.runtimeStatus, 'UNKNOWN');
    assert.equal(d.meta.recordOrigin, 'DEMO');
  }
  grouped[cabinet.code] = devices.total;
}
const report = {
  checkedAt: new Date().toISOString(),
  sixEndpointKindsPassed: 6,
  requests: results,
  counts: { parks: 1, rooms: 1, zones: 5, cabinets: 42, devices: 129 },
  registeredDevicesByCabinet: grouped,
};
await writeFile('test-results/stage1-api-results.json', JSON.stringify(report, null, 2));
console.log(
  'PASS: six real HTTP endpoint kinds; 42 cabinet details; 42 device lists; 5 zones/42 cabinets/129 devices; UNKNOWN/null verified',
);
