import { createAgentConversation } from '../../src/views/home/assistant/agent-conversation';

/** In-memory transport only; no HTTP, model, database or business service. */
export function agentFixture(handler: (route: string, body: any, emit: any) => any = () => ({ messages: [] })) {
  (globalThis as any).window = { addEventListener() {}, removeEventListener() {} };
  const state: any = { messages: [], contextId: '' };
  const calls: { room: string; route: string; body: any; page: string }[] = [];
  const request: any = async (room: string, route: string, body: any, emit: any, page: string) => {
    calls.push({ room, route, body, page });
    return await handler(route, body, emit);
  };
  const conversation = createAgentConversation(state, { cancel() {}, execute: async () => { throw new Error('unexpected scene action'); } }, () => {}, request);
  return { state, calls, conversation };
}
