<template>
  <div ref="container" class="room-events" :style="{ '--row-height': `${rowHeight}px` }">
    <table>
      <colgroup>
        <col class="description-column" />
        <col class="location-column" />
        <col class="level-column" />
        <col class="time-column" />
      </colgroup>
      <thead>
        <tr>
          <th>事件信息</th>
          <th>地点</th>
          <th>等级</th>
          <th>时间</th>
        </tr>
      </thead>
    </table>
    <p v-if="!events.length" class="empty">
      {{ monitor.error || (monitor.loading ? '正在加载…' : '暂无事件') }}
    </p>
    <div v-else class="events-viewport">
      <table
        class="events-track"
        :class="{ 'is-resetting': resetting }"
        :style="{ transform: `translateY(-${rowIndex * rowHeight}px)` }"
        @transitionend="resetLoop"
      >
        <colgroup>
          <col class="description-column" />
          <col class="location-column" />
          <col class="level-column" />
          <col class="time-column" />
        </colgroup>
        <tbody>
          <tr v-for="(event, index) in [...events, ...events]" :key="`${event.id}-${index}`">
            <td>
              <span
                class="ellipsis description"
                :title="event.description"
                @mouseenter="startDescription"
                @mouseleave="stopDescription"
                ><span class="description-text">{{ event.description }}</span></span
              >
            </td>
            <td>
              <span class="ellipsis" :title="event.location || '--'">{{
                event.location || '--'
              }}</span>
            </td>
            <td>
              <span class="alarm-level" :class="`alarm-level--${event.level}`">{{
                levelLabels[event.level]
              }}</span>
            </td>
            <td class="event-time">{{ event.time }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, watch, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { useMonitoringStore } from '@/stores/monitoring';
import { usePlatformStore } from '@/stores/platform';
import { alarmLevelName } from '@/utils/alarm-levels';

const rowIndex = ref(0);
const resetting = ref(false);
const container = ref<HTMLElement>();
const rowHeight = ref(34);
let observer: ResizeObserver | undefined;
let descriptionHovered = false;
let scrollTimer: ReturnType<typeof setInterval> | undefined;
let resetFrame: number | undefined;

function startDescription(event: MouseEvent) {
  descriptionHovered = true;
  const element = event.currentTarget as HTMLElement;
  const text = element.firstElementChild as HTMLElement;
  const distance = Math.max(0, text.scrollWidth - element.clientWidth);
  if (!distance) return;
  element.style.setProperty('--scroll-start', `-${distance}px`);
  element.style.setProperty('--scroll-duration', `${Math.max(3, distance / 35)}s`);
  element.classList.add('is-scrolling');
}

function stopDescription(event: MouseEvent) {
  descriptionHovered = false;
  (event.currentTarget as HTMLElement).classList.remove('is-scrolling');
}

async function resetLoop() {
  if (rowIndex.value < events.value.length) return;
  resetting.value = true;
  rowIndex.value = 0;
  await nextTick();
  resetFrame = requestAnimationFrame(() => {
    resetFrame = requestAnimationFrame(() => {
      resetting.value = false;
    });
  });
}

onMounted(() => {
  observer = new ResizeObserver(() => {
    rowHeight.value = container.value!.clientHeight / 5;
  });
  observer.observe(container.value!);
  scrollTimer = setInterval(() => {
    if (!descriptionHovered && events.value.length > 4 && !resetting.value) rowIndex.value += 1;
  }, 5000);
});
onBeforeUnmount(() => {
  observer?.disconnect();
  clearInterval(scrollTimer);
  if (resetFrame !== undefined) cancelAnimationFrame(resetFrame);
});

type AlarmLevel = 'critical' | 'warning' | 'normal' | 'info';
const levelLabels: Record<AlarmLevel, string> = {
  critical: alarmLevelName('critical'),
  warning: alarmLevelName('warning'),
  normal: alarmLevelName('normal'),
  info: alarmLevelName('info'),
};
const monitor = useMonitoringStore(),
  platform = usePlatformStore();
const events = computed(() =>
  [
    ...(monitor.snapshot?.events ?? []),
    ...(monitor.snapshot?.alarms ?? []).filter((a) => a.status !== '已恢复'),
  ]
    .map((item) => ({
      id: item.id,
      description: item.title || item.description || '',
      location:
        item.location ||
        platform.scene?.cabinets.find((c) => c.id === item.cabinetId)?.name ||
        platform.room?.name,
      level: (['critical', 'warning', 'normal', 'info'].includes(item.severity ?? '')
        ? item.severity
        : 'info') as AlarmLevel,
      time: item.createdAt
        ? new Date(item.createdAt).toLocaleTimeString('zh-CN', { hour12: false })
        : '--',
      createdAt: item.createdAt ?? '',
    }))
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
);
watch(events, () => {
  if (resetFrame !== undefined) cancelAnimationFrame(resetFrame);
  rowIndex.value = 0;
  resetting.value = false;
});
</script>

<style scoped lang="scss">
.room-events {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}
.room-events > table {
  flex: 0 0 var(--row-height);
  height: var(--row-height);
}
.events-viewport {
  flex: 0 0 calc(var(--row-height) * 4);
  min-height: 0;
  overflow: hidden;
}
.events-track {
  transition: transform 0.45s ease;
}
.events-track.is-resetting {
  transition: none;
}
table {
  width: 100%;
  table-layout: fixed;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 11px;
  text-align: left;
}
.description-column {
  width: 31%;
}
.location-column {
  width: 29%;
}
.level-column {
  width: 21%;
}
.time-column {
  width: 19%;
}
th {
  background: transparent;
  color: #aac0d8;
  font-weight: 400;
}
th,
td {
  box-sizing: border-box;
  height: var(--row-height);
  padding: 0 4px;
  border-bottom: 1px solid rgba(116, 174, 224, 0.12);
  line-height: 16px;
}
td {
  color: #c6e2ff;
}
tbody tr:hover {
  background: rgba(72, 145, 217, 0.1);
}
.ellipsis {
  display: block;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.description-text {
  display: inline-block;
}
.description.is-scrolling {
  text-overflow: clip;
}
.description.is-scrolling .description-text {
  animation: description-scroll var(--scroll-duration) linear infinite;
}
@keyframes description-scroll {
  0%,
  10% {
    transform: translateX(var(--scroll-start));
  }
  90%,
  100% {
    transform: translateX(0);
  }
}
.alarm-level {
  white-space: nowrap;
}
.alarm-level--critical {
  color: #ff596b;
}
.alarm-level--warning {
  color: #ffd263;
}
.alarm-level--normal {
  color: #36a7ff;
}
.alarm-level--info {
  color: #31dfc1;
}
.event-time {
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
  color: #91abc7;
}
</style>
