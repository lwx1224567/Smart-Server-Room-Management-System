<template>
  <div class="device-status">
    <div class="status-main">
      <div class="status-visual">
        <div ref="chartElement" class="status-chart" aria-hidden="true" />
        <div class="status-center">
          <strong :style="{ color: statusColor }">{{ statusLabel }}</strong
          ><span>设备状态</span>
        </div>
      </div>
      <dl class="status-metrics">
        <div v-for="item in metrics" :key="item.label" class="status-metric">
          <dt>
            <span class="metric-icon" aria-hidden="true"><component :is="item.icon" /></span
            >{{ item.label }}
          </dt>
          <dd><i aria-hidden="true" />{{ item.value }}</dd>
        </div>
      </dl>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch, computed } from 'vue';
import { SwitchButton, Lightning, Connection } from '@element-plus/icons-vue';
import { init, use, type ECharts } from 'echarts/core';
import { GaugeChart } from 'echarts/charts';
import { CanvasRenderer } from 'echarts/renderers';
import { cabinetControls, deviceModelAlarms } from '../../../scene/cabinetState';
import { topologyNodes } from '../../../demo/topologyData';
import { mockData } from '../../../demo/mockData.js';
import { useMonitoringStore } from '@/stores/monitoring';
import { usePlatformStore } from '@/stores/platform';
const monitoring = useMonitoringStore();
const platformStore = usePlatformStore();
const actualStatus = computed(() =>
  monitoring.cabinetStatus(
    props.deviceId,
    (platformStore.scene?.devices ?? [])
      .filter((d) => d.cabinetId === props.deviceId)
      .map((d) => d.id),
  ),
);
function realState(metric: string) {
  const r = monitoring.reading('CABINET', props.deviceId, metric);
  return r ? (r.value === 1 ? '正常' : '异常') : '未采集';
}

use([GaugeChart, CanvasRenderer]);
const props = defineProps<{ deviceId: string; platform?: boolean }>();
const powered = computed(() => cabinetControls[props.deviceId]?.power !== false);
const hasAlarm = computed(
  () =>
    Boolean(mockData.devices.find((device) => device.id === props.deviceId)?.alarm) ||
    topologyNodes.some(
      (device) => device.cabinetId === props.deviceId && deviceModelAlarms[device.id],
    ),
);
const statusLabel = computed(() =>
  props.platform
    ? actualStatus.value
    : !powered.value
      ? '断电（演示）'
      : hasAlarm.value
        ? '告警（演示）'
        : '正常（演示）',
);
const statusColor = computed(() =>
  props.platform
    ? actualStatus.value.includes('告警')
      ? '#ff6868'
      : actualStatus.value === '在线'
        ? '#31dfc1'
        : '#8298af'
    : !powered.value
      ? '#8298af'
      : hasAlarm.value
        ? '#ff6868'
        : '#31dfc1',
);
const metrics = computed(() =>
  props.platform
    ? [
        { label: '运行状态', value: actualStatus.value, icon: SwitchButton },
        { label: '供电状态', value: realState('powerState'), icon: Lightning },
        { label: '网络状态', value: realState('networkState'), icon: Connection },
      ]
    : [
        { label: '运行状态', value: powered.value ? '在线' : '停止', icon: SwitchButton },
        { label: '供电状态', value: powered.value ? '正常' : '断电', icon: Lightning },
        { label: '网络状态', value: powered.value ? '正常' : '未采集', icon: Connection },
      ],
);
const chartElement = ref<HTMLElement>();
let chart: ECharts | undefined;
let observer: ResizeObserver | undefined;
function updateChart() {
  if (!chart) return;
  chart.resize();
  chart.setOption({
    animationDuration: 700,
    animationDurationUpdate: 450,
    series: [
      {
        type: 'gauge',
        center: ['50%', '50%'],
        radius: '84%',
        startAngle: 90,
        endAngle: -270,
        silent: true,
        axisLine: { lineStyle: { width: 7, color: [[1, 'rgba(76, 157, 188, 0.18)']] } },
        progress: {
          show: true,
          roundCap: true,
          width: 7,
          itemStyle: {
            color: statusColor.value,
            shadowColor: 'rgba(49, 223, 193, 0.4)',
            shadowBlur: 9,
          },
        },
        pointer: { show: false },
        axisTick: { show: false },
        splitLine: { show: false },
        axisLabel: { show: false },
        title: { show: false },
        detail: { show: false },
        // 固定弧长仅作为状态装饰，不表示健康分数或百分比。
        data: [{ value: 88 }],
      },
    ],
  });
}
watch(() => [props.deviceId, statusColor.value], updateChart, { flush: 'post' });
onMounted(() => {
  chart = init(chartElement.value!);
  observer = new ResizeObserver(updateChart);
  observer.observe(chartElement.value!);
  updateChart();
});
onBeforeUnmount(() => {
  observer?.disconnect();
  chart?.dispose();
});
</script>

<style scoped>
.device-status {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  container-type: size;
  color: #dff7ff;
}
.status-main {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 44% minmax(0, 1fr);
  align-items: center;
  gap: 5%;
  padding: 2cqh 0 5cqh;
}
.status-visual {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 0;
}
.status-chart {
  position: absolute;
  inset: 0;
}
.status-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  pointer-events: none;
}
.status-center strong {
  color: #31dfc1;
  font-size: clamp(18px, 7cqw, 30px);
  font-weight: 600;
  text-shadow: 0 0 16px rgba(49, 223, 193, 0.25);
}
.status-center span {
  color: #9bbdd1;
  font-size: clamp(10px, 3cqw, 13px);
  letter-spacing: 1px;
}
.status-metrics {
  display: grid;
  grid-template-rows: repeat(3, minmax(0, 1fr));
  gap: 5cqh;
  margin: 0;
  width: 100%;
}
.status-metric {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 4cqh 3cqw;
  background: linear-gradient(90deg, rgba(32, 130, 172, 0.15), rgba(32, 130, 172, 0.04));
  border: 1px solid rgba(101, 220, 255, 0.1);
  border-radius: 5px;
}
.status-metric dt {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #abcddd;
  font-size: clamp(11px, 3.2cqw, 14px);
  white-space: nowrap;
}
.metric-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #65dcff;
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  filter: drop-shadow(0 0 3px rgba(101, 220, 255, 0.2));
}
.metric-icon svg {
  display: block;
  width: 100%;
  height: 100%;
}
.status-metric dd {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  color: #55e4cc;
  font-weight: 600;
  font-size: clamp(13px, 3.8cqw, 17px);
  white-space: nowrap;
}
.status-metric dd i {
  width: 5px;
  height: 5px;
  background: currentColor;
  border-radius: 50%;
  box-shadow: 0 0 6px rgba(49, 223, 193, 0.35);
}
</style>
