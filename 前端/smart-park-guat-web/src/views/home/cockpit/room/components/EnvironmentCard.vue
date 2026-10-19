<template>
  <div class="environment-monitor">
    <div
      v-for="(item, index) in metrics"
      :key="item.name"
      class="metric-row"
      :style="{ '--color': item.color }"
    >
      <div class="metric-icon" aria-hidden="true">
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.6"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path :d="item.icon" />
        </svg>
      </div>
      <div class="reading">
        <div class="name">{{ item.name }}</div>
        <div class="value">
          {{ item.value }}<span>{{ item.unit }}</span>
        </div>
      </div>
      <div
        :ref="(el) => setElement(el, index)"
        class="trend"
        role="img"
        :aria-label="`${item.name}趋势：${item.points.join('、')}${item.suffix}`"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, watch, onMounted, onBeforeUnmount } from 'vue';
import type { ComponentPublicInstance } from 'vue';
import { init, use, graphic } from 'echarts/core';
import type { ECharts } from 'echarts/core';
import { LineChart } from 'echarts/charts';
import { GridComponent, TooltipComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import { useMonitoringStore } from '@/stores/monitoring';
use([LineChart, GridComponent, TooltipComponent, CanvasRenderer]);
const definitions = [
  {
    name: '机房温度',
    unit: '℃',
    suffix: '℃',
    color: '#ffc968',
    icon: 'M9 14V5a3 3 0 0 1 6 0v9a5 5 0 1 1-6 0ZM12 8v10m5-12h3m-3 4h2',
  },
  {
    name: '相对湿度',
    unit: '%',
    suffix: '%',
    color: '#48d9ef',
    icon: 'M12 2S5 10 5 15a7 7 0 0 0 14 0c0-5-7-13-7-13ZM8 15a4 4 0 0 0 4 4',
  },
  {
    name: '空气质量',
    unit: 'AQI 28',
    suffix: ' AQI',
    color: '#64e7b5',
    icon: 'M3 8h12a3 3 0 1 0-3-3M3 12h16a3 3 0 1 1-3 3M3 16h6a3 3 0 1 1-3 3',
  },
  {
    name: '烟雾感知',
    unit: '无烟雾',
    suffix: '%（烟雾检测值）',
    color: '#99adff',
    icon: 'M5 14a4 4 0 0 1 1-7 6 6 0 0 1 11 0 4 4 0 0 1 1 7H5ZM8 18v3m4-3v3m4-3v3',
  },
  {
    name: '水浸监测',
    unit: '无水浸',
    suffix: '（0=干燥，1=水浸）',
    color: '#52baff',
    icon: 'M12 2s-5 6-5 10a5 5 0 0 0 10 0c0-4-5-10-5-10ZM3 20q3-3 6 0t6 0 6 0M10 12a2 2 0 0 0 2 2',
  },
];
const monitor = useMonitoringStore();
const metricKeys = ['temperature', 'humidity', 'aqi', 'smoke', 'water'];
const metrics = computed(() =>
  definitions.map((definition, index) => {
    const history = (monitor.snapshot?.readings ?? [])
      .filter(
        (r) =>
          r.entityType === 'ROOM' &&
          r.entityId === monitor.snapshot?.roomId &&
          r.metric === metricKeys[index] &&
          r.recordOrigin === 'REAL' &&
          r.quality === 'GOOD',
      )
      .sort((a, b) => Date.parse(a.observedAt) - Date.parse(b.observedAt))
      .slice(-10);
    const latest = history[history.length - 1];
    const value = !latest
      ? '未采集'
      : index === 2
        ? latest.value <= 50
          ? '优'
          : latest.value <= 100
            ? '良'
            : '较差'
        : index >= 3
          ? latest.value === 0
            ? '正常'
            : '异常'
          : latest.value.toFixed(1);
    const unit = !latest
      ? ''
      : index === 2
        ? `AQI ${latest.value}`
        : index === 3
          ? latest.value === 0
            ? '无烟雾'
            : '检测到烟雾'
          : index === 4
            ? latest.value === 0
              ? '无水浸'
              : '检测到水浸'
            : latest.unit;
    return {
      ...definition,
      value,
      unit,
      points: history.map((r) => r.value),
      labels: history.map((r) =>
        new Date(r.observedAt).toLocaleTimeString('zh-CN', { hour12: false }),
      ),
    };
  }),
);
const elements: HTMLElement[] = [];
const charts: ECharts[] = [];
function setElement(el: Element | ComponentPublicInstance | null, index: number) {
  if (el instanceof HTMLElement) elements[index] = el;
}
let observer: ResizeObserver | undefined;
function renderCharts() {
  metrics.value.forEach((item, index) => {
    const chart = charts[index];
    if (!chart) return;
    chart.setOption(
      {
        animationDuration: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 1400,
        grid: { left: 4, right: 7, top: 7, bottom: 5 },
        tooltip: {
          trigger: 'axis',
          confine: true,
          backgroundColor: '#102441',
          borderColor: item.color,
          textStyle: { color: '#e3efff', fontSize: 11 },
          formatter: (params: unknown) => {
            const point = (params as { dataIndex: number; value: number }[])[0]!;
            return `${item.name} · ${item.labels[point.dataIndex]}<br/>${point.value}${item.suffix}`;
          },
        },
        xAxis: { type: 'category', show: false, boundaryGap: false, data: item.labels },
        yAxis: {
          type: 'value',
          show: false,
          scale: true,
          ...(item.points.every((v) => v === item.points[0])
            ? {
                min: (item.points[0] ?? 0) === 0 ? 0 : item.points[0]! - 1,
                max: (item.points[0] ?? 0) === 0 ? 1 : item.points[0]! + 1,
              }
            : {}),
        },
        series: [
          {
            type: 'line',
            data: item.points,
            smooth: 0.35,
            showSymbol: item.points.length === 1,
            lineStyle: { width: 2, color: item.color },
            areaStyle: {
              color: new graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: `${item.color}55` },
                { offset: 1, color: `${item.color}00` },
              ]),
            },
            markPoint: {
              symbol: 'circle',
              symbolSize: 5,
              silent: true,
              itemStyle: { color: item.color },
              data: item.points.length
                ? [{ coord: [item.points.length - 1, item.points[item.points.length - 1]] }]
                : [],
            },
          },
        ],
      },
      { notMerge: true },
    );
  });
}
watch(metrics, renderCharts, { deep: true, flush: 'post' });
onMounted(() => {
  elements.forEach((element) => charts.push(init(element, undefined, { renderer: 'canvas' })));
  renderCharts();
  observer = new ResizeObserver(() => charts.forEach((chart) => chart.resize()));
  elements.forEach((el) => observer!.observe(el));
});
onBeforeUnmount(() => {
  observer?.disconnect();
  charts.forEach((chart) => chart.dispose());
});
</script>

