import type { Cabinet, Device, SceneSnapshot, Zone } from '@/types/platform';
import type {
  CabinetRef,
  DeviceRef,
  IdentityResolution,
  InventoryRef,
  PlatformIdentityIndex,
  UnlinkedReason,
} from '@/types/platform-identity';

// These are legacy identities from the original seed, never database primary keys.
const topologyRules: Readonly<Record<string, { cabinetKey: string; type: Device['type'] }>> =
  Object.freeze({
    core: { cabinetKey: 'A-01', type: 'SWITCH' },
    agg: { cabinetKey: 'A-01', type: 'SWITCH' },
    sw1: { cabinetKey: 'A-01', type: 'SWITCH' },
    sw2: { cabinetKey: 'A-02', type: 'SWITCH' },
    srv1: { cabinetKey: 'A-01', type: 'SERVER' },
    srv2: { cabinetKey: 'A-01', type: 'SERVER' },
    nas: { cabinetKey: 'A-01', type: 'STORAGE' },
    srv3: { cabinetKey: 'A-02', type: 'SERVER' },
    srv4: { cabinetKey: 'A-02', type: 'SERVER' },
  });

function buckets<T>(rows: readonly T[], key: (row: T) => string) {
  const result = new Map<string, T[]>();
  for (const row of rows) {
    const value = key(row);
    const group = result.get(value) ?? [];
    group.push(row);
    result.set(value, group);
  }
  return result;
}

function unlinked<T extends InventoryRef>(
  kind: T['kind'],
  key: string,
  reason: UnlinkedReason,
): IdentityResolution<T> {
  return Object.freeze({ state: 'UNLINKED', mockKind: kind, mockKey: key, reason });
}
function linked<T extends InventoryRef>(ref: T): IdentityResolution<T> {
  return Object.freeze({ state: 'LINKED', ref: Object.freeze(ref) });
}
const validId = (value: unknown): value is string =>
  typeof value === 'string' && /^[1-9][0-9]*$/.test(value);
const validKey = (value: unknown): value is string => typeof value === 'string' && value.length > 0;

