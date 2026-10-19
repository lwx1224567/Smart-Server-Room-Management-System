<template>
  <section class="inspection-assistant" aria-label="小维巡检对话">
    <div class="intro">
      <span>● 在线</span
      ><RouterLink
        class="all-orders"
        :to="{ path: '/home', query: { business: 'alarm', orders: 'all' } }"
        >工单记录 ↗</RouterLink
      >
    </div>
    <div class="progress">
      <span>{{ assistant.state.status }}</span
      ><small class="stage">{{ assistant.state.mode }}</small
      ><progress v-if="assistant.state.busy" :value="assistant.state.progress" max="1" />
    </div>
    <div ref="log" class="messages" role="log" aria-live="polite">
      <article
        v-for="(message, index) in assistant.state.messages"
        :key="index"
        :class="message.role"
      >
        <small>{{ message.role === 'user' ? '你' : '小维' }}</small>
        <p v-if="message.role === 'user'">{{ message.text }}</p>
        <AssistantMarkdown v-else :text="message.text" />
      </article>
      <article
        v-if="assistant.state.dialogueBusy && assistant.state.messages.at(-1)?.role === 'user'"
        class="assistant"
      >
        <small>小维</small>
        <p>{{ assistant.state.status }}</p>
      </article>
    </div>
    <p v-if="assistant.state.agentContext?.alarmId" class="alarm-context">
      当前告警： {{ assistant.state.agentContext.alarmCode || '已选择告警' }}
    </p>
    <div v-if="assistant.state.contextId" class="context">
      <b>{{ assistant.state.contextId }} · 台账机柜</b
      ><button :disabled="contextIdentity.state !== 'LINKED'" @click="inspectContext">
        机柜台账详情 ↗
      </button>
    </div>
    <div v-if="assistant.state.agentContext?.orderId" class="order">
      当前工单：{{ assistant.state.agentContext.orderCode || '已选择工单' }}，状态及结果见服务端回复。
      <RouterLink :to="{ path: '/home', query: { business: 'alarm', order: assistant.state.agentContext.orderId } }">查看工单记录 ↗</RouterLink>
    </div>
    <section v-if="assistant.state.pending" class="order" aria-label="业务操作确认卡片">
      <p>{{ assistant.state.pending.summary }}</p>
      <p>仅点击下方按钮才会执行业务写入。文字或语音确认不会执行。</p>
      <button type="button" :disabled="assistant.state.dialogueBusy || assistant.state.confirmationUnknown" @click="assistant.confirmPending()">{{ confirmationLabel }}</button>
      <button type="button" :disabled="assistant.state.dialogueBusy" @click="assistant.dismissPending()">取消并关闭</button>
    </section>
    <section v-if="assistant.state.confirmationUnknown" class="order" role="alert">
      提交结果尚未核实，请查询实际状态，不要重复提交。
      <button type="button" :disabled="assistant.state.dialogueBusy" @click="assistant.checkConfirmation()">查询执行结果</button>
    </section>
    <details class="more-actions">
      <summary>语音设置</summary>
      <label><input v-model="readout" type="checkbox" />语音播报</label>
      <p>可直接输入或说出需求；有多个候选时回复编号或名称，写入必须核对确认卡片并点击按钮；文字或语音确认不会执行。</p>
    </details>
    <p v-if="assistant.state.dialogueBusy" class="voice-status" role="status">
      {{ assistant.state.status }}
    </p>
    <form @submit.prevent="submit">
      <button
        type="button"
        class="mic"
        :class="{ listening }"
        :disabled="!Recognition"
        :aria-pressed="listening"
        :aria-label="listening ? '关闭麦克风' : '开启麦克风'"
        :title="listening ? '关闭麦克风' : '开启麦克风'"
        @click="toggleVoice"
      >
        {{ listening ? '停止' : '语音' }}</button
      ><input
        v-model="input"
        aria-label="巡检助手指令"
        maxlength="1000"
        placeholder="直接告诉小维你想查询或处理什么…"
      /><button type="submit" :disabled="!input.trim() || assistant.state.dialogueBusy">
        发送
      </button>
    </form>
    <small
      v-if="listening || voiceStatus !== initialVoiceStatus"
      class="voice-status"
      role="status"
      >{{ voiceStatus }}</small
    >
  </section>
</template>
<script setup lang="ts">
import { computed, ref, watch, nextTick, onBeforeUnmount } from 'vue';
import type { CabinetRef } from '@/types/platform-identity';
import { assessSpeechAlternatives, normalizeVoiceText } from './voice-turn.js';
import type { AgentAssistant } from './agent-assistant';
import AssistantMarkdown from './AssistantMarkdown.vue';
const props = defineProps<{ assistant: AgentAssistant }>();
const emit = defineEmits<{ inspect: [ref: CabinetRef] }>();
const contextIdentity = computed(() => props.assistant.resolveContext());
const confirmationLabel = computed(() => ({ create: '确认创建工单', assign: '确认指派', retest: '确认复检' }[props.assistant.state.pending?.kind || ''] || '确认操作'));
function inspectContext() {
  const result = props.assistant.resolveContext();
  if (result.state === 'LINKED') emit('inspect', result.ref);
}
const input = ref(''),
  log = ref<HTMLElement>(),
  readout = ref(false),
  listening = ref(false);
const Recognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
const initialVoiceStatus = Recognition
  ? '点击开启后直接说话；待机时说“小维”唤醒。'
  : '此浏览器不支持语音识别，可使用文字。';
const voiceStatus = ref(initialVoiceStatus);
let recognition: any,
  wanted = false,
  speaking = false,
  echoUntil = 0,
  armedUntil = 0,
  timer: ReturnType<typeof setTimeout> | undefined,
  pending = '',
  review = false,
  restart: ReturnType<typeof setTimeout> | undefined;
watch(
  () => [props.assistant.state.messages.length, props.assistant.state.messages.at(-1)?.text],
  async () => {
    await nextTick();
    if (log.value) log.value.scrollTop = log.value.scrollHeight;
  },
);
async function send(text: string) {
  const reply = await props.assistant.command(text);
  if (readout.value && reply && window.speechSynthesis) {
    speechSynthesis.cancel();
    speaking = true;
    const utterance = new SpeechSynthesisUtterance(reply);
    utterance.lang = 'zh-CN';
    utterance.onend = utterance.onerror = () => {
      speaking = false;
      echoUntil = Date.now() + 850;
    };
    speechSynthesis.speak(utterance);
  }
}
function submit() {
  const text = input.value.trim();
  if (text) {
    input.value = '';
    send(text);
  }
}
function stopVoice() {
  wanted = false;
  listening.value = false;
  clearTimeout(timer);
  clearTimeout(restart);
  pending = '';
  recognition?.abort();
}
function toggleVoice() {
  if (wanted) {
    stopVoice();
    voiceStatus.value = '麦克风已关闭。';
    return;
  }
  recognition ||= new Recognition();
  wanted = true;
  armedUntil = Date.now() + 45000;
  recognition.lang = 'zh-CN';
  recognition.continuous = true;
  recognition.interimResults = true;
  recognition.maxAlternatives = 3;
  recognition.onstart = () => {
    listening.value = true;
    voiceStatus.value = '正在听，请说出设备编号或巡检操作。';
  };
  recognition.onresult = (event: any) => {
    if (!wanted || speaking || Date.now() < echoUntil) return;
    for (let i = event.resultIndex; i < event.results.length; i++) {
      const result = event.results[i];
      if (!result.isFinal) continue;
      const candidate = assessSpeechAlternatives(result);
      let text = normalizeVoiceText(candidate.text);
      if (text.includes('小维')) {
        armedUntil = Date.now() + 45000;
        text = text.slice(text.indexOf('小维') + 2).replace(/^[，,\s]+/, '');
      }
      if (Date.now() > armedUntil || !text) continue;
      pending += text;
      review ||= candidate.needsReview;
      clearTimeout(timer);
      timer = setTimeout(() => {
        if (!wanted) return;
        const value = pending;
        pending = '';
        if (review) {
          input.value = value;
          voiceStatus.value = '识别不确定，请核对文字后发送。';
          review = false;
        } else {
          armedUntil = Date.now() + 45000;
          send(value);
        }
      }, 650);
    }
  };
  recognition.onerror = (event: any) => {
    if (['not-allowed', 'service-not-allowed', 'audio-capture', 'network'].includes(event.error)) {
      stopVoice();
      voiceStatus.value = `语音不可用（${event.error}），请使用文字。`;
    }
  };
  recognition.onend = () => {
    listening.value = false;
    if (wanted)
      restart = setTimeout(() => {
        try {
          recognition.start();
        } catch {
          stopVoice();
          voiceStatus.value = '语音连接暂停，请重新开启。';
        }
      }, 600);
  };
  try {
    recognition.start();
  } catch {
    stopVoice();
    voiceStatus.value = '麦克风启动失败，请使用文字。';
  }
}
onBeforeUnmount(() => {
  stopVoice();
  if (speaking) speechSynthesis.cancel();
});
</script>
<style scoped>
.alarm-choices {
  flex-shrink: 0;
  max-height: 150px;
  overflow: auto;
  padding: 8px;
  border: 1px solid #245276;
}
.alarm-choices select {
  width: 100%;
  margin-top: 6px;
  padding: 7px;
  color: #c6e2ff;
  background: #082846;
  border: 1px solid #297ba9;
  border-radius: 4px;
}
.alarm-choices p,
.alarm-context {
  color: #91abc7;
  font-size: 11px;
  overflow-wrap: anywhere;
}
.alarm-choices button {
  display: block;
  text-align: left;
  width: 100%;
  margin-bottom: 6px;
  white-space: normal;
}
.alarm-choices small {
  display: block;
  overflow-wrap: anywhere;
  font-size: 10px;
}
.messages,
.order,
.more-actions {
  scrollbar-width: thin;
  scrollbar-color: #237da5 #092441;
  scrollbar-gutter: stable;
}
.messages::-webkit-scrollbar,
.order::-webkit-scrollbar,
.more-actions::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}
.messages::-webkit-scrollbar-track,
.order::-webkit-scrollbar-track,
.more-actions::-webkit-scrollbar-track {
  background: #092441;
  border-radius: 8px;
}
.messages::-webkit-scrollbar-thumb,
.order::-webkit-scrollbar-thumb,
.more-actions::-webkit-scrollbar-thumb {
  background: #237da5;
  border-radius: 8px;
}
.messages::-webkit-scrollbar-thumb:hover,
.order::-webkit-scrollbar-thumb:hover,
.more-actions::-webkit-scrollbar-thumb:hover {
  background: #42bddb;
}
.messages::-webkit-scrollbar-button,
.order::-webkit-scrollbar-button,
.more-actions::-webkit-scrollbar-button {
  display: none;
  width: 0;
  height: 0;
}
.intro span {
  font-size: 12px !important;
  color: #31dfc1 !important;
}
.all-orders {
  font-size: 12px;
  text-decoration: none;
}
.progress {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px solid #245276;
}
.progress .stage {
  margin-left: auto;
  color: #91abc7;
  font-size: 11px;
}
.messages article {
  border: none !important;
  background: rgba(54, 167, 255, 0.06) !important;
}
.messages p {
  font-weight: 400;
}
.examples button {
  font-size: 12px;
  padding: 7px 10px;
}
.more-actions {
  font-size: 11px;
  color: #91abc7;
}
.more-actions summary {
  cursor: pointer;
  padding: 3px 0;
}
.more-actions .examples {
  margin: 8px 0;
}
.more-actions p {
  line-height: 1.6;
  margin: 6px 0;
}
.more-actions label {
  display: flex;
  align-items: center;
  gap: 4px;
}
.mic {
  font-size: 12px;
}
.mic.listening {
  color: #31dfc1;
  border-color: #31dfc1;
}
form {
  padding-top: 8px;
  border-top: 1px solid #245276;
  flex-shrink: 0;
}
.order,
.more-actions {
  flex-shrink: 0;
  max-height: 150px;
  overflow: auto;
}
.workflow-stages {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  list-style: none;
  padding: 0;
  margin: 0;
  font-size: 10px;
}
.workflow-stages li {
  padding: 4px 6px;
  border: 1px solid #245276;
  color: #91abc7;
}
.workflow-stages .active {
  color: #ffdb69;
  border-color: #ffdb69;
}
.workflow-stages .done {
  color: #31dfc1;
}
a {
  color: #5cdcf3;
}
.inspection-assistant {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  color: #c6e2ff;
  font-size: 13px;
  box-sizing: border-box;
}
.intro,
.context,
.voice {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.intro span {
  color: #5cdcf3;
  font-size: 17px;
}
.intro small,
.disclosure,
.voice-status {
  color: #91abc7;
  font-size: 11px;
}
.progress {
  font-size: 12px;
  color: #31dfc1;
}
.progress button {
  float: right;
}
progress {
  width: 100%;
  height: 5px;
  display: block;
  margin-top: 7px;
  accent-color: #31dfc1;
}
.messages {
  flex: 1;
  min-height: 80px;
  overflow: auto;
  padding-right: 5px;
}
.messages article {
  padding: 9px 11px;
  margin: 0 0 10px;
  background: rgba(54, 167, 255, 0.09);
  border: 1px solid #245276;
  border-radius: 6px;
}
.messages .user {
  margin-left: 25px;
  background: rgba(49, 223, 193, 0.08);
  border-color: #267c80;
}
.messages small {
  color: #5cdcf3;
}
.messages p {
  white-space: pre-wrap;
  line-height: 1.7;
  margin: 5px 0 0;
}
.examples {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}
.order {
  max-height: 110px;
  overflow: auto;
  border: 1px solid #245276;
  padding: 8px;
}
.order p {
  margin: 5px 0;
}
.voice label {
  font-size: 12px;
}
form {
  display: flex;
  gap: 6px;
}
input:not([type='checkbox']) {
  min-width: 0;
  flex: 1;
  padding: 10px;
  background: #082846;
  border: 1px solid #297ba9;
  color: #c6e2ff;
  border-radius: 4px;
}
button {
  color: #b4e7ff;
  background: #0c3357;
  border: 1px solid #297ba9;
  padding: 5px 8px;
  border-radius: 3px;
  cursor: pointer;
}
button:disabled {
  opacity: 0.5;
  cursor: default;
}
button:hover {
  border-color: #5cdcf3;
}
button:focus-visible,
input:focus-visible {
  outline: 2px solid #68e3ff;
  outline-offset: 2px;
}
.disclosure {
  text-align: center;
}
</style>
