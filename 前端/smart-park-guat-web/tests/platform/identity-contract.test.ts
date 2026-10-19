import { test, expect } from '@playwright/test';
import { buildPlatformIdentityIndex } from '../../src/utils/platform-identity';
import type { Device, SceneSnapshot } from '../../src/types/platform';

function fixture(): SceneSnapshot {
  const meta = {
    recordOrigin: 'DEMO' as const,
    source: 'MOCK_IMPORT',
    sourceUpdatedAt: null,
    quality: 'GOOD',
    generatedAt: '',
  };
  const rules: [string, string, Device['type']][] = [
    ['core', '71', 'SWITCH'],
    ['agg', '71', 'SWITCH'],
    ['sw1', '71', 'SWITCH'],
    ['sw2', '92', 'SWITCH'],
    ['srv1', '71', 'SERVER'],
    ['srv2', '71', 'SERVER'],
    ['nas', '71', 'STORAGE'],
    ['srv3', '92', 'SERVER'],
    ['srv4', '92', 'SERVER'],
  ];
  return {
    roomId: '41',
    zones: [{ id: '51', code: 'A', name: 'A', displayOrder: 0, meta }],
    cabinets: ['71', '92'].map((id, i) => ({
      id,
      roomId: '41',
      zoneId: '51',
      code: `A-0${i + 1}`,
      name: 'Same display name',
      sceneModelKey: `A-0${i + 1}`,
      capacityU: 42,
      registrationStatus: 'ACTIVE',
      registeredDeviceCount: i ? 3 : 6,
      runtimeStatus: 'UNKNOWN',
      meta,
    })),
    devices: rules.map(([key, cabinetId, type], i) => ({
      id: i === 0 ? '71' : String(800 + i),
      cabinetId,
      code: `TEST-${i}`,
      name: 'Same display name',
      type,
      sceneModelKey: key,
      legacyKey: key,
      registrationStatus: 'ACTIVE',
      runtimeStatus: 'UNKNOWN',
      uStart: null,
      uHeight: null,
      meta,
    })),
  };
}

test('nine explicit legacy rules resolve actual string IDs independently of names and array order', () => {
  const snapshot = fixture();
  snapshot.devices.reverse();
  const index = buildPlatformIdentityIndex(snapshot);
  for (const d of snapshot.devices) {
    expect(index.resolveTopologyNode(d.legacyKey)).toEqual({
      state: 'LINKED',
      ref: {
        kind: 'DEVICE',
        deviceId: d.id,
        cabinetId: d.cabinetId,
        sceneModelKey: d.sceneModelKey,
        cabinetSceneModelKey: d.cabinetId === '71' ? 'A-01' : 'A-02',
      },
    });
    expect(index.resolveDeviceSceneKey(d.sceneModelKey)).toEqual(index.resolveDeviceId(d.id));
  }
  expect(index.resolveCabinetSceneKey('A-02')).toEqual(index.resolveCabinetId('92'));
});

test('identical cabinet/device primary keys stay separate; no ID/key/name fallback', () => {
  const index = buildPlatformIdentityIndex(fixture());
  expect(index.resolveCabinetId('71')).toMatchObject({
    state: 'LINKED',
    ref: { kind: 'CABINET', cabinetId: '71' },
  });
  expect(index.resolveDeviceId('71')).toMatchObject({
    state: 'LINKED',
    ref: { kind: 'DEVICE', deviceId: '71' },
  });
  for (const key of ['unknown', 'Same display name', 'A-01', 'constructor', '__proto__']) {
    expect(index.resolveTopologyNode(key)).toMatchObject({
      state: 'UNLINKED',
      reason: 'NOT_REGISTERED',
    });
  }
  expect(index.resolveDeviceId('srv3')).toMatchObject({
    state: 'UNLINKED',
    reason: 'NOT_REGISTERED',
  });
  expect(index.resolveDeviceSceneKey('71')).toMatchObject({
    state: 'UNLINKED',
    reason: 'NOT_REGISTERED',
  });
  expect(index.resolveDeviceId('92')).toMatchObject({
    state: 'UNLINKED',
    reason: 'NOT_REGISTERED',
  });
});

for (const field of ['id', 'sceneModelKey', 'legacyKey'] as const) {
  test(`duplicate device ${field} rejects every lookup rather than selecting the first row`, () => {
    const snapshot = fixture(),
      d = snapshot.devices[7]!;
    snapshot.devices.push({
      ...d,
      id: '999',
      sceneModelKey: 'copy',
      legacyKey: 'copy',
      [field]: d[field],
    });
    const index = buildPlatformIdentityIndex(snapshot);
    expect(index.resolveDeviceId(d.id)).toMatchObject({ state: 'UNLINKED', reason: 'AMBIGUOUS' });
    expect(index.resolveLegacyDevice(d.legacyKey)).toMatchObject({
      state: 'UNLINKED',
      reason: 'AMBIGUOUS',
    });
    expect(index.resolveTopologyNode('srv3')).toMatchObject({
      state: 'UNLINKED',
      reason: 'AMBIGUOUS',
    });
  });
}

