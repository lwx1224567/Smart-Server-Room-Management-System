<template>
  <div class="alarm-analysis">
    <div class="tabs" role="tablist" aria-label="告警分析">
      <button
        v-for="item in tabs"
        :id="`alarm-tab-${item.id}`"
        :key="item.id"
        type="button"
        role="tab"
        :aria-selected="tab === item.id"
        :aria-controls="`alarm-panel-${item.id}`"
        :class="{ active: tab === item.id }"
        @click="tab = item.id"
      >
        {{ item.name }}
      </button>
    </div>
    <section
      v-if="tab === 'active'"
      id="alarm-panel-active"
      class="active-panel"
      role="tabpanel"
      aria-labelledby="alarm-tab-active"
    >
      <ActiveAlarms @locate="emit('locate', $event)" @create="emit('create', $event)" />
    </section>
    <section
      v-show="tab === 'trend'"
      id="alarm-panel-trend"
      class="trend-panel"
      role="tabpanel"
      aria-labelledby="alarm-tab-trend"
    >
      <div class="ranges" aria-label="时间范围">
        <button
          v-for="key in ranges"
          :key="key"
          type="button"
          :aria-pressed="range === key"
          :class="{ active: range === key }"
          @click="range = key"
        >
          {{ { '24h': '24小时', '7d': '7天', '30d': '30天' }[key] }}
        </button>
      </div>
      <div
        ref="chartElement"
        class="trend-chart"
        role="img"
        :aria-label="`${period.label}告警统计，共${total}次：${alarmLevels.map((item) => `${item.name}${item.value}次`).join('，')}`"
      />
    </section>
    <section
      v-show="tab === 'devices'"
      id="alarm-panel-devices"
      class="devices-panel"
      role="tabpanel"
      aria-labelledby="alarm-tab-devices"
    >
      <h3>高频告警设备 TOP5</h3>
      <ol>
        <li
          v-for="(device, index) in frequentDevices"
          :key="device.id"
          :class="{ critical: device.severity === 'critical' }"
        >
          <span class="rank">{{ String(index + 1).padStart(2, '0') }}</span>
          <div class="device">
            <div>
              <span>{{ device.name }}</span
              ><b>{{ device.count }}次</b>
            </div>
            <div class="track"><i :style="{ width: `${(device.count / maxCount) * 100}%` }" /></div>
          </div>
        </li>
      </ol>
    </section>
  </div>
