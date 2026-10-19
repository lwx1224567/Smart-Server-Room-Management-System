export interface DataMeta {
  recordOrigin: 'DEMO' | 'REAL';
  source: string;
  sourceUpdatedAt: string | null;
  quality: string;
  generatedAt: string;
}
export interface Room {
  id: string;
  parkId: string;
  parkName: string;
  code: string;
  name: string;
  location: string;
  managerDisplayName: string | null;
  sceneModelKey: string;
  zoneCount: number;
  cabinetCount: number;
  registeredDeviceCount: number;
  meta: DataMeta;
}
export interface Zone {
  id: string;
  code: string;
  name: string;
  displayOrder: number;
  meta: DataMeta;
}
export interface Reading {
  value: number | null;
  unit: string;
  observedAt: string | null;
  quality: string;
}
export interface Cabinet {
  id: string;
  roomId: string;
  zoneId: string;
  zoneCode?: string;
  code: string;
  name: string;
  sceneModelKey: string;
  capacityU: number;
  registeredDeviceCount: number;
  registrationStatus: string;
  runtimeStatus: 'UNKNOWN';
  position?: number[];
  gridRow?: number;
  gridColumn?: number;
  serialNumber?: string;
  roomName?: string;
  location?: string;
  usedU?: number | null;
  telemetry?: {
    runtimeStatus: 'UNKNOWN';
    powerState: 'UNKNOWN';
    networkState: 'UNKNOWN';
    temperature: Reading;
    power: Reading;
  };
  meta: DataMeta;
}
export interface Device {
  id: string;
  cabinetId: string;
  code: string;
  name: string;
  type: 'SWITCH' | 'SERVER' | 'STORAGE' | 'OTHER';
  sceneModelKey: string;
  legacyKey: string;
  registrationStatus: string;
  runtimeStatus: 'UNKNOWN';
  uStart: number | null;
  uHeight: number | null;
  meta: DataMeta;
}
export interface Page<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
  meta: DataMeta;
}
export interface SceneSnapshot {
  roomId: string;
  zones: Zone[];
  cabinets: Cabinet[];
  devices: Device[];
  meta: DataMeta;
}
