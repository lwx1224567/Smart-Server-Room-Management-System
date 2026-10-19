import type { InventoryRef } from '@/types/platform-identity';

export interface SceneInput {
  devices: {
    runtimeStatus?: string;
    id: string;
    databaseId?: string;
    databaseRef?: InventoryRef;
    name: string;
    zone: string;
    doorOpen: boolean;
    alarm: { title: string; severity: string } | null;
    position: number[];
    column: number;
    row: number;
  }[];
  zones: { id: string; position: number[]; temperature: string; humidity: string }[];
  // id/cabinetId remain Three.js keys; database fields never replace those keys.
  inventory: {
    id: string;
    databaseId?: string;
    databaseCabinetId?: string;
    databaseRef?: InventoryRef;
    label: string;
    sub: string;
    cabinetId: string;
    layer: string;
  }[];
  controls: Record<string, { lighting: boolean; fans: boolean; locked: boolean; power: boolean }>;
  alarms: Record<string, { title: string; severity: string }>;
  platform: boolean;
}
