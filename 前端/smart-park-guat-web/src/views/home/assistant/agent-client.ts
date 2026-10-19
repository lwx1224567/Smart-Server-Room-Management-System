import { auth, clearSession } from '@/services/console';
import type { SceneAction } from './scene-action-executor';

export interface AgentSession {
  reply?: string;
  messages: { role: string; text: string }[];
  context?: { cabinetKey?: string; orderId?: string; orderCode?: string; alarmId?: string; alarmCode?: string };
  pending?: { kind: string; summary: string; token: string };
  trace?: { tool: unknown; result: unknown }[];
  clientAction?: SceneAction;
  interrupted?: boolean;
  confirmation?: { status: 'SUCCEEDED' | 'REJECTED' | 'UNKNOWN'; message?: string; result?: unknown };
  actualOrders?: { id: string; orderCode?: string; status: string }[];
}
export interface AgentStreamEvent {
  type: string;
  data: { text?: string; message?: string };
}
export async function agentRequest(
  room: string,
  route: string,
  body?: unknown,
  onEvent?: (event: AgentStreamEvent) => void,
  pageId?: string,
): Promise<AgentSession> {
  const streaming = !!onEvent && body !== undefined;
  const response = await fetch(
    `${import.meta.env.VITE_APP_BASE_API}/api/v1/assistant/rooms/${encodeURIComponent(room)}/${route}`,
    {
      method: body === undefined ? 'GET' : 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${auth.token}`,
        ...(pageId ? { 'X-Assistant-Page': pageId } : {}),
        ...(streaming ? { Accept: 'text/event-stream' } : {}),
      },
      keepalive: route === 'session/end',
      body:
        body === undefined
          ? undefined
          : JSON.stringify(streaming ? { ...(body as object), stream: true } : body),
      signal: AbortSignal.timeout(100000),
    },
  );
  if (response.ok && response.headers.get('Content-Type')?.includes('text/event-stream')) {
    if (!response.body) throw new Error('浏览器未提供流式响应内容');
    const reader = response.body.getReader(),
      decoder = new TextDecoder();
    let buffer = '',
      completed: AgentSession | undefined;
    const consume = (block: string) => {
      const lines = block.split('\n');
      const type =
        lines
          .find((line) => line.startsWith('event:'))
          ?.slice(6)
          .trim() || 'message';
      const raw = lines
        .filter((line) => line.startsWith('data:'))
        .map((line) => line.slice(5).replace(/^ /, ''))
        .join('\n');
      if (!raw) return;
      const data = JSON.parse(raw);
      if (type === 'error') throw new Error(data.message || '回答传输中断');
      if (type === 'done') completed = data as AgentSession;
      else onEvent?.({ type, data });
    };
    try {
      while (!completed) {
        const chunk = await reader.read();
        buffer = (buffer + decoder.decode(chunk.value, { stream: !chunk.done })).replace(
          /\r\n/g,
          '\n',
        );
        let end: number;
        while ((end = buffer.indexOf('\n\n')) !== -1) {
          const block = buffer.slice(0, end);
          buffer = buffer.slice(end + 2);
          consume(block);
          if (completed) break;
        }
        if (chunk.done) {
          if (buffer.trim() && !completed) consume(buffer);
          break;
        }
      }
      if (!completed) throw new Error('回答传输中断，尚未收到完成确认');
      return completed;
    } finally {
      await reader.cancel().catch(() => {});
      reader.releaseLock();
    }
  }
  const result = await response.json();
  if (!response.ok || result.code !== 200) {
    if (response.status === 401) clearSession();
    throw new Error(result.msg || '对话服务暂不可用，请查询工单状态后再试');
  }
  return result.data;
}
