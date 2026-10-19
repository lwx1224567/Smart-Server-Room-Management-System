<template>
  <div class="database-topology">
    <div class="topology-side topology-side--left">
      <Border3 name="网络设备概览"><NetworkOverviewCard /></Border3>
      <Border3 name="网络链路统计"
        ><NetworkLinksCard :monitor="monitor" :links="links" :link-statuses="linkStatuses"
      /></Border3>
      <Border3 name="网络层级结构"
        ><NetworkLayersCard
          :monitor="monitor"
          :layers="layers"
          :nodes="nodes"
          v-model:layer="layer"
      /></Border3>
    </div>
    <DrilldownTopology
      :nodes="nodes"
      :links="links"
      :status="nodeStatus"
      :layer="layer"
      :room-id="monitor.snapshot?.roomId"
      :error="monitor.error"
      @node="selectedNode = $event"
      @link="selectedLink = $event"
    />
    <div class="topology-side topology-side--right">
      <Border3 name="端口状态分析"
        ><PortStatusCard :selected-node="selectedNode" :ports="ports"
      /></Border3>
      <Border3 class="link-quality-card" name="链路质量分析"
        ><LinkQualityCard :selected-link="selectedLinkDisplay"
      /></Border3>
      <Border3 class="topology-detail-card" :name="selectedLink ? '链路连接详情' : '设备连接详情'"
        ><ConnectionDetailsCard
          :selected-link="selectedLinkDisplay"
          :selected-node="selectedNodeDisplay"
          @locate="locate"
      /></Border3>
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import Border3 from '@/components/Border3/index.vue';
import {
  NetworkOverviewCard,
  NetworkLinksCard,
  NetworkLayersCard,
  PortStatusCard,
  LinkQualityCard,
  ConnectionDetailsCard,
} from './components';
import DrilldownTopology from './DrilldownTopology.vue';
import { ElMessage } from 'element-plus';
import { useMonitoringStore, type MonitorRecord } from '@/stores/monitoring';
import { usePlatformStore } from '@/stores/platform';
import type { DeviceRef } from '@/types/platform-identity';
import type { TopologyLink } from './topology-layout';
const monitor = useMonitoringStore(),
  platform = usePlatformStore();
const layer = ref(''),
  selectedNode = ref<MonitorRecord>(),
  selectedLink = ref<TopologyLink>();
const emit = defineEmits<{ 'locate-device': [ref: DeviceRef] }>();
const layers = [
  { key: '', name: '全部' },
  { key: 'core', name: '核心层' },
  { key: 'aggregate', name: '汇聚层' },
  { key: 'access', name: '接入层' },
  { key: 'terminal', name: '终端层' },
];
const nodes = computed(() => monitor.snapshot?.nodes ?? []),
  links = computed(() => monitor.snapshot?.links ?? []);
