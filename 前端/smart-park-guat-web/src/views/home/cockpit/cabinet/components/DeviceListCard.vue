<template>
  <div ref="list" class="cabinet-devices">
    <p
      v-if="targetDeviceId && !devices.some((device) => device.id === targetDeviceId)"
      class="empty"
      role="status"
    >
      该设备暂无柜内明细
    </p>
    <p v-else-if="!devices.length" class="empty">
      {{ platformDevices ? '暂无已登记设备' : '暂无设备明细' }}
    </p>
    <button
      v-for="device in devices"
      :key="device.id"
      :data-device-id="device.id"
      :class="{ selected: selectedId === device.id }"
      :aria-expanded="selectedId === device.id"
      @click="selectDevice(device.id)"
    >
      <span
        >{{ device.label }}<small>{{ device.sub.split(' · ')[0] }}</small></span
      ><span :class="{ danger: !platformDevices && deviceModelAlarms[device.id] }">{{
        platformDevices
          ? deviceStatus(device.id)
          : deviceModelAlarms[device.id]
            ? '设备告警（本地演示）'
            : '本地演示'
      }}</span>
      <small v-if="selectedId === device.id" class="detail"
        >类型：{{ device.kind }} · 所属机柜：{{ cabinetCode ?? cabinetId }} · {{ device.source }} ·
        {{ device.registration }} · U 位：{{ device.uPosition }} · 温度：{{
          reading(device.id, 'temperature')
        }}
        · 功率：{{ reading(device.id, 'power') }}</small
      >
    </button>
  </div>
</template>
<script setup lang="ts">
import { computed, ref, watch, nextTick } from 'vue';
import { topologyNodes } from '../../../demo/topologyData';
import { deviceModelAlarms } from '../../../scene/cabinetState';
import type { Device } from '@/types/platform';
import { originLabel, registrationLabel, uPositionLabel } from '@/utils/platform-display';
import { useMonitoringStore } from '@/stores/monitoring';
const monitoring = useMonitoringStore();
function deviceStatus(id: string) {
  const v = monitoring.reading('DEVICE', id, 'online');
  return v ? (v.value === 1 ? '在线' : '离线') : '未采集';
}
function reading(id: string, metric: string) {
  const value = monitoring.reading('DEVICE', id, metric);
  return value ? `${value.value} ${value.unit} · ${value.observedAt}` : '未采集';
}
const props = defineProps<{
  cabinetId: string;
  cabinetCode?: string;
  targetDeviceId?: string;
  platformDevices?: Device[];
}>();
const emit = defineEmits<{ 'select-device': [id: string] }>();
const selectedId = ref('');
function selectDevice(id: string) {
  selectedId.value = selectedId.value === id ? '' : id;
  if (selectedId.value) emit('select-device', id);
}
const list = ref<HTMLElement>();
const devices = computed(() =>
  props.platformDevices
    ? props.platformDevices
        .filter((d) => d.cabinetId === props.cabinetId)
        .map((d) => ({
          id: d.id,
          label: d.name,
          sub: d.code,
          kind: { SERVER: '服务器', SWITCH: '交换机', STORAGE: '存储设备', OTHER: '其他' }[d.type],
          source: originLabel(d.meta),
          registration: registrationLabel(d.registrationStatus),
          uPosition: uPositionLabel(d.uStart, d.uHeight),
        }))
    : topologyNodes
        .filter((device) => device.cabinetId === props.cabinetId)
        .map((d) => ({
          ...d,
          kind: d.layer === 'terminal' ? (d.id === 'nas' ? '存储设备' : '服务器') : '交换机',
          source: '本地演示',
          registration: '演示对象',
          uPosition: '未登记',
        })),
);
watch(
  () => [props.cabinetId, props.targetDeviceId],
  async () => {
    selectedId.value = devices.value.some((device) => device.id === props.targetDeviceId)
      ? props.targetDeviceId!
      : '';
    await nextTick();
    const row = Array.from(
      list.value?.querySelectorAll<HTMLElement>('[data-device-id]') ?? [],
    ).find((item) => item.dataset.deviceId === selectedId.value);
    if (row && list.value) list.value.scrollTop = row.offsetTop - list.value.offsetTop;
  },
  { immediate: true, flush: 'post' },
);
</script>
<style scoped>
.cabinet-devices {
  display: flex;
  flex-direction: column;
  gap: 4px;
  height: 100%;
  overflow: auto;
  padding: 4px 6px;
  box-sizing: border-box;
  font-size: 11px;
}
.cabinet-devices {
  scrollbar-width: thin;
  scrollbar-color: #237da5 #092441;
  scrollbar-gutter: stable;
}
.cabinet-devices::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}
.cabinet-devices::-webkit-scrollbar-track {
  background: #092441;
  border-radius: 8px;
}
.cabinet-devices::-webkit-scrollbar-thumb {
  background: #237da5;
  border-radius: 8px;
}
.cabinet-devices::-webkit-scrollbar-thumb:hover {
  background: #42bddb;
}
.cabinet-devices::-webkit-scrollbar-thumb:active {
  background: #65e4ff;
}
.cabinet-devices::-webkit-scrollbar-button {
  display: none;
  width: 0;
  height: 0;
}
.cabinet-devices::-webkit-scrollbar-corner {
  background: #092441;
}
.note {
  color: #83a7c5;
  font-size: 10px;
  margin: 0 0 8px;
  line-height: 1.5;
}
.empty {
  text-align: center;
  color: #94b1c8;
  padding: 18px 0;
}
button {
  display: flex;
  flex: 1 0 auto;
  min-height: 44px;
  gap: 4px;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  padding: 5px 6px;
  margin: 0;
  background: #0b2a49;
  border: 1px solid #24557b;
  color: #c6e2ff;
  text-align: left;
  cursor: pointer;
  font: inherit;
}
small {
  display: block;
  margin-top: 4px;
  color: #8baccc;
}
.danger {
  color: #ff6868;
}
.detail {
  flex-basis: 100%;
  line-height: 1.6;
  border-top: 1px solid #24557b;
  padding-top: 5px;
}
button.selected {
  background: #124867;
  border-color: #65e4ff;
  box-shadow: inset 3px 0 #65e4ff;
}
button:focus-visible {
  outline: 2px solid #68e3ff;
}
</style>
