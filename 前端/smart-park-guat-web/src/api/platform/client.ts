import { consoleApi } from '@/services/console';

// All JSON calls share authentication, envelope parsing and session expiry handling.
export function mutateInventory<T>(
  path: string,
  method: 'POST' | 'PUT' | 'PATCH' | 'DELETE',
  body?: unknown,
): Promise<T> {
  return consoleApi<T>(path, method, body);
}

export function saveCabinetControl<T>(
  roomId: string,
  cabinetId: string,
  key: string,
  value: boolean,
): Promise<T> {
  return mutateInventory<T>(
    `/rooms/${encodeURIComponent(roomId)}/cabinets/${encodeURIComponent(cabinetId)}/controls`,
    'PUT',
    { key, value },
  );
}

export function getInventory<T>(path: string, params: Record<string, unknown> = {}): Promise<T> {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null) query.set(key, String(value));
  }
  return consoleApi<T>(path + (query.size ? (path.includes('?') ? '&' : '?') + query : ''));
}
