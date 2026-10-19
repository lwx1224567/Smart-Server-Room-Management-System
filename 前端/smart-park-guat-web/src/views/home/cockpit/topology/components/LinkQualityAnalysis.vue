<template>
  <div class="quality-analysis">
    <div class="scope">
      <span :title="name">{{ name }}</span
      ><small>{{
        data?.observedAt ? new Date(data.observedAt).toLocaleTimeString('zh-CN') : '未采集'
      }}</small>
    </div>
    <div class="metrics">
      <div>
        <span>带宽</span>
        <div class="metric-value">
          <b id="tq-band">{{ data?.bandwidth ?? '--' }}</b
          ><small>Mbps</small>
        </div>
      </div>
      <div>
        <span>网络延迟</span>
        <div class="metric-value">
          <b id="tq-latency">{{ data?.latency ?? '--' }}</b
          ><small>ms</small>
        </div>
      </div>
      <div>
        <span>丢包率</span>
        <div class="metric-value">
          <b id="tq-loss">{{ data?.loss ?? '--' }}</b
          ><small>%</small>
        </div>
      </div>
    </div>
    <div class="trend">
      <div v-if="history.length" class="history-chart">
        <div class="legend">
          <span v-for="metric in metrics" :key="metric.key" :style="{ color: metric.color }">{{
            metric.label
          }}</span
          ><small v-if="data?.developmentSimulated">开发演示</small>
        </div>
        <svg
          viewBox="0 0 300 90"
          preserveAspectRatio="none"
          role="img"
          aria-label="链路质量历史趋势，各指标独立归一化"
        >
          <polyline
            v-for="metric in metrics"
            :key="metric.key"
            :points="line(metric.key)"
            fill="none"
            :stroke="metric.color"
            :stroke-width="metric.key === 'bandwidth' ? 3 : 2"
            :stroke-dasharray="metric.key === 'loss' ? '6 5' : undefined"
            vector-effect="non-scaling-stroke"
          />
        </svg>
        <small>趋势对比（各指标独立刻度，丢包率为虚线）</small>
      </div>
      <div v-if="!history.length" class="empty">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <rect x="3" y="4" width="18" height="13" rx="2" />
          <path d="M8 21h8M12 17v4M7 10h10" /></svg
        ><span>暂无链路历史数据</span><small>暂无链路历史记录</small>
      </div>
      <div class="axis">
        <span>时间</span><span v-for="at in ticks" :key="at">{{ at }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { LinkQualityPoint } from '@/stores/monitoring';
const props = defineProps<{
  name: string;
  data?: {
    bandwidth?: number;
    latency?: number;
    loss?: number;
    observedAt?: string;
    qualityHistory?: LinkQualityPoint[];
    developmentSimulated?: boolean;
  };
}>();
const history = computed(() =>
  (props.data?.qualityHistory ?? [])
    .filter(
      (p) =>
        Number.isFinite(Date.parse(p.observedAt)) &&
        [p.bandwidth, p.latency, p.loss].every(Number.isFinite),
    )
    .slice()
    .sort((a, b) => a.observedAt.localeCompare(b.observedAt)),
);
const metrics = [
  { key: 'bandwidth' as const, label: '带宽', color: '#51cfff' },
  { key: 'latency' as const, label: '延迟', color: '#f6be63' },
  { key: 'loss' as const, label: '丢包率', color: '#f27991' },
];
const ticks = computed(() =>
  history.value.length
    ? [0, Math.floor((history.value.length - 1) / 2), history.value.length - 1].map((i) =>
        new Date(history.value[i].observedAt).toLocaleTimeString('zh-CN', {
          hour: '2-digit',
          minute: '2-digit',
        }),
      )
    : ['--', '--', '--'],
);
function line(key: 'bandwidth' | 'latency' | 'loss') {
  const values = history.value.map((p) => p[key]),
    min = Math.min(...values),
    max = Math.max(...values);
  return values
    .map(
      (value, i) =>
        `${(i / Math.max(1, values.length - 1)) * 300},${max === min ? 45 : 80 - ((value - min) / (max - min)) * 70}`,
    )
    .join(' ');
}
</script>

<style scoped>
.history-chart {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  gap: 4px;
}
.history-chart svg {
  width: 100%;
  flex: 1;
  min-height: 0;
  height: 0;
}
.legend {
  display: flex;
  gap: 12px;
  font-size: 10px;
}
.legend small {
  margin-left: auto;
  color: #91abc7;
}
.history-chart > small {
  font-size: 9px;
  color: #728ca9;
}
.quality-analysis {
  display: flex;
  flex-direction: column;
  gap: clamp(6px, 0.7vh, 10px);
  width: 100%;
  height: 100%;
  flex: 1;
  min-width: 0;
  min-height: 0;
  color: #b9cde4;
}
.scope {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 11px;
  color: #9cb5d1;
  min-width: 0;
}
.scope > span {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.scope small {
  font-size: 10px;
  color: #8096b1;
  flex-shrink: 0;
}
.metrics {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) repeat(2, minmax(0, 1fr));
  column-gap: clamp(8px, 1.2vw, 24px);
  flex-shrink: 0;
}
.metrics > div {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.metrics span {
  font-size: 11px;
  color: #95acc7;
  white-space: nowrap;
}
.metrics b {
  font-size: clamp(20px, 1.5vw, 27px);
  font-weight: 500;
  color: #b7ccdf;
  line-height: 1.2;
}
.metric-value {
  display: flex;
  align-items: baseline;
  gap: 4px;
  white-space: nowrap;
}
.metrics small {
  font-size: 10px;
  color: #7088a4;
  line-height: 1;
}
.trend {
  position: relative;
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  background: repeating-linear-gradient(
    to bottom,
    transparent 0,
    transparent calc(33.333% - 1px),
    rgba(120, 157, 198, 0.07) calc(33.333% - 1px),
    rgba(120, 157, 198, 0.07) 33.333%
  );
}
.empty {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  padding: 4px 0;
  color: #94acc7;
  font-size: 11px;
}
.empty svg {
  width: 20px;
  height: 20px;
  fill: none;
  stroke: #5e7e9f;
  stroke-width: 1.25;
  flex-shrink: 0;
}
.empty small {
  font-size: 10px;
  color: #728ca9;
  text-align: center;
}
.axis {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  color: #a4bdd7;
  padding-top: 3px;
  flex-shrink: 0;
}
@media (max-height: 800px) {
  .empty svg {
    display: none;
  }
  .quality-analysis {
    gap: 6px;
  }
  .metrics b {
    font-size: 20px;
  }
}
</style>
