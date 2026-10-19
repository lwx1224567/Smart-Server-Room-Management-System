<template>
  <div class="twin-body capacity-ranking">
    <template v-if="ranking.length">
      <div v-for="(cabinet, index) in ranking" :key="cabinet.id" class="ranking-row">
        <div class="ranking-label">
          <span class="ranking-number">{{ index + 1 }}</span>
          <span class="ranking-code" :title="cabinet.name">{{ cabinet.code }}</span>
          <span class="ranking-usage">{{ cabinet.used }} / {{ cabinet.total }} U</span>
          <b>{{ cabinet.percent }}%</b>
        </div>
        <div class="status-track"><i :style="{ width: `${cabinet.percent}%` }"></i></div>
      </div>
      <small v-if="unregistered" class="tiny-note"
        >{{ unregistered }} 台机柜U位登记不完整，未参与排行</small
      >
    </template>
    <div v-else class="ranking-empty" role="status">
      {{
        error ||
        (loading ? '正在查询机柜容量…' : unregistered ? '机柜U位尚未完整登记' : '暂无机柜容量数据')
      }}
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps(['snapshot', 'loading', 'error']);
const capacities = computed(() =>
  (props.snapshot?.cabinets ?? []).map((cabinet) => {
    const devices = props.snapshot.devices.filter((device) => device.cabinetId === cabinet.id);
    if (
      !devices.length ||
      !(cabinet.capacityU > 0) ||
      devices.some((device) => device.uStart == null || device.uHeight == null)
    )
      return null;
    const used = devices.reduce((sum, device) => sum + device.uHeight, 0);
    const utilization = used / cabinet.capacityU;
    return {
      id: cabinet.id,
      code: cabinet.code,
      name: cabinet.name,
      used,
      total: cabinet.capacityU,
      utilization,
      percent: (utilization * 100).toFixed(1),
    };
  }),
);
const unregistered = computed(() => capacities.value.filter((cabinet) => !cabinet).length);
const ranking = computed(() =>
  capacities.value
    .filter(Boolean)
    .sort((a, b) => b.utilization - a.utilization || a.code.localeCompare(b.code))
    .slice(0, 5),
);
</script>

<style scoped>
.capacity-ranking {
  gap: 8px;
}
.ranking-row {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  justify-content: center;
  gap: 5px;
}
.ranking-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}
.ranking-number {
  color: #65ddff;
  width: 14px;
  flex-shrink: 0;
}
.ranking-code {
  color: #b9e6f3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ranking-usage {
  margin-left: auto;
  color: #85b6ca;
  white-space: nowrap;
  font-size: 11px;
}
.ranking-label b {
  color: #5bdeff;
  min-width: 48px;
  text-align: right;
}
.status-track {
  height: 6px;
  flex-shrink: 0;
}
.status-track i {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #168ab8, #42d9ff);
}
.ranking-empty {
  margin: auto;
  color: #85b6ca;
  font-size: 12px;
}
</style>
