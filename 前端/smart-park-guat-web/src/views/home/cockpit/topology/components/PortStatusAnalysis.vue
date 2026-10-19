<template>
  <div class="port-analysis">
    <div class="scope">
      <span id="topo-port-title" :title="name">{{ name }}</span
      ><small v-if="demo">演示数据</small>
    </div>
    <div class="overview">
      <div>
        <span class="label">端口使用率</span>
        <div class="rate">{{ rate }}<small v-if="total">%</small></div>
      </div>
      <span class="summary">{{
        data ? `已使用 ${data.used} / 总计 ${total}` : '暂无该设备端口数据'
      }}</span>
    </div>
    <div
      class="distribution"
      :aria-label="
        data ? `已使用 ${data.used}，空闲 ${data.free}，未知 ${data.unknown}` : '暂无端口数据'
      "
    >
      <i
        v-for="item in states"
        :key="item.key"
        :class="item.key"
        :style="{ flexGrow: total ? item.count / total : 0 }"
      />
    </div>
    <div class="statistics">
      <div v-for="item in states" :key="item.key">
        <span><i :class="item.key" />{{ item.label }}</span
        ><b :id="item.id" :class="item.key">{{ data ? item.count : '--' }}</b>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
const props = defineProps<{
  name: string;
  data: { used: number; free: number; unknown: number } | null;
  demo?: boolean;
}>();
const total = computed(() =>
  props.data ? props.data.used + props.data.free + props.data.unknown : 0,
);
const rate = computed(() =>
  total.value ? ((props.data!.used / total.value) * 100).toFixed(1) : '--',
);
const states = computed(() => [
  { key: 'used', label: '已使用', id: 'tp-used', count: props.data?.used ?? 0 },
  { key: 'free', label: '空闲', id: 'tp-free', count: props.data?.free ?? 0 },
  { key: 'unknown', label: '未知', id: 'tp-unknown', count: props.data?.unknown ?? 0 },
]);
</script>

<style scoped>
.port-analysis {
  --used: #55c8a5;
  --free: #559fd7;
  --unknown: #657e9e;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: clamp(6px, 0.7vh, 10px);
  width: 100%;
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
  flex-shrink: 0;
  color: #738da9;
  font-size: 10px;
}
.overview {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.label {
  font-size: 11px;
  color: #95acc7;
}
.rate {
  font-size: clamp(25px, 2vw, 36px);
  font-weight: 600;
  color: #65d3eb;
  line-height: 1.15;
  font-variant-numeric: tabular-nums;
  margin-top: 3px;
}
.rate small {
  font-size: 13px;
  margin-left: 3px;
  font-weight: 400;
}
.summary {
  font-size: 11px;
  color: #8ea6c2;
  text-align: right;
  line-height: 1.5;
}
.distribution {
  display: flex;
  gap: 3px;
  height: 9px;
  flex-shrink: 0;
  border-radius: 5px;
  overflow: hidden;
  background: rgba(105, 140, 180, 0.1);
}
.distribution i {
  flex-basis: 0;
  min-width: 0;
  border-radius: 3px;
  transition: flex-grow 0.3s ease;
}
.distribution .used,
.statistics i.used {
  background: var(--used);
}
.distribution .free,
.statistics i.free {
  background: var(--free);
}
.distribution .unknown,
.statistics i.unknown {
  background: var(--unknown);
}
.statistics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}
.statistics > div {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.statistics span {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: #96adc8;
}
.statistics i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  flex-shrink: 0;
}
.statistics b {
  font-size: clamp(18px, 1.35vw, 24px);
  font-weight: 500;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}
.statistics b.used {
  color: var(--used);
}
.statistics b.free {
  color: #72b4e5;
}
.statistics b.unknown {
  color: #99acc4;
}
@media (prefers-reduced-motion: reduce) {
  .distribution i {
    transition: none;
  }
}
</style>
