<template>
  <div class="network-overview">
    <div class="overview-summary">
      <div>
        <span class="summary-label">网络设备总数</span>
        <div class="summary-value">
          <strong>{{ total }}</strong
          ><span>个节点</span>
        </div>
      </div>
      <svg
        class="summary-icon"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="1.6"
        aria-hidden="true"
      >
        <path :d="networkIcon" />
      </svg>
    </div>
    <div class="overview-types">
      <div v-for="item in categories" :key="item.name" class="overview-type">
        <div class="type-label">
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.6"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <path :d="item.icon" /></svg
          ><span>{{ item.name }}</span>
        </div>
        <div class="type-value">
          <strong>{{ item.count }}</strong
          ><span>{{ item.unit }}</span>
        </div>
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import { useMonitoringStore } from '@/stores/monitoring';
import { usePlatformStore } from '@/stores/platform';
const monitor = useMonitoringStore(),
  platform = usePlatformStore();
const networkIcon =
  'M12 8v4M5 16v-4h14v4M12 12v4M9 5a3 3 0 1 0 6 0a3 3 0 1 0-6 0M2 19a3 3 0 1 0 6 0a3 3 0 1 0-6 0M9 19a3 3 0 1 0 6 0a3 3 0 1 0-6 0M16 19a3 3 0 1 0 6 0a3 3 0 1 0-6 0';
const definitions = [
  { name: '交换机', unit: '台', icon: networkIcon },
  {
    name: '路由器',
    unit: '台',
    icon: 'M3 14h18v7H3zM7 17v1m4-1v1m5-1v1M12 14V9M8 9a6 6 0 0 1 8 0M5 6a10 10 0 0 1 14 0',
  },
  { name: '防火墙', unit: '台', icon: 'M12 2l8 3v6c0 5-4 9-8 11c-4-2-8-6-8-11V5zM9 12l2 2l4-5' },
  { name: '终端节点', unit: '个', icon: 'M3 3h18v13H3zM12 16v5M8 21h8' },
];
const categories = computed(() =>
  definitions.map((item, index) => ({
    ...item,
    count: monitor.snapshot
      ? monitor.snapshot.nodes.filter((node) => {
          const type =
            node.type ?? platform.scene?.devices.find((d) => d.id === node.deviceId)?.type;
          return index === 0
            ? type === 'SWITCH'
            : index === 1
              ? type === 'ROUTER'
              : index === 2
                ? type === 'FIREWALL'
                : !['SWITCH', 'ROUTER', 'FIREWALL'].includes(type ?? '');
        }).length
      : '--',
  })),
);
const total = computed(() => monitor.snapshot?.nodes.length ?? '--');
</script>
<style scoped>
.network-overview {
  --overview-accent: #43d9fa;
  --overview-border: rgba(80, 190, 231, 0.22);
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  gap: 6px;
  color: #cce8ff;
}
.overview-summary {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-sizing: border-box;
  flex: 0 0 52px;
  min-height: 0;
  min-width: 0;
  padding: 4px 11px;
  border: 1px solid rgba(67, 217, 250, 0.3);
  border-radius: 5px;
  background: linear-gradient(110deg, rgba(10, 65, 101, 0.58), rgba(5, 30, 61, 0.32));
  box-shadow: inset 0 0 12px rgba(33, 149, 205, 0.045);
}
.overview-summary::before {
  content: '';
  position: absolute;
  left: -1px;
  top: 13px;
  width: 2px;
  height: 24px;
  background: var(--overview-accent);
  box-shadow: 0 0 5px rgba(67, 217, 250, 0.25);
}
.summary-label {
  display: block;
  color: #a6c7dc;
  font-size: clamp(11px, 0.9vw, 14px);
  line-height: 1.2;
}
.summary-value,
.type-value {
  display: flex;
  align-items: baseline;
  gap: 5px;
}
.summary-value strong {
  color: var(--overview-accent);
  font-size: clamp(26px, 2.05vw, 32px);
  line-height: 1;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.summary-value span,
.type-value span {
  color: #8eafc8;
  font-size: 10px;
  line-height: 1;
}
.summary-icon {
  width: 29px;
  height: 29px;
  flex-shrink: 0;
  color: #8bdff4;
  filter: drop-shadow(0 0 4px rgba(67, 217, 250, 0.18));
}
.overview-types {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-template-rows: repeat(2, minmax(0, 1fr));
  flex: 1;
  min-height: 0;
  gap: 8px 8px;
}
.overview-type {
  position: relative;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  box-sizing: border-box;
  min-width: 0;
  min-height: 0;
  gap: 5px;
  padding: 8px 7px;
  border: 1px solid var(--overview-border);
  border-radius: 5px;
  background: linear-gradient(120deg, rgba(9, 47, 77, 0.56), rgba(5, 29, 56, 0.36));
  transition: border-color 0.18s ease;
}
.overview-type::before {
  content: '';
  position: absolute;
  left: 7px;
  top: -1px;
  width: 12px;
  height: 1px;
  background: rgba(67, 217, 250, 0.55);
}
.overview-type:hover {
  border-color: rgba(80, 190, 231, 0.48);
}
.type-label {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #adc8dc;
  font-size: clamp(11px, 0.85vw, 14px);
  line-height: 1.15;
  white-space: nowrap;
}
.type-label svg {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
  color: var(--overview-accent);
}
.type-value {
  padding-left: 0;
  flex-shrink: 0;
  gap: 3px;
  line-height: 1;
  white-space: nowrap;
}
.type-value strong {
  font-size: clamp(20px, 1.55vw, 26px);
  line-height: 1;
  color: #f0f8ff;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
@media (prefers-reduced-motion: reduce) {
  .overview-type {
    transition: none;
  }
}
</style>
