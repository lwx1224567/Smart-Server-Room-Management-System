import { test, expect } from '@playwright/test';
import { agentFixture } from '../../tests/platform/agent-test-fixture';

test('new page never reuses a pending token; text confirmation is a model request', async () => {
  const f = agentFixture(route => route === 'chat' ? { messages: [], pending: { kind: 'create', token: 'one', summary: '创建工单' } } : { messages: [] });
  await f.conversation.enable('1'); await f.conversation.command('创建工单');
  await f.conversation.command('确认');
  expect(f.calls.filter(c => c.route === 'chat')).toHaveLength(2);
  expect(f.calls.some(c => c.route === 'confirm')).toBe(false);
  const page = f.calls[0].page; f.conversation.dispose();
  const fresh = agentFixture(); await fresh.conversation.enable('1');
  expect(fresh.state.pending).toBeUndefined(); expect(fresh.calls[0].page).not.toBe(page); fresh.conversation.dispose();
});

test('only explicit confirmation sends pendingToken, kind and a new requestId', async () => {
  const f = agentFixture(route => route === 'chat' ? { messages: [], pending: { kind: 'create', token: 'one', summary: '创建工单' } } : { messages: [], confirmation: { status: 'SUCCEEDED' } });
  await f.conversation.enable('1'); await f.conversation.command('创建工单'); await f.conversation.confirm();
  const request = f.calls.find(c => c.route === 'confirm')!;
  expect(request.body).toMatchObject({ pendingToken: 'one', kind: 'create' }); expect(request.body.requestId).toBeTruthy();
  expect(f.calls.find(c => c.route === 'chat')!.body).not.toHaveProperty('pendingToken'); f.conversation.dispose();
});
