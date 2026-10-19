import { getInventory, mutateInventory } from './client';
export interface IntegrationCapabilities {
  agentEnabled?: boolean;
  modelConfigured: boolean;
  videoGatewayConfigured: boolean;
  controlGatewayConfigured: boolean;
}
export interface VideoSource {
  url: string;
  protocol: 'MP4' | 'HLS';
  live: boolean;
  expiresAt?: string;
}
export interface DeviceCommand {
  id: string;
  status: 'PENDING' | 'SUCCEEDED' | 'FAILED';
  actualValue?: boolean;
  message?: string;
}
export type InventoryKind = 'parks' | 'rooms' | 'zones' | 'cabinets' | 'devices';
const roomPath = (room: string) => `/rooms/${encodeURIComponent(room)}`;
export const integrationApi = {
  capabilities: () => getInventory<IntegrationCapabilities>('/integrations'),
  inventory: async (kind: InventoryKind) => {
    const result = await getInventory<{ items: Record<string, unknown>[]; total: number }>(
      `/admin/${kind}`,
      { page: 1, pageSize: 100 },
    );
    const items = [...result.items];
    for (let page = 2; items.length < result.total; page++) {
      const next = await getInventory<{ items: Record<string, unknown>[] }>(`/admin/${kind}`, {
        page,
        pageSize: 100,
      });
      if (!next.items.length) break;
      items.push(...next.items);
    }
    return items;
  },
  rules: (room: string) => getInventory<Record<string, unknown>[]>(`${roomPath(room)}/alarm-rules`),
  video: (room: string, id: string) =>
    getInventory<VideoSource>(`${roomPath(room)}/cameras/${encodeURIComponent(id)}/video`),
  command: (room: string, id: string, key: string, value: boolean) =>
    mutateInventory<DeviceCommand>(
      `${roomPath(room)}/cabinets/${encodeURIComponent(id)}/commands`,
      'POST',
      { key, value },
    ),
};