</template>
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { init, use, type ECharts } from 'echarts/core';
import { GaugeChart, PieChart } from 'echarts/charts';
import { TooltipComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import { useMonitoringStore } from '@/stores/monitoring';
import ActiveAlarms from './ActiveAlarms.vue';
import { alarmLevels as levelDefinitions } from '@/utils/alarm-levels';
const emit = defineEmits<{ locate: [id: string]; create: [id: string] }>();
use([GaugeChart, PieChart, TooltipComponent, CanvasRenderer]);
const tabs = [
  { id: 'active', name: '活动告警' },
  { id: 'trend', name: '告警统计' },
  { id: 'devices', name: '高频设备' },
];
const tab = ref('active');
const ranges = ['24h', '7d', '30d'] as const;
const range = ref<(typeof ranges)[number]>('7d');
const monitor = useMonitoringStore();
const records = computed(() => monitor.snapshot?.alarms ?? []);
const period = computed(() => {
  const hourly = range.value === '24h',
    count = hourly ? 24 : range.value === '7d' ? 7 : 30,
    step = hourly ? 3600000 : 86400000;
  const shifted = new Date(Date.now() + 8 * 3600000),
    day =
      Date.UTC(shifted.getUTCFullYear(), shifted.getUTCMonth(), shifted.getUTCDate()) - 8 * 3600000;
  const start = day - (hourly ? 0 : count - 1) * step,
    labels = Array.from({ length: count }, (_, i) =>
      hourly
        ? `${String(i).padStart(2, '0')}:00`
        : new Date(start + i * step + 8 * 3600000).toISOString().slice(5, 10),
    );
  const values = (from: number) =>
    Array.from(
      { length: count },
      (_, i) =>
        records.value.filter((a) => {
          const at = Date.parse(a.createdAt ?? '');
          return at >= from + i * step && at < from + (i + 1) * step;
        }).length,
    );
  return {
    label: hourly ? '24小时' : `${count}天`,
    labels,
    start,
    end: start + count * step,
    current: values(start),
  };
});
const alarmLevels = computed(() => {
  const levels = levelDefinitions.map((item) => ({
    name: item.name,
    level: item.key,
    value: 0,
    itemStyle: { color: item.color },
  }));
  for (const alarm of records.value) {
    const at = Date.parse(alarm.createdAt ?? '');
    if (!(at >= period.value.start && at < period.value.end)) continue;
    const item = levels.find((item) => item.level === alarm.severity);
    if (item) item.value++;
  }
  return levels.filter((item) => item.value > 0);
});
const frequentDevices = computed(() => {
  const counts = new Map<string, { id: string; name: string; count: number; severity: string }>();
  for (const a of records.value) {
    const id = a.deviceId ?? a.cabinetId ?? a.entityId ?? a.id;
    const item = counts.get(id) ?? { id, name: id, count: 0, severity: a.severity ?? '' };
    item.count++;
    counts.set(id, item);
  }
  return [...counts.values()].sort((a, b) => b.count - a.count).slice(0, 5);
});
const total = computed(() => period.value.current.reduce((sum, value) => sum + value, 0));
const maxCount = computed(() =>
  Math.max(...frequentDevices.value.map((device) => device.count), 1),
);
const chartElement = ref<HTMLElement>();
let chart: ECharts | undefined;
let observer: ResizeObserver | undefined;
function renderChart() {
  const width = chartElement.value?.clientWidth || 420;
  const height = chartElement.value?.clientHeight || 260;
  const outerRadius = Math.min(width * 0.22, height * 0.4);
  chart?.setOption({
    backgroundColor: 'transparent',
    animationDuration: 300,
    tooltip: {
      trigger: 'item',
      confine: true,
      formatter: '{b}：{c}次（{d}%）',
      backgroundColor: '#102441',
      borderColor: '#36A7FF',
      textStyle: { color: '#c6e2ff', fontSize: 14 },
    },
    series: [
      {
        type: 'gauge',
        radius: outerRadius,
        center: ['50%', '50%'],
        startAngle: 90,
        endAngle: -270,
        min: 0,
        max: 100,
        splitNumber: 50,
        silent: true,
        pointer: { show: false },
        axisLine: { show: false, lineStyle: { width: 0 } },
        axisLabel: { show: false },
        axisTick: { show: false },
        splitLine: {
          show: true,
          distance: 0,
          length: outerRadius * 0.09,
          lineStyle: { color: '#159CFF', width: 3 },
        },
        title: { show: false },
        detail: { show: false },
        data: [{ value: 0 }],
      },
      {
        name: '告警等级',
        type: 'pie',
        radius: [outerRadius * 0.62, outerRadius * 0.78],
        center: ['50%', '50%'],
        avoidLabelOverlap: true,
        minShowLabelAngle: 1,
        label: {
          color: '#e3efff',
          fontSize: 14,
          alignTo: 'edge',
          edgeDistance: 12,
          bleedMargin: 4,
          formatter: '{count|{c}}\n{name|{b}}',
          rich: {
            count: { fontSize: 15, fontWeight: 'bold', lineHeight: 23 },
            name: { color: '#e3efff', fontSize: 14, lineHeight: 22 },
          },
        },
        labelLine: { length: outerRadius * 0.3, length2: 18, lineStyle: { width: 1 } },
        data: alarmLevels.value.map((item) => ({
          ...item,
          label: { rich: { count: { color: item.itemStyle.color } } },
          labelLine: { lineStyle: { color: item.itemStyle.color } },
        })),
        emptyCircleStyle: { color: '#173d50' },
      },
      {
        type: 'pie',
        radius: [0, outerRadius * 0.5],
        center: ['50%', '50%'],
        silent: true,
        tooltip: { show: false },
        labelLine: { show: false },
        label: {
          show: true,
          position: 'center',
          formatter: `{count|${total.value}}\n{caption|告警次数}`,
          rich: {
            count: { color: '#FFD600', fontSize: 24, fontWeight: 'bold', lineHeight: 30 },
            caption: { color: '#e3efff', fontSize: 15, lineHeight: 23 },
          },
        },
        data: [{ value: 1, itemStyle: { color: 'transparent' } }],
        emphasis: { disabled: true },
      },
    ],
  });
}
watch(alarmLevels, renderChart);
watch(tab, async () => {
  await nextTick();
  if (tab.value === 'trend') {
    chart?.resize();
    renderChart();
  }
});
onMounted(() => {
  chart = init(chartElement.value!, undefined, { renderer: 'canvas' });
  renderChart();
  observer = new ResizeObserver(() => {
    if (tab.value === 'trend') {
      chart?.resize();
      renderChart();
    }
  });
  observer.observe(chartElement.value!);
});
onBeforeUnmount(() => {
  observer?.disconnect();
  chart?.dispose();
});
</script>
<style scoped lang="scss">
.active-panel {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
.alarm-analysis {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
  font-size: 16px;
  line-height: 1.35;
  gap: 12px;
}
button {
  padding: 7px 16px;
  border: 1px solid rgba(54, 167, 255, 0.25);
  background: rgba(54, 167, 255, 0.06);
  color: #91abc7;
  font: inherit;
  cursor: pointer;
}
button.active {
  color: #5cdcf3;
  border-color: #36a7ff;
  background: rgba(54, 167, 255, 0.18);
}
button:focus-visible {
  outline: 2px solid #5cdcf3;
  outline-offset: 2px;
}
.tabs,
.ranges {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}
.ranges {
  justify-content: flex-end;
}
.ranges button {
  padding: 4px 12px;
  font-size: 13px;
}
.trend-panel {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  gap: 10px;
}
.trend-chart {
  flex: 1;
  min-height: 90px;
  width: 100%;
}
h3 {
  margin: 0 0 10px;
  font-size: 16px;
  font-weight: normal;
  color: #91abc7;
}
.track {
  height: 6px;
  background: rgba(116, 174, 224, 0.12);
}
.track i {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #36a7ff, #5cdcf3);
}
.devices-panel {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  padding: 5px 4px;
}
ol {
  display: flex;
  flex-direction: column;
  justify-content: space-around;
  flex: 1;
  padding: 0;
  margin: 0;
  list-style: none;
}
li {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 12px 0;
}
.rank {
  color: #36a7ff;
  font-size: 27px;
  font-variant-numeric: tabular-nums;
}
.device {
  flex: 1;
  min-width: 0;
  font-size: 17px;
}
.device > div:first-child {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 12px;
}
.device b {
  color: #5cdcf3;
  font-size: 22px;
  font-weight: 500;
  white-space: nowrap;
}
.critical .rank,
.critical b {
  color: #ff596b;
}
.critical .track i {
  background: #ff596b;
}
@media (max-height: 800px), (max-width: 1500px) {
  .alarm-analysis {
    font-size: 14px;
    gap: 8px;
  }
  button {
    padding: 5px 12px;
  }
  .tabs,
  .ranges {
    gap: 8px;
  }
  .ranges button {
    padding: 3px 9px;
    font-size: 12px;
  }
  .trend-panel {
    gap: 6px;
  }
  h3 {
    font-size: 14px;
    margin-bottom: 6px;
  }
  .track {
    height: 5px;
  }
  li {
    gap: 12px;
    padding: 8px 0;
  }
  .rank {
    font-size: 23px;
  }
  .device {
    font-size: 14px;
  }
  .device b {
    font-size: 18px;
  }
  .device > div:first-child {
    margin-bottom: 9px;
  }
}
</style>