for (const fault of [
  'wrong-cabinet',
  'wrong-type',
  'missing-cabinet',
  'wrong-room',
  'missing-zone',
] as const) {
  test(`legacy mapping rejects ${fault}`, () => {
    const snapshot = fixture(),
      d = snapshot.devices[7]!;
    if (fault === 'wrong-cabinet') d.cabinetId = '71';
    if (fault === 'wrong-type') d.type = 'SWITCH';
    if (fault === 'missing-cabinet') d.cabinetId = '999';
    if (fault === 'wrong-room') snapshot.cabinets[1]!.roomId = '999';
    if (fault === 'missing-zone') snapshot.cabinets[1]!.zoneId = '999';
    expect(buildPlatformIdentityIndex(snapshot).resolveTopologyNode('srv3')).toEqual({
      state: 'UNLINKED',
      mockKind: 'DEVICE',
      mockKey: 'srv3',
      reason: 'MISMATCH',
    });
  });
}

test('legacy and current classifications resolve the same inventory relationships', () => {
  const snapshot = fixture();
  snapshot.devices[7]!.meta = { ...snapshot.devices[7]!.meta, recordOrigin: 'REAL' };
  expect(buildPlatformIdentityIndex(snapshot).resolveTopologyNode('srv3')).toMatchObject({
    state: 'LINKED',
    ref: { deviceId: '807', cabinetId: '92' },
  });
});

test('duplicate parent ID, parent scene key and zone ID reject cabinet and descendant references', () => {
  for (const fault of ['cabinet-id', 'cabinet-key', 'zone-id']) {
    const snapshot = fixture();
    if (fault === 'zone-id') snapshot.zones.push({ ...snapshot.zones[0]! });
    else
      snapshot.cabinets.push({
        ...snapshot.cabinets[1]!,
        id: fault === 'cabinet-id' ? '92' : '999',
        sceneModelKey: fault === 'cabinet-key' ? 'A-02' : 'copy',
      });
    const index = buildPlatformIdentityIndex(snapshot);
    expect(index.resolveCabinetId('92')).toMatchObject({ state: 'UNLINKED', reason: 'AMBIGUOUS' });
    expect(index.resolveTopologyNode('srv3')).toMatchObject({
      state: 'UNLINKED',
      reason: 'AMBIGUOUS',
    });
  }
});

test('invalid numeric API IDs are not coerced to strings', () => {
  const snapshot = fixture();
  snapshot.devices[7]!.id = 8 as unknown as string;
  expect(buildPlatformIdentityIndex(snapshot).resolveTopologyNode('srv3')).toMatchObject({
    state: 'UNLINKED',
    reason: 'MISMATCH',
  });
});

test('a registered generated device has an identity but does not become a legacy topology node', () => {
  const snapshot = fixture();
  snapshot.devices.push({
    ...snapshot.devices[4]!,
    id: '999',
    code: 'GEN-A-01-1',
    legacyKey: 'generated-A-01-1',
    sceneModelKey: 'generated-A-01-1',
  });
  const index = buildPlatformIdentityIndex(snapshot);
  expect(index.resolveDeviceId('999')).toMatchObject({ state: 'LINKED', ref: { deviceId: '999' } });
  expect(index.resolveTopologyNode('generated-A-01-1')).toMatchObject({
    state: 'UNLINKED',
    reason: 'NOT_REGISTERED',
  });
});

test('unavailable and empty snapshots differ; index and references are immutable captured values', () => {
  expect(buildPlatformIdentityIndex().resolveCabinetId('71')).toMatchObject({
    state: 'UNLINKED',
    reason: 'UNAVAILABLE',
  });
  expect(buildPlatformIdentityIndex().resolveTopologyNode('srv3')).toMatchObject({
    state: 'UNLINKED',
    reason: 'UNAVAILABLE',
  });
  const snapshot = fixture(),
    index = buildPlatformIdentityIndex(snapshot);
  const result = index.resolveTopologyNode('srv3');
  snapshot.devices[7]!.cabinetId = '71';
  expect(index.resolveTopologyNode('srv3')).toEqual(result);
  expect(Object.isFrozen(index)).toBe(true);
  expect(Object.isFrozen(result)).toBe(true);
  if (result.state === 'LINKED') expect(Object.isFrozen(result.ref)).toBe(true);
  snapshot.devices = [];
  expect(buildPlatformIdentityIndex(snapshot).resolveTopologyNode('srv3')).toMatchObject({
    state: 'UNLINKED',
    reason: 'NOT_REGISTERED',
  });
});
