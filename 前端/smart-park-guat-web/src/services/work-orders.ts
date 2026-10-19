import { reactive } from 'vue';
import { consoleApi } from '@/services/console';

export interface FrontendOrder {
  id: string;
  orderCode?: string;
  device: string;
  alarmEvent: string;
  title: string;
  priority: string;
  status: string;
  owner: string;
  created: string;
  note: string;
  steps: string[];
  siteChecked: boolean;
  verified: boolean;
  process?: string;
  cause?: string;
  result?: string;
  submittedAt?: string;
  retestNote?: string;
  retestChecks?: {
    passed: boolean | null;
    checkedAt?: string;
    note?: string;
    evidence?: {
      metric: string;
      value: number;
      unit: string;
      observedAt: string;
      source: string;
    }[];
  }[];
  source?: string;
  ownerAccountId?: number;
  recordOrigin?: string;
  roomId?: string;
  cabinetId?: string;
  deviceId?: string;
  updatedAt?: string;
  retestEvidence?: {
    passed: boolean | null;
    checkedAt?: string;
    evidence?: {
      metric: string;
      value: number;
      unit: string;
      observedAt: string;
      source: string;
    }[];
  };
}
export const frontendOrders = reactive<FrontendOrder[]>([]);
export const orderStorage = reactive({ error: '', loading: false });
let generation = 0;
window.addEventListener('console-session-cleared', () => {
  generation++;
  frontendOrders.splice(0);
  orderStorage.error = '';
  orderStorage.loading = false;
});
function merge(order: FrontendOrder) {
  const existing = frontendOrders.find((o) => o.id === order.id);
  if (existing) Object.assign(existing, order);
  else frontendOrders.unshift(order);
  return existing ?? order;
}
export async function loadOrders() {
  const current = generation;
  orderStorage.loading = true;
  orderStorage.error = '';
  try {
    const orders = await consoleApi<FrontendOrder[]>('/work-orders');
    if (current !== generation) return;
    const ids = new Set(orders.map((o) => o.id));
    for (let i = frontendOrders.length - 1; i >= 0; i--)
      if (!ids.has(frontendOrders[i]!.id)) frontendOrders.splice(i, 1);
    orders.forEach(merge);
    frontendOrders.sort((left, right) => right.created.localeCompare(left.created));
  } catch (e) {
    if (current === generation) orderStorage.error = (e as Error).message;
  } finally {
    if (current === generation) orderStorage.loading = false;
  }
}
export async function createOrder(draft: Record<string, unknown>) {
  return merge(await consoleApi<FrontendOrder>('/work-orders', 'POST', draft));
}
export async function orderAction(
  order: FrontendOrder,
  action: string,
  body: Record<string, unknown> = {},
) {
  try {
    return merge(
      await consoleApi<FrontendOrder>(
        `/work-orders/${encodeURIComponent(order.id)}/${action === 'save' ? 'processing-record' : action}`,
        action === 'save' ? 'PATCH' : 'POST',
        body,
      ),
    );
  } catch (e) {
    await loadOrders();
    throw e;
  }
}

export async function submitFrontendOrder(
  order: FrontendOrder,
  process: string,
  result: string,
  cause: string,
) {
  if (!cause.trim()) throw new Error('请填写故障原因');
  if (!process.trim() || !result.trim()) throw new Error('请填写处理过程和处理结果');
  return orderAction(order, 'submit', {
    cause: cause.trim(),
    process: process.trim(),
    result: result.trim(),
  });
}
