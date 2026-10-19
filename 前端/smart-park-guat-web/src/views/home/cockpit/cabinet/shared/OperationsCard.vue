<template>
  <div
    class="operations-card"
    :class="{
      'operations-card--location': kind === '设备定位',
      'operations-card--alarm': kind === '当前设备告警统计',
      'operations-card--logs': kind === '操作日志',
      'operations-card--controls': kind === '控制面板',
    }"
  >
    <dl v-if="kind === '设备定位'" class="location">
      <div class="location-row">
        <dt>设备名称</dt>
        <dd>{{ device.name }}</dd>
      </div>
      <div class="location-row">
        <dt>所属机房</dt>
        <dd>{{ platform ? device.roomName : '研发大厦 1F 机房' }}</dd>
      </div>
      <div class="location-row">
        <dt>位置</dt>
        <dd>
          {{ platform ? device.location : '办公楼 1F' }} / {{ device.zone }}区 /
          {{ device.code ?? device.id }}
        </dd>
      </div>
      <div class="location-row">
        <dt>类型</dt>
        <dd>标准机柜</dd>
      </div>
    </dl>
    <DeviceStatus v-else-if="kind === '设备状态'" :device-id="device.id" :platform="platform" />
    <template v-else-if="kind === '当前设备告警统计'">
      <p v-if="monitor.loading || monitor.error" class="alarm-empty" role="status">
        {{ monitor.error || '正在加载告警…' }}
      </p>
      <template v-else>
        <div class="alarm-summary">
          <span
            >当前告警
            <strong :class="{ 'has-alarm': alarms.length }">{{ alarms.length }}</strong> 条</span
          ><span class="alarm-cabinet" :title="device.name">{{ device.name }}</span>
        </div>
        <div v-if="alarms.length" ref="alarmListElement" class="alarm-list">
          <article
            v-for="alarm in visibleAlarms"
            :key="alarm.id"
            class="alarm-item"
            :class="`alarm-item--${alarm.severity}`"
          >
            <div class="alarm-heading">
              <h4 :title="alarm.title || '设备告警'">{{ alarm.title || '设备告警' }}</h4>
              <span class="alarm-level">{{ alarmLevelName(alarm.severity) }}</span>
            </div>
            <div class="alarm-values">
              <time v-if="alarm.createdAt" :datetime="alarm.createdAt">{{
                formatAlarmTime(alarm.createdAt)
              }}</time>
              <span :title="`${alarm.metric || '指标'}：${alarm.value ?? '未采集'}`"
                >{{ alarm.metric || '指标' }} <b>{{ alarm.value ?? '未采集' }}</b></span
              ><span :title="`阈值：${alarm.threshold ?? '未配置'}`"
                >阈值 {{ alarm.threshold ?? '未配置' }}</span
              >
            </div>
          </article>
        </div>
        <p v-else class="alarm-empty">当前无告警</p>
        <div v-if="alarms.length" class="alarm-footer">
          <span>{{
            alarmPageCount === 1
              ? '全部告警已展示'
              : `第 ${alarmPage * alarmPageSize + 1}–${Math.min((alarmPage + 1) * alarmPageSize, alarms.length)} 条，共 ${alarms.length} 条`
          }}</span>
          <nav v-if="alarmPageCount > 1" class="alarm-pagination" aria-label="告警分页">
            <button
              type="button"
              aria-label="上一页"
              :disabled="alarmPage === 0"
              @click="alarmPage--"
            >
              ‹</button
            ><span aria-live="polite">{{ alarmPage + 1 }} / {{ alarmPageCount }}</span
            ><button
              type="button"
              aria-label="下一页"
              :disabled="alarmPage >= alarmPageCount - 1"
              @click="alarmPage++"
            >
              ›
            </button>
          </nav>
        </div>
      </template>
    </template>
    <template v-else-if="kind === '控制面板'">
      <div v-for="control in state.controls" :key="control.name" class="control-row">
        <span>{{ control.name }}</span>
        <button
          type="button"
          role="switch"
          :aria-label="`${device.name} ${control.name}`"
          :aria-checked="control.on"
          :class="['switch', { on: control.on }]"
          @click="toggle(control)"
        >
          <i />
        </button>
      </div>
    </template>
    <template v-else-if="kind === '操作日志'">
      <table class="logs-header">
        <thead>
          <tr>
            <th>序号</th>
            <th>操作</th>
            <th>时间</th>
            <th>操作人</th>
          </tr>
        </thead>
      </table>
      <div class="logs-viewport">
        <table
          class="logs-track"
          :class="{ 'is-resetting': logsResetting }"
          :style="{ transform: `translateY(-${logRowIndex * 20}cqh)` }"
          @transitionend="resetLogLoop"
        >
          <tbody>
            <tr
              v-for="(log, index) in state.logs.length > 4
                ? [...state.logs, ...state.logs]
                : state.logs"
              :key="index"
            >
              <td>{{ (index % state.logs.length) + 1 }}</td>
              <td :title="log.logCode ? `${log.logCode} · ${log.action}` : log.action">
                {{ log.action }}<small v-if="log.logCode" class="log-code">{{ log.logCode }}</small>
              </td>
              <td>{{ log.time }}</td>
              <td>{{ log.user }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import DeviceStatus from '../components/DeviceStatus.vue';
import { useMonitoringStore } from '@/stores/monitoring';
import { usePlatformStore } from '@/stores/platform';
import { alarmLevelName, alarmLevelRank } from '@/utils/alarm-levels';
const monitor = useMonitoringStore();
const platformStore = usePlatformStore();
import { cabinetControls, controlKeys } from '../../../scene/cabinetState';
export interface CabinetOperations {
  controls: { name: string; on: boolean }[];
  logs: { action: string; time: string; user: string; logCode?: string }[];
}
const props = defineProps<{
  kind: string;
  device: {
    id: string;
    name: string;
    zone: string;
    code?: string;
    sceneModelKey?: string;
    roomName?: string;
    location?: string;
  };
  state: CabinetOperations;
  platform?: boolean;
}>();
const emit = defineEmits<{ control: [key: string, value: boolean] }>();
const logRowIndex = ref(0);
const logsResetting = ref(false);
let logTimer: ReturnType<typeof setInterval> | undefined;
let logResetFrame: number | undefined;
async function resetLogLoop() {
  if (logRowIndex.value < props.state.logs.length) return;
  logsResetting.value = true;
  logRowIndex.value = 0;
  await nextTick();
  logResetFrame = requestAnimationFrame(() => {
    logResetFrame = requestAnimationFrame(() => {
      logsResetting.value = false;
    });
  });
}
watch(
  () => [props.device.id, props.state.logs.length],
  () => {
    if (logResetFrame !== undefined) cancelAnimationFrame(logResetFrame);
    logsResetting.value = false;
    logRowIndex.value = 0;
  },
);
onMounted(() => {
  logTimer = setInterval(() => {
    if (props.kind === '操作日志' && props.state.logs.length > 4 && !logsResetting.value)
      logRowIndex.value += 1;
  }, 5000);
});
onBeforeUnmount(() => {
  clearInterval(logTimer);
  if (logResetFrame !== undefined) cancelAnimationFrame(logResetFrame);
});
const alarms = computed(() =>
  monitor
    .cabinetAlarms(
      props.device.id,
      (platformStore.scene?.devices ?? [])
        .filter((d) => d.cabinetId === props.device.id)
        .map((d) => d.id),
    )
    .sort(
      (a, b) =>
        alarmLevelRank(a.severity) - alarmLevelRank(b.severity) ||
        (b.createdAt ?? '').localeCompare(a.createdAt ?? ''),
    ),
);
const alarmPage = ref(0);
const alarmListElement = ref<HTMLElement>();
const alarmPageSize = ref(2);
let alarmObserver: ResizeObserver | undefined;
watch(
  alarmListElement,
  (element) => {
    alarmObserver?.disconnect();
    if (!element) return;
    const resize = () => {
      const count = element.clientHeight >= 114 ? 2 : 1;
      if (alarmPageSize.value !== count) {
        alarmPageSize.value = count;
        alarmPage.value = 0;
      }
    };
    alarmObserver = new ResizeObserver(resize);
    alarmObserver.observe(element);
    resize();
  },
  { flush: 'post' },
);
onBeforeUnmount(() => alarmObserver?.disconnect());
const alarmPageCount = computed(() =>
  Math.max(1, Math.ceil(alarms.value.length / alarmPageSize.value)),
);
const visibleAlarms = computed(() =>
  alarms.value.slice(
    alarmPage.value * alarmPageSize.value,
    (alarmPage.value + 1) * alarmPageSize.value,
  ),
);
watch(
  () => props.device.id,
  () => {
    alarmPage.value = 0;
  },
);
watch(alarmPageCount, (count) => {
  alarmPage.value = Math.min(alarmPage.value, count - 1);
});
function formatAlarmTime(value: string) {
  return new Date(value).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  });
}
function toggle(control: CabinetOperations['controls'][number]) {
  const key = controlKeys[control.name];
  if (props.platform && key) {
    emit('control', key, !control.on);
    return;
  }
  control.on = !control.on;
  if (key && cabinetControls[props.device.id]) cabinetControls[props.device.id]![key] = control.on;
  props.state.logs.unshift({
    action: `${control.on ? '开启' : '关闭'}${control.name}`,
    time: '刚刚',
    user: props.platform ? '本地演示操作者（未认证）' : '当前用户',
  });
}
</script>

