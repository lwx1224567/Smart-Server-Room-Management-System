import { getInventory } from './client';
import type { Room, Page, Cabinet, Device, SceneSnapshot } from '@/types/platform';
export const inventoryApi = {
  rooms: (recordOrigin: 'DEMO' | 'REAL' = 'REAL') =>
    getInventory<Page<Room>>('/rooms', { recordOrigin, pageSize: 100 }),
  room: (id: string) => getInventory<Room>('/rooms/' + encodeURIComponent(id)),
  scene: (id: string, recordOrigin: 'DEMO' | 'REAL' = 'REAL') =>
    getInventory<SceneSnapshot>('/rooms/' + encodeURIComponent(id) + '/scene', { recordOrigin }),
  cabinets: (id: string) =>
    getInventory<Page<Cabinet>>('/rooms/' + encodeURIComponent(id) + '/cabinets', {
      pageSize: 100,
    }),
  cabinet: (id: string) => getInventory<Cabinet>('/cabinets/' + encodeURIComponent(id)),
  devices: (id: string) =>
    getInventory<Page<Device>>('/cabinets/' + encodeURIComponent(id) + '/devices', {
      pageSize: 100,
    }),
};