const linkStatuses = [
  { key: 'normal', name: '正常' },
  { key: 'error', name: '异常' },
  { key: 'unknown', name: '状态未知' },
];
const ports = computed(() => {
  if (selectedNode.value) return selectedNode.value.ports;
  if (!nodes.value.length || nodes.value.some((node) => !node.ports)) return undefined;
  return nodes.value.reduce(
    (sum, node) => ({
      total: sum.total + node.ports!.total,
      used: sum.used + node.ports!.used,
      errors: sum.errors + node.ports!.errors,
    }),
    { total: 0, used: 0, errors: 0 },
  );
});
function nodeStatus(node: MonitorRecord) {
  const online = node.deviceId ? monitor.reading('DEVICE', node.deviceId, 'online') : undefined;
  const alarm =
    !!node.deviceId &&
    monitor.snapshot?.alarms.some(
      (alarm) =>
        alarm.status !== '已恢复' &&
        (alarm.deviceId === node.deviceId ||
          (alarm.entityType === 'DEVICE' && alarm.entityId === node.deviceId)),
    );
  const failedLink = links.value.some(
    (link) => link.status === 'error' && (link.from === node.id || link.to === node.id),
  );
  if (
    alarm ||
    failedLink ||
    online?.value === 0 ||
    ['异常', '离线', 'error', 'offline'].includes(node.status ?? '')
  )
    return '异常';
  return online?.value === 1 || ['正常', '在线', 'normal', 'online'].includes(node.status ?? '')
    ? '正常'
    : '未采集';
}
const selectedNodeDisplay = computed(() =>
  selectedNode.value
    ? { ...selectedNode.value, status: nodeStatus(selectedNode.value) }
    : undefined,
);
const selectedLinkDisplay = computed(() => selectedLink.value);
function locate() {
  const id = selectedNode.value?.deviceId;
  if (!id) {
    ElMessage.warning('未关联设备台账');
    return;
  }
  const resolved = platform.identityIndex.resolveDeviceId(id);
  if (resolved.state === 'LINKED') emit('locate-device', resolved.ref);
  else ElMessage.warning('未关联场景');
}
watch(
  () => monitor.snapshot,
  () => {
    if (selectedNode.value)
      selectedNode.value = nodes.value.find((node) => node.id === selectedNode.value?.id);
  },
);
</script>
<style scoped>
.link-quality-card :deep(.border-card__inner) {
  box-sizing: border-box;
  min-height: 0;
}
.link-quality-card :deep(.border-card__content) {
  display: flex;
  padding: 8px 10px;
  overflow: hidden;
}
.link-quality-card :deep(.card-body) {
  flex: 1;
  min-width: 0;
}
.topology-detail-card :deep(.border-card__content) {
  display: flex;
  padding: 8px 10px;
  overflow: visible;
}
.database-topology {
  position: absolute;
  inset: 0;
  color: #c6e2ff;
  font-size: 12px;
}
.topology-side {
  position: absolute;
  top: 5vw;
  bottom: 0;
  width: 25%;
  box-sizing: border-box;
  padding: 0 15px 15px;
  display: grid;
  grid-template-rows: repeat(3, minmax(0, 1fr));
  gap: 15px;
  min-height: 0;
}
.topology-side--left {
  left: 0;
}
.topology-side--right {
  right: 0;
}
.card-body {
  height: 100%;
  min-height: 0;
  box-sizing: border-box;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  overflow: auto;
}
.metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  flex: 1;
  min-height: 0;
}
.metrics > div {
  background: #082b4380;
  border: 1px solid #164b67;
  padding: 8px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 5px;
  color: #91abc7;
}
.metrics b {
  color: #5cddff;
  font-size: 18px;
}
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 7px 0;
  border-bottom: 1px dashed #245276;
  flex: 1;
  min-height: 0;
}
.total {
  font-size: 26px;
  color: #5cddff;
}
.total small {
  font-size: 11px;
}
small {
  color: #91abc7;
  font-size: 10px;
  overflow-wrap: anywhere;
}
button,
input,
select {
  padding: 7px;
  background: #0c3357;
  border: 1px solid #297ba9;
  color: #b4e7ff;
  cursor: pointer;
}
.layers button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: 1;
  min-height: 0;
  border: 0;
  border-bottom: 1px dashed #245276;
  background: transparent;
}
.layers button.active {
  color: #5cddff;
  background: #0d4961;
}
.graph {
  position: absolute;
  top: 5vw;
  bottom: 170px;
  left: 25%;
  right: 25%;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: radial-gradient(ellipse at 50% 45%, #0d355080, transparent 65%);
}
.toolbar {
  display: flex;
  gap: 8px;
  padding: 10px;
  flex-wrap: wrap;
}
.toolbar input {
  flex: 1;
  min-width: 140px;
}
.topology-summary {
  padding: 0 10px 8px;
  color: #65ddff;
}
.topology-summary span {
  margin-left: 10px;
  color: #91abc7;
  font-size: 11px;
}
.topology-legend {
  display: flex;
  gap: 12px;
  padding: 0 10px 8px;
}
.topology-legend i {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 4px;
  border-radius: 2px;
}
.graph-viewport {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.topology-hint {
  padding: 0 10px 8px;
  color: #91abc7;
  font-size: 12px;
}
.graph svg {
  display: block;
  max-width: none;
}
.graph .cabinet-title {
  font-size: 20px;
  fill: #65ddff;
}
.graph .cabinet-count {
  font-size: 14px;
  fill: #91abc7;
}
.graph .cabinet-action {
  font-size: 14px;
  fill: #65ddff;
}
.graph .cabinet-status {
  font-size: 13px;
  fill: #b9cde4;
}
.cabinet-toggle {
  cursor: pointer;
}
.cabinet-group {
  cursor: default;
}
.empty {
  text-align: center;
  margin: auto;
  padding: 15px;
  color: #91abc7;
}
text {
  fill: #d5f2ff;
  font-size: 14px;
  pointer-events: none;
}
g,
path {
  cursor: pointer;
}
</style>
