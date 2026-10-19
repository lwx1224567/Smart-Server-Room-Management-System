import type { SceneSnapshot } from '@/types/platform';
import { topologyNodes } from '../demo/topologyData';
import { buildPlatformIdentityIndex } from '../../../utils/platform-identity';
import type { InventoryRef } from '@/types/platform-identity';
const zonePositions: Record<string, number[]> = {
  A: [-5.7, 3.7, -4.3],
  B: [0, 4.1, -4.3],
  C: [5.7, 3.7, -4.3],
  D: [-4.9, 3.9, 4.8],
  E: [4.9, 3.9, 4.8],
};
export function adaptPlatformScene(snapshot: SceneSnapshot) {
  const identities = buildPlatformIdentityIndex(snapshot);
  const reference = (
    result:
      | ReturnType<typeof identities.resolveDeviceId>
      | ReturnType<typeof identities.resolveCabinetId>,
  ): InventoryRef | undefined => (result.state === 'LINKED' ? result.ref : undefined);
  const controls = Object.fromEntries(
    snapshot.cabinets.map((c) => [
      c.sceneModelKey,
      { lighting: true, fans: true, locked: false, power: true },
    ]),
  );
  const alarms = {};
  return {
    devices: snapshot.cabinets.map((c) => ({
      id: c.sceneModelKey,
      databaseId: c.id,
      databaseRef: reference(identities.resolveCabinetId(c.id)),
      name: c.name,
      zone: snapshot.zones.find((z) => z.id === c.zoneId)?.code ?? '',
      column: c.gridColumn!,
      row: c.gridRow!,
      position: c.position!,
      capacity: c.capacityU,
      serverCount: c.registeredDeviceCount,
      // Existing highlight is a frozen visual demo layer, never a measured fault.
      alarm: null,
      doorOpen: false,
      runtimeStatus: 'UNKNOWN',
    })),
    zones: snapshot.zones.map((z) => ({
      id: z.code,
      position: [...(zonePositions[z.code] ?? [0, 3, 0])],
      temperature: '--',
      humidity: '--',
    })),
    inventory: snapshot.devices.map((d) => ({
      id: d.sceneModelKey,
      databaseId: d.id,
      databaseCabinetId: d.cabinetId,
      databaseRef: reference(identities.resolveDeviceId(d.id)),
      label: d.name,
      sub: d.code,
      cabinetId: snapshot.cabinets.find((c) => c.id === d.cabinetId)!.sceneModelKey,
      layer:
        d.type === 'SWITCH'
          ? topologyNodes.find((n) => n.id === d.legacyKey)?.layer || 'access'
          : 'terminal',
    })),
    controls,
    alarms,
    platform: true,
  };
}
// Cabinet detail responses, rather than the overview snapshot, drive visible units.
export function adaptCabinetInventory(
  devices: import('@/types/platform').Device[],
  cabinetKey: string,
) {
  return devices.map((d) => ({
    id: d.sceneModelKey,
    databaseId: d.id,
    databaseCabinetId: d.cabinetId,
    label: d.name,
    sub: d.code,
    cabinetId: cabinetKey,
    layer: d.type === 'SWITCH' ? 'access' : 'terminal',
  }));
}
