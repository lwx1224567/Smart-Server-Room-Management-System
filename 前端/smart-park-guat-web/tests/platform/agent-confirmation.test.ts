import { test, expect } from '@playwright/test';
import { createAgentConversation } from '../../src/views/home/assistant/agent-conversation';

function fixture(options: { lost?: boolean; unknown?: boolean; failed?: boolean } = {}) {
  (globalThis as any).window = { addEventListener() {}, removeEventListener() {} };
  const state: any = { messages: [], contextId: '' }, calls: { route: string; body: any }[] = [];
  const pending = { kind: 'create', token: 'pending-one', summary: '创建 A-01 告警工单' };
  const sceneCalls: any[] = [];
  const request: any = async (_room: string, route: string, body: any, onEvent: any) => {
    calls.push({ route, body });
    if (route === 'session') return { messages: [] };
    if (route === 'chat') {
      if (options.failed) throw new Error('model offline');
      onEvent?.({ type: 'message_start', data: {} }); onEvent?.({ type: 'delta', data: { text: '正在准备' } });
      return { messages: [{ role: 'assistant', text: '请核对卡片' }], pending };
    }
    if (route === 'confirm') { if (options.lost) throw new Error('response lost'); return { messages: [], confirmation: { status: 'SUCCEEDED' } }; }
    if (route === 'confirmation/status') return { messages: [], confirmation: { status: options.unknown ? 'UNKNOWN' : 'SUCCEEDED' }, actualOrders: [{ id: 'WO-1', status: '待受理' }] };
    return { messages: [] };
  };
  const conversation = createAgentConversation(state, { cancel() {}, execute: async (action: any) => { sceneCalls.push(action); return {} as any; } }, () => {}, request);
  return { state, calls, conversation, request, sceneCalls };
}
test('text and voice-transcribed confirmation never submit writes', async () => {
  const f = fixture(); await f.conversation.enable('1');
  for (const text of ['创建工单', '确认？', '确认', '好的', '可以']) await f.conversation.command(text);
  expect(f.calls.filter(c => c.route === 'chat')).toHaveLength(5);
  expect(f.calls.some(c => c.route === 'confirm')).toBe(false);
  expect(f.calls.filter(c => c.route === 'chat').every(c => !('pendingToken' in c.body))).toBe(true);
  expect(f.state.pending.token).toBe('pending-one'); expect(f.state.messages.at(-1).text).toBe('请核对卡片');f.conversation.dispose();
});
test('explicit card click binds token kind and requestId and double click never replays', async () => {
  const f=fixture();await f.conversation.enable('1');await f.conversation.command('创建工单');
  await Promise.all([f.conversation.confirm(), f.conversation.confirm()]);
  const writes=f.calls.filter(c=>c.route==='confirm');expect(writes).toHaveLength(1);expect(writes[0].body).toMatchObject({pendingToken:'pending-one',kind:'create'});expect(writes[0].body.requestId).toBeTruthy();f.conversation.dispose();
});
test('lost write response queries status using same identity without resubmission', async () => {
  const f=fixture({lost:true});await f.conversation.enable('1');await f.conversation.command('创建');await f.conversation.confirm();
  const write=f.calls.find(c=>c.route==='confirm')!, read=f.calls.find(c=>c.route==='confirmation/status')!;
  expect(read.body).toEqual(write.body);expect(f.calls.filter(c=>c.route==='confirm')).toHaveLength(1);expect(f.state.confirmationUnknown).toBe(false);f.conversation.dispose();
});
test('unknown result remains blocked and status retry is read only', async () => {
  const f=fixture({lost:true,unknown:true});await f.conversation.enable('1');await f.conversation.command('创建');await f.conversation.confirm();await f.conversation.confirm();await f.conversation.checkConfirmation();
  expect(f.state.confirmationUnknown).toBe(true);expect(f.calls.filter(c=>c.route==='confirm')).toHaveLength(1);expect(f.calls.filter(c=>c.route==='confirmation/status')).toHaveLength(2);f.conversation.dispose();
});
test('dismiss and switching room never confirm pending operation', async () => {
  const f=fixture();await f.conversation.enable('1');await f.conversation.command('创建');await f.conversation.dismiss();await f.conversation.confirm();
  await f.conversation.command('再准备');await f.conversation.enable('2');await f.conversation.confirm();
  expect(f.calls.some(c=>c.route==='pending/dismiss')).toBe(true);expect(f.calls.some(c=>c.route==='confirm')).toBe(false);expect(f.state.pending).toBeUndefined();f.conversation.dispose();
});
test('model failure never falls back to local scene or business execution', async () => {
  const f=fixture({failed:true});await f.conversation.enable('1');await f.conversation.command('打开 A-01 柜门');
  expect(f.sceneCalls).toHaveLength(0);expect(f.calls.map(c=>c.route)).toEqual(['session','chat']);expect(f.state.messages.at(-1).text).toContain('不会切回本地规则');f.conversation.dispose();
});
test('Agent action uses independent executor and submits its structured receipt', async () => {
  (globalThis as any).window={addEventListener(){},removeEventListener(){}};
  const state:any={messages:[],contextId:''},calls:any[]=[];
  const action={token:'scene-one',type:'visit',cabinetSceneModelKey:'A-01'};
  const receipt={...action,status:'SUCCEEDED',arrived:true,result:'已到达'};
  const request:any=async(_:string,route:string,body:any)=>{calls.push({route,body});return route==='chat'?{messages:[],clientAction:action}:{messages:[],reply:'完成'};};
  let executions=0;
  const conversation=createAgentConversation(state,{cancel(){},execute:async value=>{expect(value).toEqual(action);executions++;return receipt as any;}},()=>{},request);
  await conversation.enable('1');await conversation.command('巡检机柜');expect(executions).toBe(1);expect(calls.find(c=>c.route==='scene-result').body).toEqual(receipt);conversation.dispose();
});
