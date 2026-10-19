<template>
  <div class="energy-trend">
    <div class="energy-summary">
      <div>
        <div class="energy-total">
          {{ total?.toLocaleString('en-US', { maximumFractionDigits: 1 }) ?? '--' }}<span>kWh</span>
        </div>
        <div v-if="monitor.week?.simulated" class="energy-label">含模拟数据</div>
      </div>
      <div class="energy-comparison">
        <div class="energy-change">
          <svg
            aria-hidden="true"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M12 4v16m-6-6 6 6 6-6" />
          </svg>
          {{ change === null ? '--' : change.toFixed(1) + '%' }}
        </div>
        <div class="energy-label">较前七天</div>
      </div>
    </div>
    <div
      ref="chartElement"
      class="energy-chart"
      role="img"
      :aria-label="`近七天每日能耗：${daily.map((item) => `${item.date} ${item.value} kWh`).join('，')}`"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, watch, onBeforeUnmount, onMounted, ref } from 'vue';
import { graphic, init, use } from 'echarts/core';
import type { ECharts } from 'echarts/core';
import { BarChart } from 'echarts/charts';
import { GridComponent, TooltipComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';

import { useMonitoringStore } from '@/stores/monitoring';
use([BarChart, GridComponent, TooltipComponent, CanvasRenderer]);

const monitor = useMonitoringStore();
const daily = computed(() =>
  (monitor.week?.labels ?? []).map((label, i) => ({
    date: label.slice(5).replace('-', '/'),
    value: monitor.week?.totals[i] ?? null,
  })),
);
const total = computed(() => monitor.week?.total ?? null);
const change = computed(() =>
  total.value !== null && monitor.previousWeekTotal != null && monitor.previousWeekTotal > 0
    ? ((total.value - monitor.previousWeekTotal) / monitor.previousWeekTotal) * 100
    : null,
);
const axisMax = computed(() =>
  Math.max(1, Math.ceil(Math.max(...daily.value.map((item) => item.value ?? 0), 1) / 500) * 500),
);
const chartElement = ref<HTMLElement>();
let chart: ECharts | undefined;
let observer: ResizeObserver | undefined;

function renderChart() {
  chart?.setOption({
    animationDuration: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 1400,
    animationEasing: 'cubicOut',
    grid: { left: 42, right: 8, top: 12, bottom: 24 },
    tooltip: {
      trigger: 'axis',
      confine: true,
      axisPointer: { type: 'shadow' },
      backgroundColor: '#102441',
      borderColor: '#FFDB69',
      textStyle: { color: '#e3efff', fontSize: 12 },
      valueFormatter: (value: number) => `${value.toLocaleString('en-US')} kWh`,
    },
    xAxis: {
      type: 'category',
      data: daily.value.map((item) => item.date),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: 'rgba(116, 174, 224, 0.2)' } },
      axisLabel: { color: '#91abc7', fontSize: 10, hideOverlap: true },
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: axisMax.value,
      splitNumber: 3,
      axisLabel: { color: '#91abc7', fontSize: 10, formatter: (value: number) => `${value}` },
      splitLine: { lineStyle: { type: 'dashed', color: 'rgba(116, 174, 224, 0.16)' } },
    },
    series: [
      {
        name: '能耗',
        type: 'bar',
        barWidth: 9,
        data: daily.value.map((item) => item.value),
        itemStyle: {
          color: new graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#FFDB69' },
            { offset: 1, color: '#FF9E24' },
          ]),
        },
      },
    ],
  });
}
watch(daily, renderChart, { deep: true, flush: 'post' });
onMounted(() => {
  chart = init(chartElement.value!, undefined, { renderer: 'canvas' });
  renderChart();
  observer = new ResizeObserver(() => chart?.resize());
  observer.observe(chartElement.value!);
});

onBeforeUnmount(() => {
  observer?.disconnect();
  chart?.dispose();
});
</script>

<style scoped lang="scss">
.energy-trend {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}
.energy-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-shrink: 0;
  padding: 2px 4px 0;
}
.energy-label {
  color: #91abc7;
  font-size: 11px;
  line-height: 1.5;
}
.energy-total {
  color: #ffdb69;
  font-size: clamp(20px, 1.6vw, 30px);
  font-weight: 600;
  line-height: 1.4;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  span {
    margin-left: 6px;
    color: #aac0d8;
    font-size: 11px;
    font-weight: 400;
  }
}
.energy-comparison {
  text-align: right;
}
.energy-change {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 3px;
  color: #64e7b5;
  font-size: 18px;
  font-weight: 600;
  line-height: 1.5;
  svg {
    width: 16px;
    height: 16px;
  }
}
.energy-chart {
  flex: 1;
  width: 100%;
  min-height: 0;
}
</style>
