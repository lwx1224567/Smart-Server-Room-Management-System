/** Database references are distinct from the keys used inside Three.js and demos. */
export interface DeviceRef {
  readonly kind: 'DEVICE';
  readonly deviceId: string;
  readonly cabinetId: string;
  readonly sceneModelKey: string;
  readonly cabinetSceneModelKey: string;
}

export interface CabinetRef {
  readonly kind: 'CABINET';
  readonly cabinetId: string;
  readonly sceneModelKey: string;
}

export type InventoryRef = DeviceRef | CabinetRef;
export type UnlinkedReason = 'NOT_REGISTERED' | 'AMBIGUOUS' | 'MISMATCH' | 'UNAVAILABLE';
export type IdentityResolution<T extends InventoryRef = InventoryRef> =
  | { readonly state: 'LINKED'; readonly ref: T }
  | {
      readonly state: 'UNLINKED';
      readonly mockKind: T['kind'];
      readonly mockKey: string;
      readonly reason: UnlinkedReason;
    };

/** Resolver-only interface: callers cannot mutate the index or its references. */
export interface PlatformIdentityIndex {
  resolveDeviceId(id: string): IdentityResolution<DeviceRef>;
  resolveDeviceSceneKey(key: string): IdentityResolution<DeviceRef>;
  resolveLegacyDevice(key: string): IdentityResolution<DeviceRef>;
  resolveTopologyNode(key: string): IdentityResolution<DeviceRef>;
  resolveCabinetId(id: string): IdentityResolution<CabinetRef>;
  resolveCabinetSceneKey(key: string): IdentityResolution<CabinetRef>;
}
