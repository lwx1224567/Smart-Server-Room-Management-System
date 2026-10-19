import { defineStore } from 'pinia';
import { ref } from 'vue';
import { getInventory } from '@/api/platform/client';
import { alarmLevelRank } from '@/utils/alarm-levels';

export interface MonitorReading {
  entityType: string;
  entityId: string;
  metric: string;
  value: number;
  unit: string;
  observedAt: string;
  quality: string;
  source: string;
  recordOrigin: string;
}
export interface MonitorRecord {
  id: string;
  alarmCode?: string;
  logCode?: string;
  requestCode?: string;
  roomId: string;
  name?: string;
  title?: string;
  sceneModelKey?: string;
  entityId?: string;
  entityType?: string;
  cabinetId?: string;
  deviceId?: string;
  severity?: string;
  status?: string;
  metric?: string;
  value?: string | number;
  threshold?: string;
  advice?: string;
  observedAt?: string;
  createdAt?: string;
  recordOrigin?: string;
  type?: string;
  source?: string;
  description?: string;
  location?: string;
  from?: string;
  to?: string;
  layer?: string;
  parent?: string;
  bandwidth?: number;
  latency?: number;
  loss?: number;
  ports?: { total: number; used: number; errors: number };
  action?: string;
  user?: string;
}
export interface CabinetControlRecord {
  id: string;
  cabinetId: string;
  lighting: boolean;
  fans: boolean;
  locked: boolean;
  power: boolean;
}
// Inventory identity and provenance carried by whole-room topology records.
export interface LinkQualityPoint {
  observedAt: string;
  bandwidth: number;
  latency: number;
  loss: number;
}
export interface MonitorRecord {
  code?: string;
  cabinetCode?: string;
  zoneCode?: string;
  developmentSimulated?: boolean;
  qualityHistory?: LinkQualityPoint[];
}
export interface MonitoringSnapshot {
  roomId: string;
  generatedAt: string;
  readings: MonitorReading[];
  alarms: MonitorRecord[];
  facilities: MonitorRecord[];
  events: MonitorRecord[];
  nodes: MonitorRecord[];
  links: MonitorRecord[];
  logs: MonitorRecord[];
  controls?: CabinetControlRecord[];
}
export interface EnergySnapshot {
  simulated?: boolean;
  labels: string[];
  totals: (number | null)[];
  total: number | null;
  shares: {
    name: string;
    color: string;
    values: (number | null)[];
    consumption: number | null;
    value: number | null;
  }[];
  ranks: { name: string; value: number }[];
  available: boolean;
  partial: boolean;
}
export const useMonitoringStore = defineStore('monitoring', () => {
  const snapshot = ref<MonitoringSnapshot>(),
    energy = ref<EnergySnapshot>(),
    week = ref<EnergySnapshot>();
  const previousWeekTotal = ref<number | null>(null);
  const error = ref(''),
    energyError = ref(''),
    loading = ref(false);
  let generation = 0,
    energyGeneration = 0;
  async function load(roomId: string) {
    const ticket = ++generation;
    snapshot.value = undefined;
    energy.value = undefined;
    week.value = undefined;
    previousWeekTotal.value = null;
    energyGeneration++;
    error.value = '';
    loading.value = true;
    try {
      const result = await getInventory<MonitoringSnapshot>(
        `/rooms/${encodeURIComponent(roomId)}/monitoring`,
      );
      if (ticket === generation) snapshot.value = result;
    } catch (e) {
      if (ticket === generation) error.value = (e as Error).message;
    } finally {
      if (ticket === generation) loading.value = false;
    }
  }
  async function loadEnergy(roomId: string, period: string) {
    const ticket = ++energyGeneration;
    energyError.value = '';
    energy.value = undefined;
    try {
      const result = await getInventory<EnergySnapshot>(
        `/rooms/${encodeURIComponent(roomId)}/energy`,
        { period },
      );
      if (ticket === energyGeneration) energy.value = result;
    } catch (e) {
      if (ticket === energyGeneration) energyError.value = (e as Error).message;
    }
  }
  async function refresh() {
    const roomId = snapshot.value?.roomId,
      ticket = generation;
    if (!roomId) return;
    const today = new Intl.DateTimeFormat('sv-SE', {
      timeZone: 'Asia/Shanghai',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).format(new Date());
    if (week.value && week.value.labels[week.value.labels.length - 1] !== today)
      await loadWeek(roomId);
    try {
      const result = await getInventory<MonitoringSnapshot>(
        `/rooms/${encodeURIComponent(roomId)}/monitoring`,
      );
      if (ticket === generation && snapshot.value?.roomId === roomId) {
        snapshot.value = result;
        error.value = '';
      }
    } catch (e) {
      if (ticket === generation) error.value = (e as Error).message;
    }
  }
  async function loadWeek(roomId: string) {
    const ticket = generation;
    try {
      const [result, month] = await Promise.all([
        getInventory<EnergySnapshot>(`/rooms/${encodeURIComponent(roomId)}/energy`, {
          period: '7d',
        }),
        getInventory<EnergySnapshot>(`/rooms/${encodeURIComponent(roomId)}/energy`, {
          period: '30d',
        }),
      ]);
      if (ticket === generation) {
        week.value = result;
        const previous = month.totals.slice(-14, -7);
        previousWeekTotal.value =
          previous.length === 7 && previous.every((v) => v !== null)
            ? previous.reduce<number>((sum, v) => sum + v!, 0)
            : null;
      }
    } catch (e) {
      if (ticket === generation) energyError.value = (e as Error).message;
    }
  }
  function reading(entityType: string, entityId: string, metric: string) {
    return snapshot.value?.readings.find(
      (r) =>
        r.entityType === entityType &&
        r.entityId === entityId &&
        r.metric === metric &&
        r.recordOrigin === 'REAL' &&
        r.quality === 'GOOD',
    );
  }
  function cabinetAlarms(id: string, deviceIds: string[] = []) {
    return (snapshot.value?.alarms ?? []).filter(
      (a) =>
        (a.cabinetId === id || (!!a.deviceId && deviceIds.includes(a.deviceId))) &&
        a.status !== '已恢复',
    );
  }
  function cabinetAlarm(id: string, deviceIds: string[] = []) {
    return cabinetAlarms(id, deviceIds).sort(
      (a, b) =>
        alarmLevelRank(a.severity) - alarmLevelRank(b.severity) ||
        (b.createdAt ?? '').localeCompare(a.createdAt ?? ''),
    )[0];
  }
  function cabinetStatus(id: string, deviceIds: string[] = []) {
    const online = reading('CABINET', id, 'online'),
      offline =
        online?.value === 0 ||
        deviceIds.some((deviceId) => reading('DEVICE', deviceId, 'online')?.value === 0);
    return (
      [offline ? '离线' : '', cabinetAlarms(id, deviceIds).length ? '告警' : '']
        .filter(Boolean)
        .join(' · ') || (online?.value === 1 ? '在线' : '未采集')
    );
  }
  function clear() {
    generation++;
    energyGeneration++;
    snapshot.value = undefined;
    energy.value = undefined;
    week.value = undefined;
    previousWeekTotal.value = null;
    error.value = '';
    energyError.value = '';
    loading.value = false;
  }
  window.addEventListener('console-session-cleared', clear);
  return {
    snapshot,
    energy,
    week,
    previousWeekTotal,
    error,
    energyError,
    loading,
    load,
    refresh,
    loadEnergy,
    loadWeek,
    reading,
    cabinetAlarms,
    cabinetAlarm,
    cabinetStatus,
    clear,
  };
});