/** Build only from the complete room snapshot, never the selected cabinet's page. */
export function buildPlatformIdentityIndex(snapshot?: SceneSnapshot): PlatformIdentityIndex {
  // Capture values so later mutations of API/UI objects cannot alter an existing index.
  const roomId = snapshot?.roomId;
  const zones = (snapshot?.zones ?? []).map((z) => ({ ...z, meta: { ...z.meta } }));
  const cabinets = (snapshot?.cabinets ?? []).map((c) => ({ ...c, meta: { ...c.meta } }));
  const devices = (snapshot?.devices ?? []).map((d) => ({ ...d, meta: { ...d.meta } }));
  const zoneIds = buckets(zones, (z) => z.id);
  const cabinetIds = buckets(cabinets, (c) => c.id);
  const cabinetKeys = buckets(cabinets, (c) => c.sceneModelKey);
  const deviceIds = buckets(devices, (d) => d.id);
  const deviceKeys = buckets(devices, (d) => d.sceneModelKey);
  const legacyKeys = buckets(devices, (d) => d.legacyKey);
  const available = snapshot !== undefined;

  function cabinetIssue(c: Cabinet): UnlinkedReason | undefined {
    if (cabinetIds.get(c.id)!.length !== 1 || cabinetKeys.get(c.sceneModelKey)!.length !== 1)
      return 'AMBIGUOUS';
    const parent = zoneIds.get(c.zoneId);
    if (parent && parent.length !== 1) return 'AMBIGUOUS';
    if (
      !validId(roomId) ||
      !validId(c.id) ||
      !validId(c.zoneId) ||
      !validKey(c.sceneModelKey) ||
      c.roomId !== roomId ||
      !parent ||
      !sameOrigin(c, parent[0]!)
    )
      return 'MISMATCH';
  }
  function sameOrigin(a: Cabinet | Device, b: Cabinet | Zone) {
    return (
      (a.meta.recordOrigin === 'DEMO' || a.meta.recordOrigin === 'REAL') &&
      (b.meta.recordOrigin === 'DEMO' || b.meta.recordOrigin === 'REAL')
    );
  }
  function deviceIssue(d: Device): UnlinkedReason | undefined {
    if (
      deviceIds.get(d.id)!.length !== 1 ||
      deviceKeys.get(d.sceneModelKey)!.length !== 1 ||
      legacyKeys.get(d.legacyKey)!.length !== 1
    )
      return 'AMBIGUOUS';
    const parent = cabinetIds.get(d.cabinetId);
    if (parent && parent.length !== 1) return 'AMBIGUOUS';
    if (
      !validId(d.id) ||
      !validId(d.cabinetId) ||
      !validKey(d.sceneModelKey) ||
      !validKey(d.legacyKey) ||
      !['SWITCH', 'SERVER', 'STORAGE', 'OTHER'].includes(d.type) ||
      !parent
    )
      return 'MISMATCH';
    return cabinetIssue(parent[0]!) ?? (sameOrigin(d, parent[0]!) ? undefined : 'MISMATCH');
  }
  function resolveCabinet(
    map: Map<string, Cabinet[]>,
    key: string,
  ): IdentityResolution<CabinetRef> {
    if (!available) return unlinked('CABINET', key, 'UNAVAILABLE');
    const matches = map.get(key);
    if (!matches) return unlinked('CABINET', key, 'NOT_REGISTERED');
    if (matches.length !== 1) return unlinked('CABINET', key, 'AMBIGUOUS');
    const c = matches[0]!,
      issue = cabinetIssue(c);
    return issue
      ? unlinked('CABINET', key, issue)
      : linked({ kind: 'CABINET', cabinetId: c.id, sceneModelKey: c.sceneModelKey });
  }
  function resolveDevice(map: Map<string, Device[]>, key: string): IdentityResolution<DeviceRef> {
    if (!available) return unlinked('DEVICE', key, 'UNAVAILABLE');
    const matches = map.get(key);
    if (!matches) return unlinked('DEVICE', key, 'NOT_REGISTERED');
    if (matches.length !== 1) return unlinked('DEVICE', key, 'AMBIGUOUS');
    const d = matches[0]!,
      issue = deviceIssue(d);
    if (issue) return unlinked('DEVICE', key, issue);
    return linked({
      kind: 'DEVICE',
      deviceId: d.id,
      cabinetId: d.cabinetId,
      sceneModelKey: d.sceneModelKey,
      cabinetSceneModelKey: cabinetIds.get(d.cabinetId)![0]!.sceneModelKey,
    });
  }
  return Object.freeze({
    resolveDeviceId: (id: string) => resolveDevice(deviceIds, id),
    resolveDeviceSceneKey: (key: string) => resolveDevice(deviceKeys, key),
    resolveLegacyDevice: (key: string) => resolveDevice(legacyKeys, key),
    resolveCabinetId: (id: string) => resolveCabinet(cabinetIds, id),
    resolveCabinetSceneKey: (key: string) => resolveCabinet(cabinetKeys, key),
    resolveTopologyNode(key: string): IdentityResolution<DeviceRef> {
      if (!available) return unlinked('DEVICE', key, 'UNAVAILABLE');
      if (!Object.prototype.hasOwnProperty.call(topologyRules, key))
        return unlinked('DEVICE', key, 'NOT_REGISTERED');
      const rule = topologyRules[key]!,
        result = resolveDevice(legacyKeys, key);
      if (result.state === 'UNLINKED') return result;
      const device = deviceIds.get(result.ref.deviceId)![0]!;
      return result.ref.cabinetSceneModelKey === rule.cabinetKey && device.type === rule.type
        ? result
        : unlinked('DEVICE', key, 'MISMATCH');
    },
  });
}
