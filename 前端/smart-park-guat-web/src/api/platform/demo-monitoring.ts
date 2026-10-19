import { getInventory, mutateInventory } from './client';
import { integrationApi } from './integrations';
import { inventoryApi } from './inventory';
import { consoleApi } from '@/services/console';
import type { MonitoringSnapshot, MonitorRecord } from '@/stores/monitoring';

export const DEMO_RULE = 'demo-a01-temperature-v1';
export const DEMO_SOURCE = 'ADMIN_DEMO_TEMPERATURE';
export interface TemperatureRequest {
  cabinetId: string;
  value: number;
  requestId: string;
}
export interface TemperatureReceipt extends TemperatureRequest {
  requestCode?: string;
  batchCode?: string;
  roomId: string;
  readingId: string;
  observedAt: string;
  unit: string;
  recordOrigin: string;
  source: string;
  quality: string;
  duplicate: boolean;
  alarmIds: string[];
}
export interface DemoOrder {
  id: string;
  orderCode?: string;
  roomId?: string;
  cabinetId?: string;
  alarmEvent?: string;
  recordOrigin?: string;
  status: string;
  owner?: string;
}
export interface DemoState {
  roomId: string;
  roomName: string;
  cabinetId: string;
  cabinetName: string;
  rule: Record<string, unknown>;
  snapshot: MonitoringSnapshot;
  orders: DemoOrder[];
}

export const demoMonitoringApi = {
  submit: (room: string, body: TemperatureRequest) =>
    mutateInventory<TemperatureReceipt>(
      `/rooms/${encodeURIComponent(room)}/demo-monitoring/temperature`,
      'POST',
      body,
    ),
  async read(): Promise<DemoState> {
    // Discover the enabled scope from stored rules and inventory, never from hard-coded database IDs.
    const rooms = await integrationApi.inventory('rooms');
    const candidates = (
      await Promise.all(
        rooms.map(async (room) => {
          const roomId = String(room.id);
          const rules = await integrationApi.rules(roomId);
          return rules
            .filter((rule) => rule.id === DEMO_RULE && rule.recordOrigin === 'REAL')
            .map((rule) => ({ roomId, rule }));
        }),
      )
    ).flat();
    if (candidates.length !== 1)
      throw new Error('未找到唯一的 A-01 温度规则，请检查开发环境配置。');
    const { roomId, rule } = candidates[0]!;
    const [room, cabinets, snapshot, orders] = await Promise.all([
      inventoryApi.room(roomId),
      inventoryApi.cabinets(roomId),
      getInventory<MonitoringSnapshot>(`/rooms/${encodeURIComponent(roomId)}/monitoring`),
      consoleApi<DemoOrder[]>('/work-orders'),
    ]);
    const targets = cabinets.items.filter((c) => c.code === 'A-01' || c.sceneModelKey === 'A-01');
    if (
      targets.length !== 1 ||
      String(rule.entityId) !== targets[0]!.id ||
      rule.entityType !== 'CABINET' ||
      rule.metric !== 'temperature' ||
      rule.unit !== '℃' ||
      rule.sampleSource !== DEMO_SOURCE
    )
      throw new Error('演示规则与 A-01 台账不一致，已停止模拟上报。');
    return {
      roomId,
      roomName: room.name,
      cabinetId: targets[0]!.id,
      cabinetName: targets[0]!.code,
      rule,
      snapshot,
      orders,
    };
  },
};

// The snapshot exposes samples without IDs. IDs come from the transactionally saved ingest audit response.
// Match both records so an audit alone cannot masquerade as a current monitoring sample.
export function demoReceipts(state: DemoState): TemperatureReceipt[] {
  const readings = state.snapshot.readings.filter(
    (r) =>
      r.entityType === 'CABINET' &&
      r.entityId === state.cabinetId &&
      r.metric === 'temperature' &&
      r.unit === '℃' &&
      r.recordOrigin === 'REAL' &&
      r.source === DEMO_SOURCE &&
      r.quality === 'GOOD',
  );
  return state.snapshot.logs
    .flatMap((log) => {
      const response = (log as MonitorRecord & { response?: TemperatureReceipt }).response;
      if (
        !response ||
        response.roomId !== state.roomId ||
        response.cabinetId !== state.cabinetId ||
        response.recordOrigin !== 'REAL' ||
        response.source !== DEMO_SOURCE ||
        response.quality !== 'GOOD' ||
        response.unit !== '℃' ||
        !response.readingId
      )
        return [];
      return readings.some(
        (r) =>
          r.value === response.value &&
          Date.parse(r.observedAt) === Date.parse(response.observedAt),
      )
        ? [response]
        : [];
    })
    .sort(
      (a, b) =>
        Date.parse(b.observedAt) - Date.parse(a.observedAt) ||
        String(b.readingId).localeCompare(String(a.readingId), undefined, { numeric: true }),
    );
}
