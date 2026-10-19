<template>
  <div class="alarms">
    <small v-if="monitor.error" role="status">{{ monitor.error }}</small>
    <input v-model="keyword" placeholder="编号 / 告警名称" aria-label="搜索告警" />
    <div
      ref="scrollElement"
      class="alarm-scroll"
      :style="{ '--alarm-row-height': `${alarmRowHeight}px` }"
      aria-label="活动告警列表"
    >
      <article v-for="alarm in alarms" :key="alarm.id" :class="`alarm-${alarm.severity}`">
        <header>
          <b :title="alarm.title">{{ alarm.title || '设备告警' }}</b
          ><span class="level">{{ alarmLevelName(alarm.severity) }}</span>
        </header>
        <p
          class="metrics"
          :title="`${alarm.metric || '指标'}：${alarm.value ?? '未采集'} · 阈值 ${alarm.threshold ?? '未配置'}`"
        >
          {{ alarm.metric || '指标' }}：{{ alarm.value ?? '未采集' }} · 阈值
          {{ alarm.threshold ?? '未配置' }}
        </p>
        <p class="advice" :title="alarm.advice || '暂无处理建议'">
          {{ alarm.advice || '暂无处理建议' }}
        </p>
        <div class="alarm-meta">
          <small v-if="alarm.alarmCode">{{ alarm.alarmCode }}</small>
          <time :datetime="alarm.createdAt">{{ formatTime(alarm.createdAt) }}</time>
        </div>
        <footer>
          <span
            class="order"
            :title="`${orderFor(alarm.id)?.orderCode ?? orderFor(alarm.id)?.id ?? '未创建工单'} · ${orderFor(alarm.id)?.status ?? alarm.status ?? '未处理'}`"
            >{{ orderFor(alarm.id)?.orderCode ?? orderFor(alarm.id)?.id ?? '未创建工单' }} ·
            {{ orderFor(alarm.id)?.status ?? alarm.status }}</span
          >
          <div class="actions">
            <button type="button" @click="locate(alarm)">定位</button
            ><button type="button" @click="create(alarm)">
              {{ orderFor(alarm.id) ? '查看工单' : '创建工单' }}
            </button>
          </div>
        </footer>
      </article>
      <p v-if="!alarms.length" class="empty">
        {{ monitor.snapshot ? '暂无匹配活动告警' : '数据尚未加载' }}
      </p>
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount } from 'vue';
import { alarmLevelName } from '@/utils/alarm-levels';
import { ElMessage } from 'element-plus';
import { useMonitoringStore, type MonitorRecord } from '@/stores/monitoring';
import { usePlatformStore } from '@/stores/platform';
import { frontendOrders, createOrder } from '../../../../../services/work-orders';
const monitor = useMonitoringStore(),
  platform = usePlatformStore(),
  keyword = ref('');
const scrollElement = ref<HTMLElement>();
const alarmRowHeight = ref(170);
let scrollObserver: ResizeObserver | undefined;
onMounted(() => {
  const element = scrollElement.value;
  if (!element) return;
  const resize = () => {
    alarmRowHeight.value = Math.max(0, (element.clientHeight - 8) / 2);
  };
  scrollObserver = new ResizeObserver(resize);
  scrollObserver.observe(element);
  resize();
});
onBeforeUnmount(() => scrollObserver?.disconnect());
const emit = defineEmits<{ locate: [key: string]; create: [key: string] }>();
const alarms = computed(() =>
  (monitor.snapshot?.alarms ?? []).filter(
    (a) =>
      a.status !== '已恢复' &&
      `${a.alarmCode ?? ''} ${a.id} ${a.title}`.toLowerCase().includes(keyword.value.trim().toLowerCase()),
  ),
);
function orderFor(id: string) {
  return frontendOrders.find((o) => o.alarmEvent === id && o.status !== '已完成');
}
function formatTime(value?: string) {
  if (!value) return '时间未记录';
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? '时间未记录'
    : date.toLocaleString('zh-CN', {
        timeZone: 'Asia/Shanghai',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false,
      });
}
function locate(a: MonitorRecord) {
  const cabinet = platform.scene?.cabinets.find((c) => c.id === a.cabinetId);
  if (cabinet) emit('locate', cabinet.sceneModelKey);
  else ElMessage.warning('未关联机柜场景');
}
async function create(a: MonitorRecord) {
  try {
    const order =
      orderFor(a.id) ??
      (await createOrder({
        roomId: a.roomId,
        cabinetId: a.cabinetId,
        deviceId: a.deviceId,
        device:
          platform.scene?.cabinets.find((c) => c.id === a.cabinetId)?.sceneModelKey ??
          a.entityId ??
          a.id,
        alarmEvent: a.id,
        title: a.title,
        priority: a.severity === 'critical' ? '紧急' : '高',
        note: a.advice ?? '',
        source: '告警转工单',
        recordOrigin: a.recordOrigin,
      }));
    ElMessage.success(`工单 ${order.id} · ${order.status}`);
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
}
</script>
<style scoped>
.alarms {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow: hidden;
  color: #c6e2ff;
  font-size: 12px;
}
small,
time {
  color: #91abc7;
  font-size: 11px;
}
input {
  flex-shrink: 0;
  width: 100%;
  box-sizing: border-box;
  margin: 0;
  background: #082846;
  color: #c6e2ff;
  border: 1px solid #297ba9;
  padding: 7px 8px;
}
.alarm-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  scrollbar-gutter: stable;
  scrollbar-width: thin;
  scrollbar-color: rgba(36, 191, 255, 0.5) transparent;
  display: grid;
  grid-auto-rows: var(--alarm-row-height);
  gap: 8px;
  align-content: start;
}
.alarm-meta {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 4px;
}
article {
  --level-color: #36a7ff;
  box-sizing: border-box;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding: 10px;
  border: 1px solid #245276;
  border-left: 3px solid var(--level-color);
  background: rgba(11, 42, 73, 0.65);
}
.alarm-critical {
  --level-color: #ff596b;
}
.alarm-warning {
  --level-color: #ffd263;
}
.alarm-info {
  --level-color: #31dfc1;
}
header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-shrink: 0;
}
header b {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #e0f2ff;
  font-size: 13px;
}
.level {
  color: var(--level-color);
  white-space: nowrap;
  flex-shrink: 0;
  font-size: 11px;
}
p {
  margin: 0;
  line-height: 1.5;
}
.metrics {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex-shrink: 0;
}
.advice {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  color: #aac0d8;
  min-height: 0;
}
time {
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}
footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-shrink: 0;
  margin-top: auto;
}
.order {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
  color: #91abc7;
}
.actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}
button {
  color: #b4e7ff;
  background: #0c3357;
  border: 1px solid #297ba9;
  padding: 4px 7px;
  cursor: pointer;
  font-size: 11px;
  white-space: nowrap;
}
button:focus-visible,
input:focus-visible {
  outline: 2px solid #32e8f2;
  outline-offset: 2px;
}
.alarm-scroll::-webkit-scrollbar {
  width: 6px;
}
.alarm-scroll::-webkit-scrollbar-thumb {
  background: rgba(36, 191, 255, 0.5);
  border-radius: 3px;
}
.empty {
  grid-row: span 2;
  display: grid;
  place-items: center;
  color: #91abc7;
}
</style>
