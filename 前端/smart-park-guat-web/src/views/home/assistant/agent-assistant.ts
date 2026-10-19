import { reactive } from 'vue';
import type { CabinetRef, IdentityResolution } from '@/types/platform-identity';
import { agentRequest } from './agent-client';
import { createAgentConversation, type AgentConversationState } from './agent-conversation';
import { createHomeSceneControls } from './home-scene-controls';
import type { AgentScene } from './scene-action-executor';

export function createAgentAssistant(getScene: () => (AgentScene & { patrolState?(): { progress: number; target?: string } | undefined }) | undefined, options: {
  resolveCabinet(key: string): IdentityResolution<CabinetRef>;
}) {
  const state = reactive<AgentConversationState>({ messages: [], contextId: '', busy: false,
    dialogueBusy: false, progress: 0, status: '请先加载机房', mode: '服务端 Agent',
    pending: undefined,
    agentContext: {}, agentRoom: '', confirmationUnknown: false });
  const scene = createHomeSceneControls(getScene, options.resolveCabinet);
  function select(key: string) {
    if (options.resolveCabinet(key).state === 'LINKED') state.contextId = key;
  }
  const conversation = createAgentConversation(state, scene.executor, select, agentRequest);
  return { state, scene, select, command: conversation.command,
    async enableAgent(room: string) { if (state.agentRoom !== room) state.contextId = ''; await conversation.enable(room); },
    confirmPending: conversation.confirm, dismissPending: conversation.dismiss, checkConfirmation: conversation.checkConfirmation,
    resolveContext: () => options.resolveCabinet(state.contextId),
    poll() { const patrol = getScene()?.patrolState?.(); if (state.busy && patrol) { state.progress = patrol.progress; state.status = `正在前往 ${patrol.target} · ${Math.round(patrol.progress * 100)}%`; } },
    dispose: conversation.dispose };
}
export type AgentAssistant = ReturnType<typeof createAgentAssistant>;
