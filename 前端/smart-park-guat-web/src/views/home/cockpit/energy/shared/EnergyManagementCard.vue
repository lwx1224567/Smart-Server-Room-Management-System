<template>
  <div class="energy-card">
    <small v-if="kind !== '实时能耗概览'" class="energy-source">{{
      period + (monitor.energyError ? ' · ' + monitor.energyError : '')
    }}</small>
    <div v-if="kind === '实时能耗概览'" class="overview">
      <div v-for="item in overview" :key="item.label" class="kpi">
        <span>{{ item.label }}</span
        ><strong
          >{{ formatEnergy(item.value) }} <small>{{ item.unit }}</small></strong
        >
      </div>
    </div>
    <div v-else-if="kind === '能耗分类统计'" class="mix">
      <div
        ref="mixElement"
        class="donut"
        role="img"
        :aria-label="`${period}分类能耗 ${formatEnergy(energyData.total)} kWh`"
      />
      <div class="mix-legend">
        <div v-for="item in shares" :key="item.name">
          <i :style="{ background: item.color }" /><span>{{ item.name }}</span
          ><b>{{ formatEnergy(item.consumption) }} kWh · {{ formatEnergy(item.value, '--') }}%</b>
        </div>
      </div>
    </div>
    <div v-else-if="kind === '用电异常告警'" class="alerts">
      <div v-if="monitor.loading" class="empty" role="status">正在加载用电告警…</div>
      <div v-else-if="monitor.error" class="empty" role="alert">{{ monitor.error }}</div>
      <div v-else-if="!monitor.snapshot" class="empty" role="status">用电告警数据尚未加载</div>
      <div v-else-if="!alerts.length" class="empty" role="status">
        近{{ period }}暂无未恢复的用电异常告警
      </div>
      <template v-else>
        <div v-for="item in alerts" :key="item.id" class="alert">
          <b :class="item.level">{{ item.title }}</b>
          <p>{{ item.detail }}</p>
          <time v-if="item.time" class="alert-time" :datetime="item.time">{{
            formatAlertTime(item.time)
          }}</time>
        </div>
      </template>
    </div>
    <div v-else-if="kind === 'PUE 指标'" class="pue">
      <div ref="pueElement" class="gauge" role="img" aria-label="PUE 采集指标" />
      <div class="pue-info">
        <div v-for="item in pueMetrics" :key="item.label">
          {{ item.label }}：<b>{{ item.value }}</b>
        </div>
      </div>
    </div>
    <div v-else-if="kind === '机柜能耗 TOP5'" class="ranking">
      <div
        v-show="rankingTop5.length"
        ref="rankingElement"
        class="chart"
        role="img"
        aria-label="机柜能耗 TOP5，按能耗从高到低排列，单位 kWh"
      />
      <div v-if="!rankingTop5.length" class="empty">暂无数据</div>
    </div>
    <template v-else-if="kind === '机房能耗趋势分析'">
      <div class="trend-toolbar">
        <div class="trend-legend" aria-label="能耗图例">
          <button
            v-for="item in trendSeries"
            :key="item.name"
            type="button"
            :aria-pressed="trendSelected[item.name]"
            :class="{ inactive: !trendSelected[item.name] }"
            @click="toggleTrend(item.name)"
          >
            <i :style="{ background: item.color }" />{{ item.name }}
          </button>
        </div>
        <div class="periods" aria-label="能耗趋势时间范围">
          <button
            v-for="item in periods"
            :key="item"
            type="button"
            :aria-pressed="period === item"
            :class="{ active: period === item }"
            @click="period = item"
          >
            {{ item }}
          </button>
        </div>
      </div>
      <div class="trend-chart">
        <span class="trend-unit">单位：kWh</span>
        <div
          ref="chartElement"
          class="chart"
          role="img"
          :aria-label="`${period}${trendSeries.map((item) => item.name).join('、')}能耗趋势，单位 kWh`"
        />
        <span v-if="!trendData.available" class="trend-empty">暂无能耗数据</span>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { energyData, energyPeriod as period, energyPeriods as periods } from '../energy-data';