<style scoped lang="scss">
.environment-monitor {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}
.metric-row {
  display: grid;
  grid-template-columns: 26px minmax(76px, 1fr) minmax(0, 1.2fr);
  align-items: center;
  gap: 8px;
  flex: 1 1 0;
  min-height: 0;
  border-bottom: 1px solid rgba(116, 174, 224, 0.09);
}
.metric-icon {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  color: var(--color);
  border: 1px solid color-mix(in srgb, var(--color) 35%, transparent);
  border-radius: 8px;
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--color) 18%, transparent),
    rgba(7, 26, 52, 0.6)
  );
  box-shadow: inset 0 0 9px color-mix(in srgb, var(--color) 12%, transparent);
  svg {
    width: 21px;
    height: 21px;
  }
}
.metric-icon {
  box-sizing: border-box;
  width: 26px;
  height: 26px;
  border-radius: 6px;
  svg {
    width: 18px;
    height: 18px;
  }
}
.name {
  color: #aac0d8;
  font-size: 10px;
  line-height: 1.2;
  letter-spacing: 0.5px;
}
.value {
  color: var(--color);
  font-size: 17px;
  font-weight: 600;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  span {
    margin-left: 4px;
    font-size: 9px;
    font-weight: 400;
    color: #a5bbd2;
  }
}
.trend {
  width: 100%;
  height: 100%;
  max-height: 30px;
  min-height: 0;
  min-width: 0;
}
</style>
