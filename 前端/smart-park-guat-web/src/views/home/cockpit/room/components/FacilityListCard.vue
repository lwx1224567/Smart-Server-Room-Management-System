<template>
  <div class="device-list">
    <section
      v-for="group in deviceGroups"
      :key="group.id"
      class="list-section"
      :class="{ expanded: expandedGroup === group.id }"
    >
      <button
        class="list-header"
        :aria-expanded="expandedGroup === group.id"
        @click="expandedGroup = expandedGroup === group.id ? '' : group.id"
      >
        <span>{{ group.name }}（{{ group.devices.length }}）</span>
        <span class="arrow" :class="{ collapsed: expandedGroup !== group.id }" aria-hidden="true"
          >▼</span
        >
      </button>
      <div v-show="expandedGroup === group.id" class="list-content">
        <button
          v-for="device in group.devices"
          :key="device.id"
          class="list-item"
          :class="{ selected: (props.selectedId ?? selectedId) === device.id }"
          :aria-pressed="(props.selectedId ?? selectedId) === device.id"
          title="点击定位设施"
          @click="selectDevice(device)"
        >
          <span class="item-name">{{ device.name }}</span>
          <span
            class="item-status"
            :class="`status-${statusOf(device).kind}`"
            :style="{ color: statusOf(device).color }"
          >
            <span class="status-dot" aria-hidden="true"></span>
            <span>{{ statusOf(device).text }}</span>
          </span>
        </button>
        <p v-if="!group.devices.length" class="empty-category">当前楼层暂无{{ group.name }}</p>
      </div>
    </section>
    <p v-if="!deviceGroups.length" class="empty-category">暂无设备数据</p>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { alarmLevels, alarmLevelName } from '@/utils/alarm-levels';

interface Device {
  id: string;
  name: string;
  status?: string;
  alarmSeverity?: string;
}
interface DeviceGroup {
  id: string;
  name: string;
  devices: Device[];
}
const props = defineProps<{ groups?: DeviceGroup[]; selectedId?: string }>();
const emit = defineEmits<{ select: [device: Device] }>();
const expandedGroup = ref('cabinet');
const selectedId = ref('');
const demoGroups: DeviceGroup[] = [
  {
    id: 'cabinet',
    name: '机柜',
    devices: [
      { id: 'demo-cabinet-1', name: '机柜 A01' },
      { id: 'demo-cabinet-2', name: '机柜 A02' },
    ],
  },
  { id: 'ups', name: 'UPS设备', devices: [{ id: 'demo-ups-1', name: 'UPS 01' }] },
  { id: 'camera', name: '室内摄像头', devices: [{ id: 'demo-camera-1', name: '室内摄像头 01' }] },
  {
    id: 'aircondition',
    name: '空调',
    devices: [{ id: 'demo-aircondition-1', name: '精密空调 01' }],
  },
];
const deviceGroups = computed(() => props.groups ?? demoGroups);
function selectDevice(device: Device) {
  selectedId.value = device.id;
  emit('select', device);
}
function statusOf(device: Device) {
  const level = alarmLevels.find((level) => level.key === device.alarmSeverity);
  if (level)
    return {
      kind: 'alarm',
      text: `${device.status?.includes('离线') ? '离线 · ' : ''}${alarmLevelName(level.key)}`,
      color: level.color,
    };
  switch (device.status) {
    case 'online':
    case '在线':
    case '正常':
      return { kind: 'online', text: '在线', color: undefined };
    case 'maintenance':
    case '维护':
      return { kind: 'maintenance', text: '维护' };
    case 'alarm':
    case '告警':
    case '故障':
      return { kind: 'alarm', text: '告警' };
    case 'offline':
    case '离线':
    case '断线':
      return { kind: 'offline', text: '离线' };
    case '离线 · 告警':
      return { kind: 'alarm', text: '离线 · 告警' };
    case 'UNKNOWN':
      return { kind: 'unknown', text: 'UNKNOWN · 未采集' };
    default:
      return {
        kind: 'unknown',
        text: device.status && device.status !== 'unknown' ? device.status : '未采集',
      };
  }
}
</script>

<style scoped lang="scss">
.device-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  color: #fff;
}
.list-section {
  display: flex;
  flex-direction: column;
  flex: 0 0 auto;
  min-height: 0;
}
.list-section.expanded {
  flex: 1 1 0;
}
.list-header {
  display: flex;
  flex-shrink: 0;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  padding: 4px 6px;
  border: 1px solid rgba(36, 191, 255, 0.3);
  background: rgba(3, 52, 117, 0.63);
  box-shadow: inset 0 0 6px 2px rgba(160, 181, 225, 0.3);
  color: #fff;
  font: inherit;
  font-size: 12px;
  line-height: 1.5;
  font-weight: bold;
  cursor: pointer;
  text-align: left;
}
.arrow {
  display: inline-block;
  color: #ddedf3;
  font-size: 8px;
  transition: transform 0.3s;
}
.arrow.collapsed {
  transform: rotate(-90deg);
}
.list-content {
  flex: 1 1 0;
  min-height: 0;
  overflow-y: auto;
  padding: 4px 0;
  margin-top: 4px;
  scrollbar-gutter: stable;
}
.list-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 5px 8px;
  margin-bottom: 3px;
  border: 1px solid rgba(36, 191, 255, 0.3);
  border-radius: 3px;
  background: rgba(48, 170, 223, 0.315);
  color: #d8effc;
  font: inherit;
  cursor: pointer;
  transition:
    background 0.2s,
    border-color 0.2s;
  &:hover {
    background: rgba(36, 191, 255, 0.15);
    border-color: rgba(36, 191, 255, 0.5);
  }
  &.selected {
    border-color: #32e8f2;
    background: rgba(27, 137, 187, 0.5);
    box-shadow: inset 0 0 0 1px #32e8f2;
  }
}
.list-header:focus-visible,
.list-item:focus-visible {
  outline: 2px solid #32e8f2;
  outline-offset: -2px;
}
.item-name {
  min-width: 0;
  overflow-wrap: anywhere;
  font-size: 12px;
  text-align: left;
}
.item-status {
  display: flex;
  align-items: center;
  gap: 5px;
  flex-shrink: 0;
  font-size: 11px;
}
.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}
.status-online {
  color: #52c41a;
}
.status-maintenance {
  color: #faad14;
}
.status-alarm {
  color: #ff4d4f;
}
.status-offline {
  color: #8c8c8c;
}
.status-unknown {
  color: #b5c4d3;
}
.empty-category {
  padding: 6px;
  color: #aac1d5;
  font-size: 11px;
}
.device-list::-webkit-scrollbar,
.list-content::-webkit-scrollbar {
  width: 6px;
}
.device-list::-webkit-scrollbar-thumb,
.list-content::-webkit-scrollbar-thumb {
  background: rgba(36, 191, 255, 0.5);
  border-radius: 3px;
}
</style>
