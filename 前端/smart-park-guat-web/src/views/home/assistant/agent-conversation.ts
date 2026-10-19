import { reactive } from 'vue';
import type { agentRequest, AgentSession, AgentStreamEvent } from './agent-client';
import type { createSceneActionExecutor } from './scene-action-executor';

/** UI state only. Business identities, pending operations and results come from AgentSession. */
export interface AgentConversationState {
  messages: AgentSession['messages'];
  contextId: string;
  busy: boolean;
  dialogueBusy: boolean;
  progress: number;
  status: string;
  mode: string;
  pending?: AgentSession['pending'];
  agentContext: NonNullable<AgentSession['context']>;
  agentRoom: string;
  confirmationUnknown: boolean;
}

export function createAgentConversation(state: AgentConversationState, scene: ReturnType<typeof createSceneActionExecutor>, select: (key: string) => void,
  request: typeof agentRequest) {
  let room = '', page = crypto.randomUUID(), version = 0, disposed = false;
  let recovery: { room: string; page: string; body: { pendingToken: string; kind: string; requestId: string } } | undefined;
  state.pending = undefined; state.agentContext = {}; state.agentRoom = ''; state.confirmationUnknown = false;
  function apply(session: AgentSession) {
    state.messages.splice(0, state.messages.length, ...session.messages);
    state.pending = session.pending; state.agentContext = session.context || {};
    if (session.context?.cabinetKey) select(session.context.cabinetKey);
  }
  const add = (text: string, role = 'assistant') => state.messages.push({ role, text });
  const end = () => { if (room) void request(room, 'session/end', {}, undefined, page).catch(() => {}); };
  window.addEventListener('pagehide', end);
  async function enable(next: string) {
    if (room === next) return;
    version++; scene.cancel(); end(); room = next; page = crypto.randomUUID();
    state.agentRoom = room; state.pending = undefined; state.agentContext = {}; state.dialogueBusy = false;
    recovery = undefined; state.confirmationUnknown = false; state.messages.splice(0);
    state.mode = '服务端 Agent'; state.status = room ? '正在连接 Agent…' : '请先加载机房';
    if (!room) return;
    const mine = version;
    try { const session = await request(room, 'session', undefined, undefined, page); if (mine === version && !disposed) { apply(session); state.status = '等待你的指令'; } }
    catch (e) { if (mine === version) { state.status = 'Agent 连接失败，可发送消息重试'; add(`Agent 连接失败：${(e as Error).message}`); } }
  }
  async function command(text: string) {
    if (!text.trim()) return;
    if (!room) { add('机房尚未就绪，无法访问服务端 Agent。'); return; }
    if (state.dialogueBusy) return '正在处理上一条请求，请稍候。';
    const mine = version, targetRoom = room, targetPage = page;
    state.dialogueBusy = true; state.pending = undefined; add(text, 'user');
    let streaming: AgentSession['messages'][number] | undefined;
    const onEvent = (event: AgentStreamEvent) => {
      if (mine !== version || disposed) return;
      if (event.type === 'status') state.status = event.data.message!;
      if (event.type === 'message_start') { if (streaming) state.messages.splice(state.messages.indexOf(streaming), 1); streaming = reactive({ role: 'assistant', text: '' }); state.messages.push(streaming); }
      if (event.type === 'delta') { if (!streaming) { streaming = reactive({ role: 'assistant', text: '' }); state.messages.push(streaming); } streaming.text += event.data.text || ''; }
    };
    try {
      state.status = '正在请求服务端 Agent…';
      let session = await request(targetRoom, 'chat', { text, sceneCabinet: state.contextId, requestId: crypto.randomUUID() }, onEvent, targetPage);
      if (mine !== version || disposed) return;
      apply(session); streaming = undefined;
      for (let i = 0; session.clientAction && i < 4; i++) {
        state.busy = true; const receipt = await scene.execute(session.clientAction); state.busy = false;
        if (mine !== version || disposed) return;
        session = await request(targetRoom, 'scene-result', receipt, onEvent, targetPage);
        if (mine !== version || disposed) return;
        apply(session); streaming = undefined;
      }
      state.status = session.interrupted ? '模型回答中断，请重试查询' : session.pending ? '请核对确认卡片' : '本轮完成';
      return session.reply;
    } catch (e) { if (mine === version && !disposed) { state.status = 'Agent 请求失败'; add(`请求未完成：${(e as Error).message}。不会切回本地规则或重放业务写入。`); } }
    finally { if (mine === version) { state.dialogueBusy = false; state.busy = false; } }
  }
  async function checkConfirmation() {
    if (!recovery || state.dialogueBusy) return;
    const saved = recovery, mine = version; state.dialogueBusy = true;
    try {
      const session = await request(saved.room, 'confirmation/status', saved.body, undefined, saved.page);
      if (mine !== version || disposed) return;
      apply(session);
      const result = session.confirmation;
      if (result?.status === 'SUCCEEDED' || result?.status === 'REJECTED') { recovery = undefined; state.confirmationUnknown = false; }
      state.status = result?.status === 'SUCCEEDED' ? '已核实执行结果' : result?.status === 'REJECTED' ? '操作被拒绝，请重新核对' : '结果仍不确定，请查看实际工单状态';
      if (session.actualOrders) add(`当前实际工单状态：${session.actualOrders.map(o => `${o.orderCode || o.id} · ${o.status}`).join('；') || '未查询到关联工单'}。${result?.status === 'UNKNOWN' ? '这不代表本次请求已成功或失败。' : '执行结果以服务端回执为准。'}`);
    } catch (e) { if (mine === version) { state.status = '状态尚未核实，请稍后查询；禁止自动重提'; add((e as Error).message); } }
    finally { if (mine === version) state.dialogueBusy = false; }
  }
  async function confirm() {
    if (!state.pending || state.dialogueBusy || recovery) return;
    const pending = state.pending, mine = version;
    recovery = { room, page, body: { pendingToken: pending.token, kind: pending.kind, requestId: crypto.randomUUID() } };
    const saved = recovery; state.pending = undefined; state.dialogueBusy = true;
    try {
      const session = await request(saved.room, 'confirm', saved.body, undefined, saved.page);
      if (mine !== version || disposed) return;
      apply(session);
      const result = session.confirmation;
      if (result?.status === 'SUCCEEDED' || result?.status === 'REJECTED') { recovery = undefined; state.confirmationUnknown = false; state.status = result.status === 'SUCCEEDED' ? '已执行并收到结果' : '操作被拒绝'; if (result.message) add(result.message); }
      else state.confirmationUnknown = true;
    } catch (e) { if (mine === version) { state.confirmationUnknown = true; state.status = '提交结果未确认，正在核对实际状态'; add(`提交回执未完成：${(e as Error).message}。不会重复提交。`); } }
    finally { if (mine === version) state.dialogueBusy = false; }
    if (mine === version && recovery) await checkConfirmation();
  }
  async function dismiss() {
    if (!state.pending || state.dialogueBusy) return;
    const pending = state.pending; state.pending = undefined;
    try { await request(room, 'pending/dismiss', { pendingToken: pending.token }, undefined, page); }
    catch { add('确认卡片已关闭；服务端取消状态未核实，未发送写入请求。'); }
  }
  return { enable, command, confirm, dismiss, checkConfirmation, dispose() { disposed = true; version++; scene.cancel(); end(); window.removeEventListener('pagehide', end); } };
}