<style scoped>
.operations-card {
  color: #c8eaff;
  font-size: 13px;
  padding: 8px 12px;
}
.operations-card:has(.device-status) {
  height: 100%;
  min-height: 0;
  box-sizing: border-box;
  padding: 4px 12px 8px;
}
.operations-card--logs {
  height: 100%;
  min-height: 0;
  box-sizing: border-box;
  padding: 0;
  container-type: size;
  overflow: hidden;
}
.logs-header {
  height: 20cqh;
}
.logs-viewport {
  height: 80cqh;
  overflow: hidden;
}
.logs-track {
  transition: transform 0.45s ease;
}
.logs-track.is-resetting {
  transition: none;
}
.device-caption {
  color: #65dcff;
  font-size: 12px;
  margin-bottom: 12px;
}
.operations-card--location {
  height: 100%;
  box-sizing: border-box;
  padding: 0 8px;
}
.location {
  display: grid;
  grid-template-rows: repeat(4, minmax(0, 1fr));
  height: 100%;
  box-sizing: border-box;
  margin: 0;
  color: #dff7ff;
  font-size: clamp(14px, 1vw, 20px);
  line-height: 1.5;
}
.location-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 0;
  border-bottom: 1px dashed rgba(100, 188, 220, 0.12);
}
.location-row dt {
  flex-shrink: 0;
  color: inherit;
}
.location-row dd {
  min-width: 0;
  margin: 0;
  color: #fff;
  font-size: clamp(16px, 1.15vw, 22px);
  font-weight: 700;
  overflow-wrap: anywhere;
  text-align: right;
}
dt,
.note {
  color: #7fa5bf;
}
dd {
  margin: 0;
  overflow-wrap: anywhere;
}
.operations-card--alarm {
  height: 100%;
  min-height: 0;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 0 8px;
  overflow: hidden;
}
.alarm-summary {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  flex-shrink: 0;
  color: #aac0d8;
  padding-bottom: 4px;
}
.alarm-summary > span:first-child {
  white-space: nowrap;
}
.alarm-cabinet {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
}
.alarm-summary strong {
  font-size: 26px;
  color: #31dfc1;
  font-variant-numeric: tabular-nums;
}
.alarm-summary strong.has-alarm {
  color: #ffcf65;
}
.alarm-list {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.alarm-item {
  flex: 1 0 54px;
  min-height: 54px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
  padding: 5px 10px;
  border-left: 3px solid #36a7ff;
  background: rgba(11, 42, 73, 0.65);
}
.alarm-item--critical {
  border-left-color: #ff596b;
}
.alarm-item--warning {
  border-left-color: #ffd263;
}
.alarm-item--info {
  border-left-color: #31dfc1;
}
.alarm-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.alarm-heading h4 {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  color: #e0f2ff;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
}
.alarm-level {
  flex-shrink: 0;
  color: #36a7ff;
  font-size: 11px;
  line-height: 1.8;
}
.alarm-item--critical .alarm-level {
  color: #ff596b;
}
.alarm-item--warning .alarm-level {
  color: #ffd263;
}
.alarm-item--info .alarm-level {
  color: #31dfc1;
}
.alarm-heading,
.alarm-values {
  flex-shrink: 0;
}
.alarm-values {
  display: flex;
  flex-wrap: nowrap;
  gap: 8px;
  color: #bdd5eb;
  font-size: 11px;
  line-height: 1.5;
}
.alarm-values time {
  color: #91abc7;
  white-space: nowrap;
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}
.alarm-values span {
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.alarm-values b {
  font-weight: 500;
  color: #e0f2ff;
}
.alarm-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-shrink: 0;
  min-height: 22px;
  color: #91abc7;
  font-size: 11px;
}
.alarm-pagination {
  display: flex;
  align-items: center;
  gap: 8px;
  font-variant-numeric: tabular-nums;
}
.alarm-pagination button {
  width: 24px;
  height: 22px;
  padding: 0;
  border: 1px solid #24557b;
  background: #0c3357;
  color: #c8eaff;
  cursor: pointer;
  font-size: 16px;
}
.alarm-pagination button:disabled {
  opacity: 0.35;
  cursor: default;
}
.alarm-pagination button:focus-visible {
  outline: 2px solid #65dcff;
  outline-offset: 2px;
}
.alarm-empty {
  flex: 1;
  display: grid;
  place-items: center;
  margin: 0;
  color: #91abc7;
  text-align: center;
}
.operations-card--controls {
  height: 100%;
  min-height: 0;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  padding: 0 12px;
}
.control-row {
  display: flex;
  flex: 1;
  min-height: 0;
  align-items: center;
  justify-content: space-between;
}
.switch {
  width: 34px;
  height: 19px;
  padding: 2px;
  border: 1px solid #50728a;
  border-radius: 12px;
  background: #284458;
  cursor: pointer;
}
.switch i {
  display: block;
  width: 13px;
  height: 13px;
  border-radius: 50%;
  background: white;
  transition: transform 0.2s;
}
.switch.on {
  background: #168ea5;
  border-color: #39d8ff;
}
.switch.on i {
  transform: translateX(14px);
}
.switch:focus-visible {
  outline: 2px solid #65dcff;
  outline-offset: 3px;
}
.note {
  font-size: 11px;
  margin: 8px 0 0;
}
table {
  width: 100%;
  table-layout: fixed;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 11px;
  text-align: left;
}
th,
td {
  box-sizing: border-box;
  height: 20cqh;
  padding: 0 4px;
  border-bottom: 1px solid rgba(116, 174, 224, 0.12);
  line-height: 16px;
}
th {
  color: #aac0d8;
  font-weight: 400;
}
td {
  color: #c6e2ff;
}
.log-code {
  display: block;
  color: #91abc7;
  font-size: 10px;
  line-height: 14px;
  white-space: nowrap;
}
</style>