import { isElectricalAlarm } from '../energy-alarms';
import { useMonitoringStore } from '@/stores/monitoring';
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { graphic, init, use, type ECharts } from 'echarts/core';
import { BarChart, LineChart, PieChart } from 'echarts/charts';
import {
  GraphicComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';

use([
  BarChart,
  LineChart,
  PieChart,
  GraphicComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  CanvasRenderer,
]);
defineProps<{ kind: string }>();
const monitor = useMonitoringStore();
function formatEnergy(value: number | string | null | undefined, missing = '未采集') {
  return typeof value === 'number' && Number.isFinite(value)
    ? value.toFixed(1)
    : typeof value === 'string'
      ? value
      : missing;
}
function formatAlertTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '时间未记录';
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  })
    .format(date)
    .replace(/\//g, '-');
}
const value = (metric: string) =>
  monitor.reading('ROOM', monitor.snapshot?.roomId ?? '', metric)?.value ?? '未采集';
const overview = computed(() => [
  { label: '今日用电', value: value('todayKwh'), unit: 'kWh' },
  { label: '本月累计', value: value('monthKwh'), unit: 'kWh' },
  { label: '当前功率', value: value('power'), unit: 'kW' },
  { label: '今日峰值', value: value('peakPower'), unit: 'kW' },
]);
const periodWindow = computed(() => {
  const now = new Date(monitor.snapshot?.generatedAt ?? Date.now());
  const day = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(now);
  const end = new Date(`${day}T00:00:00+08:00`).getTime() + 86400000;
  return { start: end - (period.value === '7天' ? 7 : 30) * 86400000, end };
});
function inPeriod(time?: string) {
  const timestamp = time ? new Date(time).getTime() : NaN;
  return timestamp >= periodWindow.value.start && timestamp < periodWindow.value.end;
}
function periodAverage(metric: string) {
  const readings = (monitor.snapshot?.readings ?? []).filter(
    (item) =>
      item.entityType === 'ROOM' &&
      item.entityId === monitor.snapshot?.roomId &&
      item.metric === metric &&
      item.recordOrigin === 'REAL' &&
      item.quality === 'GOOD' &&
      Number.isFinite(item.value) &&
      inPeriod(item.observedAt),
  );
  return readings.length
    ? readings.reduce((sum, item) => sum + item.value, 0) / readings.length
    : null;
}
const periodPue = computed(() => periodAverage('pue'));
const pueMetrics = computed(() => [
  { label: `${period.value}平均 PUE`, value: formatEnergy(periodPue.value) },
  { label: `${period.value}平均机房功率 kW`, value: formatEnergy(periodAverage('power')) },
  { label: `${period.value}平均 IT 功率 kW`, value: formatEnergy(periodAverage('itPower')) },
]);
const shares = computed(() => energyData.value.shares);
const mixElement = ref<HTMLElement>();
let mixChart: ECharts | undefined;
const pueElement = ref<HTMLElement>();
let pueChart: ECharts | undefined;
function renderPue() {
  pueChart?.setOption({
    animationDuration: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 800,
    graphic: [
      {
        type: 'text',
        left: 'center',
        top: '32%',
        style: { text: 'PUE', fill: '#91abc7', fontSize: 10 },
      },
      {
        type: 'text',
        left: 'center',
        top: '49%',
        style: {
          text: formatEnergy(periodPue.value),
          fill: '#52e4b1',
          fontSize: 16,
          fontWeight: 'bold',
        },
      },
    ],
    series: [
      {
        type: 'pie',
        radius: ['65%', '90%'],
        center: ['50%', '50%'],
        silent: true,
        label: { show: false },
        labelLine: { show: false },
        data: [
          { value: periodPue.value === null ? 0 : 100, itemStyle: { color: '#3ce3a3' } },
          { value: periodPue.value === null ? 100 : 0, itemStyle: { color: '#173d50' } },
        ],
      },
    ],
  });
}
function renderMix() {
  const totalText = formatEnergy(energyData.value.total);
  const textContext = document.createElement('canvas').getContext('2d');
  if (textContext) textContext.font = 'bold 23px sans-serif';
  const textWidth = textContext?.measureText(totalText).width ?? totalText.length * 23;
  const innerWidth =
    Math.min(mixElement.value?.clientWidth ?? 80, mixElement.value?.clientHeight ?? 80) * 0.55;
  const totalFontSize = Math.min(23, (23 * innerWidth) / Math.max(textWidth, 1));
  mixChart?.setOption({
    animationDuration: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 800,
    tooltip: {
      trigger: 'item',
      confine: true,
      formatter: (params: unknown) => {
        const item = params as { name: string; percent?: number };
        return `${item.name}：${formatEnergy(item.percent, '--')}%`;
      },
      backgroundColor: '#102441',
      borderColor: '#39d8ff',
      textStyle: { color: '#e3efff', fontSize: 11 },
    },
    graphic: [
      {
        type: 'text',
        left: 'center',
        top: '36%',
        style: {
          text: totalText,
          fill: '#e3efff',
          fontSize: totalFontSize,
          fontFamily: 'sans-serif',
          fontWeight: 'bold',
        },
      },
      {
        type: 'text',
        left: 'center',
        top: '58%',
        style: { text: 'kWh', fill: '#91abc7', fontSize: 11 },
      },
    ],
    series: [
      {
        type: 'pie',
        radius: ['65%', '90%'],
        center: ['50%', '50%'],
        label: { show: false },
        labelLine: { show: false },
        emphasis: { scaleSize: 3 },
        data: shares.value.map((item) => ({
          name: item.name,
          value: item.value,
          itemStyle: { color: item.color },
        })),
      },
    ],
  });
}
const alerts = computed(() =>
  (monitor.snapshot?.alarms ?? [])
    .filter((a) => isElectricalAlarm(a) && a.status !== '已恢复' && inPeriod(a.createdAt))
    .map((a) => ({ id: a.id, title: a.title, detail: a.advice, time: a.createdAt, level: a.severity })),
);
const ranks = computed(() => monitor.energy?.ranks ?? []);
const rankingTop5 = computed(() => [...ranks.value].sort((a, b) => b.value - a.value).slice(0, 5));
const rankingElement = ref<HTMLElement>();
let rankingChart: ECharts | undefined;
function renderRanking() {
  const duration = window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 3000;
  rankingChart?.setOption({
    animationDuration: duration,
    animationDurationUpdate: duration,
    animationEasingUpdate: 'linear',
    grid: { left: 0, right: 78, top: 4, bottom: 4, containLabel: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      confine: true,
      backgroundColor: '#102441',
      borderColor: '#39d8ff',
      textStyle: { color: '#e3efff', fontSize: 11 },
      valueFormatter: (value: number) => `${value.toFixed(1)} kWh`,
    },
    xAxis: {
      type: 'value',
      show: false,
      min: 0,
      max: Math.max(...rankingTop5.value.map((item) => item.value), 1),
    },
    yAxis: {
      type: 'category',
      inverse: true,
      data: rankingTop5.value.map((item) => item.name),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#e3efff', fontSize: 16, margin: 16 },
      animationDuration: duration,
      animationDurationUpdate: duration,
    },
    series: [
      {
        id: 'cabinet-energy-ranking',
        type: 'bar',
        barWidth: '45%',
        label: {
          show: true,
          position: 'right',
          color: '#c6e2ff',
          fontSize: 11,
          formatter: (params: unknown) =>
            `${formatEnergy((params as { value: number }).value)} kWh`,
        },
        itemStyle: {
          borderRadius: 100,
          color: new graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: '#3478e7' },
            { offset: 1, color: '#2db9ed' },
          ]),
        },
        data: rankingTop5.value.map((item) => ({ name: item.name, value: item.value })),
      },
    ],
  });
  rankingChart?.resize();
}
watch(pueMetrics, renderPue, { deep: true, flush: 'post' });
watch(rankingTop5, renderRanking, { deep: true, flush: 'post' });
const rawTrendSeries = computed(() => [
  { name: '总耗能', color: '#45d9a5', values: energyData.value.totals },
  ...energyData.value.topTwo,
]);
const trendData = computed(() => {
  const labels = energyData.value.labels,
    series = rawTrendSeries.value;
  const hasData = (index: number) =>
    series.some(
      (item) => typeof item.values[index] === 'number' && Number.isFinite(item.values[index]),
    );
  const first = labels.findIndex((_, index) => hasData(index));
  if (first < 0)
    return {
      labels: [],
      series: series.map((item) => ({ ...item, values: [] })),
      available: false,
    };
  let last = labels.length - 1;
  while (last > first && !hasData(last)) last--;
  return {
    labels: labels.slice(first, last + 1),
    series: series.map((item) => ({ ...item, values: item.values.slice(first, last + 1) })),
    available: true,
  };
});
const trendSeries = computed(() => trendData.value.series);
const trendSelected = ref<Record<string, boolean>>({ 总耗能: true, 机柜: true, 空调: true });
function toggleTrend(name: string) {
  trendSelected.value[name] = !trendSelected.value[name];
  chart?.dispatchAction({
    type: trendSelected.value[name] ? 'legendSelect' : 'legendUnSelect',
    name,
  });
}
const chartElement = ref<HTMLElement>();
let chart: ECharts | undefined;
let observer: ResizeObserver | undefined;
function renderChart() {
  const labels = trendData.value.labels;
  chart?.setOption({
    animationDuration: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 450,
    color: ['#45d9a5', '#39aaff', '#7b83ff'],
    legend: {
      show: false,
      data: trendSeries.value.map((item) => item.name),
      selected: { ...trendSelected.value },
    },
    grid: { left: 30, right: 6, top: 22, bottom: 18 },
    tooltip: {
      trigger: 'axis',
      confine: true,
      backgroundColor: '#102441',
      borderColor: '#39d8ff',
      textStyle: { color: '#e3efff', fontSize: 11 },
      valueFormatter: (value: number) => `${formatEnergy(value)} kWh`,
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: labels,
      axisTick: { show: false },
      axisLabel: { color: '#91abc7', fontSize: 9, hideOverlap: true },
      axisLine: { lineStyle: { color: '#234a69' } },
    },
    yAxis: {
      type: 'value',
      min: 0,
      axisLabel: { color: '#91abc7', fontSize: 9 },
      splitNumber: 3,
      splitLine: { lineStyle: { color: 'rgba(116,174,224,.16)', type: 'dashed' } },
    },
    series: trendSeries.value.map((item) => ({
      name: item.name,
      type: 'line',
      smooth: true,
      symbol: labels.length === 1 ? 'circle' : 'none',
      symbolSize: 6,
      connectNulls: false,
      lineStyle: { width: 2, color: item.color },
      itemStyle: { color: item.color },
      areaStyle: {
        color: new graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: item.color + '80' },
          { offset: 1, color: 'rgba(0,0,0,0)' },
        ]),
      },
      data: item.values,
    })),
  });
}
watch(energyData, () => {
  for (const item of trendSeries.value)
    if (!(item.name in trendSelected.value)) trendSelected.value[item.name] = true;
  renderMix();
  renderChart();
});
onMounted(() => {
  if (pueElement.value) {
    pueChart = init(pueElement.value);
    renderPue();
    observer = new ResizeObserver(() => pueChart?.resize());
    observer.observe(pueElement.value);
  }
  if (mixElement.value) {
    mixChart = init(mixElement.value);
    renderMix();
    observer = new ResizeObserver(() => {
      mixChart?.resize();
      renderMix();
    });
    observer.observe(mixElement.value);
  }
  if (rankingElement.value) {
    rankingChart = init(rankingElement.value);
    renderRanking();
    observer = new ResizeObserver(() => rankingChart?.resize());
    observer.observe(rankingElement.value);
  }
  if (!chartElement.value) return;
  chart = init(chartElement.value);
  renderChart();
  observer = new ResizeObserver(() => chart?.resize());
  observer.observe(chartElement.value);
});
onBeforeUnmount(() => {
  observer?.disconnect();
  chart?.dispose();
  rankingChart?.dispose();
  mixChart?.dispose();
  pueChart?.dispose();
});
</script>

<style scoped lang="scss">
.energy-source {
  color: #91abc7;
  margin-bottom: 5px;
  font-size: 10px;
}
.energy-card {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  font-size: 12px;
}
.overview {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-template-rows: repeat(2, minmax(0, 1fr));
  gap: 8px;
}
.kpi {
  min-width: 0;
  min-height: 0;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 8px;
  border: 1px solid rgba(47, 170, 211, 0.2);
  background: rgba(8, 49, 73, 0.28);
  span {
    color: #91abc7;
    font-size: clamp(12px, 1vw, 16px);
  }
  strong {
    margin-top: 6px;
    font-size: clamp(16px, 1.3vw, 24px);
    color: #4edcff;
    white-space: nowrap;
  }
}
small {
  font-size: 10px;
  font-weight: normal;
}
.mix,
.pue {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-evenly;
  gap: 12px;
}
.donut,
.gauge {
  width: clamp(80px, 7vw, 118px);
  aspect-ratio: 1;
  border-radius: 50%;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  > div {
    width: 68%;
    height: 68%;
    border-radius: 50%;
    background: #082236;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 3px;
  }
  strong {
    font-size: 23px;
  }
  span {
    color: #91abc7;
    font-size: 11px;
  }
}
.mix-legend {
  flex: 1;
  max-width: 180px;
  > div {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 6px 0;
  }
  i {
    width: 7px;
    height: 7px;
  }
  b {
    margin-left: auto;
    color: #e3efff;
  }
}
.alerts {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: rgba(80, 170, 205, 0.35) transparent;
}
.alert-time {
  display: block;
  flex-shrink: 0;
  color: #91abc7;
  font-size: 11px;
  line-height: 1.4;
  font-variant-numeric: tabular-nums;
}
.alert {
  flex: 1 0 auto;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
  padding: 4px 0;
  box-sizing: border-box;
  border-bottom: 1px dashed rgba(80, 170, 205, 0.18);
  &:last-child {
    border: 0;
  }
  b {
    flex-shrink: 0;
    font-size: 12px;
    line-height: 1.4;
  }
  p {
    flex-shrink: 0;
    margin: 0;
    color: #91abc7;
    font-size: 11px;
    line-height: 1.4;
  }
}
.critical {
  color: #ff596b;
}
.warning {
  color: #ffd263;
}
.normal {
  color: #36a7ff;
}
.info {
  color: #31dfc1;
}
.good {
  color: #3ce3a3;
}
.pue-info {
  line-height: 1.9;
  color: #91abc7;
  b {
    color: #e3efff;
  }
  .good {
    color: #3ce3a3;
  }
}
.ranking {
  flex: 1;
  min-height: 0;
  width: 100%;
  display: flex;
}
.ranking .chart {
  min-height: 0;
  height: 100%;
}
.empty {
  flex: 1;
  display: grid;
  place-items: center;
  color: #91abc7;
}
.periods {
  display: flex;
  flex-shrink: 0;
  justify-content: flex-end;
  gap: 5px;
  margin-top: -4px;
  margin-bottom: 4px;
  button {
    border: 1px solid #287b9c;
    padding: 3px 9px;
    background: rgba(8, 49, 73, 0.4);
    color: #91abc7;
    cursor: pointer;
    font-size: 11px;
  }
  button.active {
    color: #45d9ff;
    border-color: #45d9ff;
    background: rgba(45, 210, 255, 0.12);
  }
  button:focus-visible {
    outline: 2px solid #68e3ff;
    outline-offset: 2px;
  }
}
.chart {
  flex: 1;
  min-height: 110px;
  width: 100%;
}
.trend-chart {
  position: relative;
  flex: 1;
  min-height: 0;
}
.trend-empty {
  position: absolute;
  inset: 24px 0 18px;
  display: grid;
  place-items: center;
  color: #91abc7;
  font-size: 12px;
  pointer-events: none;
}
.trend-chart .chart {
  position: absolute;
  inset: 0;
  height: 100%;
  min-height: 0;
}
.trend-unit {
  position: absolute;
  left: 5px;
  top: 2px;
  z-index: 1;
  color: #91abc7;
  font-size: 11px;
  line-height: 14px;
  pointer-events: none;
}
.trend-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-shrink: 0;
  margin-top: -4px;
  margin-bottom: 4px;
}
.trend-legend {
  display: flex;
  align-items: center;
  gap: clamp(4px, 0.6vw, 12px);
  color: #c6e2ff;
  font-size: clamp(10px, 0.85vw, 13px);
  white-space: nowrap;
  button {
    display: flex;
    align-items: center;
    gap: 4px;
    padding: 0;
    border: 0;
    background: transparent;
    color: inherit;
    font: inherit;
    cursor: pointer;
  }
  button.inactive {
    opacity: 0.35;
  }
  button:focus-visible {
    outline: 2px solid #68e3ff;
    outline-offset: 3px;
  }
  i {
    width: clamp(12px, 1vw, 20px);
    height: 8px;
    border-radius: 4px;
  }
}
.trend-toolbar .periods {
  margin: 0;
  gap: 3px;
  button {
    padding: 3px 6px;
  }
}
@media (max-width: 1200px) {
  .mix,
  .pue {
    gap: 7px;
  }
  .pue-info,
  .mix-legend {
    font-size: 10px;
  }
  .kpi {
    padding: 5px;
  }
}
</style>
