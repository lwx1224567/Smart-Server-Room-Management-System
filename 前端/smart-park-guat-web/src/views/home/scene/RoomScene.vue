<template>
  <section ref="root" class="room-scene" :data-platform="!!sceneData" aria-label="三维机房">
    <div id="roomViewport" class="room-viewport">
      <div id="sceneCanvas" class="scene-canvas"></div>
      <div id="zoneLabels" class="zone-labels"></div>
      <div id="deviceHover" class="device-hover" hidden></div>
      <div id="sceneError" class="scene-error" hidden role="alert"></div>
      <div id="zoneDetail" class="zone-detail" hidden>
        <button id="closeDetail" aria-label="关闭区域信息">×</button>
        <h3></h3>
        <dl></dl>
      </div>
    </div>
    <div v-if="facilityName" class="selection-info" role="status">
      {{ facilityName }}<button @click="overview">返回机房</button>
    </div>
    <div v-if="selected" class="selection-info" role="status">
      <span
        >{{ selected }} <b>{{ selectedAlarm ? selectedAlarm : sceneData ? '' : '' }}</b></span
      >
      <span v-if="extractedName" class="extracted-info"
        >{{ extractedName }}{{ sceneData ? ' · 台账设备' : '' }}</span
      >
      <button v-if="extractedName" @click="putDeviceBack">放回设备</button>
      <button v-if="!inspecting" @click="inspect(selected)">检视机柜</button>
      <button
        v-if="inspecting"
        :aria-pressed="doorOpen"
        :disabled="modelControls[selected]?.locked"
        @click="toggleDoor"
      >
        {{ modelControls[selected]?.locked ? '柜门已锁定' : doorOpen ? '关闭柜门' : '打开柜门' }}
      </button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, computed, watch } from 'vue';
import { createRoom } from './scene.ts';
import { mockData } from '../demo/mockData.js';
import { topologyNodes } from '../demo/topologyData';
import { cabinetControls, deviceModelAlarms } from './cabinetState';
import { demoFacilities } from './facilities';
const props = defineProps<{ sceneData?: import('./scene.ts').SceneInput }>();
const sceneDevices = computed(() => props.sceneData?.devices ?? mockData.devices);
const sceneInventory = computed(() => props.sceneData?.inventory ?? topologyNodes);
const modelControls = computed(() => props.sceneData?.controls ?? cabinetControls);
const modelAlarms = computed(() => props.sceneData?.alarms ?? deviceModelAlarms);
const root = ref<HTMLElement>();
const selected = ref('');
const facilityName = ref('');
const inspecting = ref(false);
const doorOpen = ref(false);
const extractedName = ref('');
let doorBeforeExtraction: boolean | undefined;
function putDeviceBack() {
  room?.returnDevice();
  extractedName.value = '';
}
function restoreExtraction() {
  if (doorBeforeExtraction !== undefined && selected.value)
    setDeviceDoor(selected.value, doorBeforeExtraction);
  doorBeforeExtraction = undefined;
  extractedName.value = '';
}
function extractDevice(id: string) {
  const device = sceneInventory.value.find(
    (item) => item.id === id && item.cabinetId === selected.value,
  );
  if (!device || modelControls.value[selected.value]?.locked) return false;
  if (doorBeforeExtraction === undefined) doorBeforeExtraction = doorOpen.value;
  setDeviceDoor(selected.value, true);
  if (!room?.extractDevice(id)) return false;
  extractedName.value = device.sub.split(' · ')[0]!;
  return true;
}
const doors = new Map<string, boolean>();
const emit = defineEmits<{
  select: [id: string];
  inspect: [id: string];
  overview: [];
  cameraSelect: [id: string];
}>();
let room: ReturnType<typeof createRoom> | undefined;
const selectedAlarm = computed(
  () => sceneDevices.value.find((device) => device.id === selected.value)?.alarm?.title,
);
function select(id: string) {
  if (inspecting.value) {
    room?.showRoom();
    inspecting.value = false;
  }
  selected.value = id;
  room?.markTarget(id);
  emit('select', id);
}
function inspect(id: string) {
  facilityName.value = '';
  room?.clearFacility();
  restoreExtraction();
  selected.value = id;
  inspecting.value = true;
  doorOpen.value = doors.get(id) ?? false;
  room?.inspectDevice(id);
  emit('select', id);
  emit('inspect', id);
}
function toggleDoor() {
  if (modelControls.value[selected.value]?.locked) return;
  if (doorOpen.value) extractedName.value = '';
  doorOpen.value = !doorOpen.value;
  doors.set(selected.value, doorOpen.value);
  room?.setDoor(selected.value, doorOpen.value);
  const device = sceneDevices.value.find((item) => item.id === selected.value);
  if (device) device.doorOpen = doorOpen.value;
}
function overview() {
  restoreExtraction();
  inspecting.value = false;
  selected.value = '';
  facilityName.value = '';
  room?.clearFacility();
  room?.markTarget(null);
  room?.setExpanded(true);
  emit('overview');
}
function resetView() {
  if (facilityName.value) {
    facilityName.value = '';
    room?.clearFacility();
  }
  room?.resetView();
}
function locateFacility(id: string) {
  const facility = demoFacilities.find((item) => item.id === id);
  if (!facility || !room) return false;
  overview();
  if (!room.locateFacility(id)) return false;
  facilityName.value = facility.name;
  if (facility.type === 'camera') emit('cameraSelect', id);
  return true;
}
async function visit(id: string) {
  const device = sceneDevices.value.find((item) => item.id === id);
  if (!room || !device) throw new Error('三维场景尚未就绪');
  overview();
  room.markTarget(id);
  const result = await room.patrol.visit(device);
  if (!result.cancelled) {
    selected.value = id;
    emit('select', id);
  }
  return result;
}
function cancelPatrol() {
  room?.patrol.cancel();
  room?.markTarget(null);
}
function resumePatrol() {
  overview();
  room?.patrol.resume();
}
function setDeviceDoor(id: string, open: boolean) {
  if (open && modelControls.value[id]?.locked) return;
  doors.set(id, open);
  room?.setDoor(id, open);
  if (selected.value === id) doorOpen.value = open;
}
function setAlarm(id: string, alarm: unknown) {
  if (alarm === null) {
    room?.setAlarm(id, null);
    return;
  }
  if (
    typeof alarm === 'object' &&
    alarm &&
    'title' in alarm &&
    typeof alarm.title === 'string' &&
    'severity' in alarm &&
    typeof alarm.severity === 'string'
  )
    room?.setAlarm(id, { title: alarm.title, severity: alarm.severity });
}
function syncModelControls() {
  if (!room) return;
  for (const [id, controls] of Object.entries(modelControls.value)) {
    room.setCabinetControls(id, controls);
    if (controls.locked) {
      setDeviceDoor(id, false);
      if (selected.value === id) {
        extractedName.value = '';
        doorBeforeExtraction = undefined;
      }
    }
  }
  for (const device of sceneInventory.value)
    room.setDeviceAlarm(device.id, modelAlarms.value[device.id] ?? null);
}
watch([modelControls, modelAlarms], syncModelControls, { deep: true });
watch(sceneInventory, (value) => room?.setInventory(value));
function syncMonitoring() {
  if (props.sceneData) room?.setMonitoring(props.sceneData.devices, props.sceneData.zones);
}
watch(() => [props.sceneData?.devices, props.sceneData?.zones], syncMonitoring, { deep: true });
function patrolState() {
  return room?.patrol.getState();
}
defineExpose({
  getAgentState: () => ({
    ready: !!room,
    selected: selected.value,
    devices: (room?.getState().devices ?? []).map(device => ({
      ...device, locked: !!modelControls.value[device.id]?.locked,
    })),
    patrol: room?.patrol.getState(),
  }),
  extractDevice,
  select,
  inspect,
  overview,
  resetView,
  locateFacility,
  visit,
  cancelPatrol,
  resumePatrol,
  setDeviceDoor,
  setAlarm,
  patrolState,
  isDoorOpen: () => doorOpen.value,
  getState: () => room?.getState(),
});
onMounted(() => {
  try {
    room = createRoom(root.value!, {
      data: props.sceneData,
      onDevicePick: (id) => {
        select(id);
        inspect(id);
      },
      onDoorClick: toggleDoor,
      onAlarmInspect: inspect,
      onCameraPick: (id) => emit('cameraSelect', id),
    });
    room.setExpanded(true);
    syncModelControls();
    syncMonitoring();
  } catch (error) {
    const el = root.value!.querySelector<HTMLElement>('#sceneError')!;
    el.textContent = '三维场景加载失败，请检查浏览器 WebGL 支持后刷新。';
    el.hidden = false;
    console.error(error);
  }
});
onBeforeUnmount(() => room?.dispose());
</script>

<style scoped lang="scss">
.room-scene {
  position: relative;
  height: 100%;
  color: #c8eaff;
  background: radial-gradient(ellipse at 50% 60%, rgba(14, 110, 183, 0.18), transparent 65%);
}
.room-viewport {
  position: absolute;
  inset: 0;
}
.scene-canvas,
.zone-labels {
  position: absolute;
  inset: 0;
}
.scene-canvas :deep(canvas) {
  width: 100%;
  height: 100%;
  display: block;
  touch-action: none;
}
.zone-labels {
  pointer-events: none;
}
:deep(.zone-label) {
  position: absolute;
  transform: translate(-50%, -50%);
  pointer-events: none;
  border: 1px solid #3388b8;
  border-radius: 5px;
  padding: 5px 9px;
  background: rgba(5, 32, 63, 0.85);
  color: #a5dfff;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  white-space: nowrap;
}
:deep(.zone-connectors) {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  overflow: hidden;
}
:deep(.zone-connectors line) {
  stroke: #48a8cf;
  stroke-width: 1;
  opacity: 0.38;
  stroke-dasharray: 3 4;
}
:deep(.zone-label svg) {
  width: 12px;
  height: 12px;
  vertical-align: middle;
}
:deep(.zone-label strong) {
  color: #65e4ff;
  font-size: 15px;
}
:deep(.zone-label small) {
  font-size: 10px;
}
:deep(.zone-label.is-active) {
  border-color: #65e4ff;
  box-shadow: 0 0 12px #158ace;
}
.device-hover {
  position: absolute;
  pointer-events: none;
  padding: 8px;
  font-size: 11px;
  background: #092a49;
  border: 1px solid #4393bd;
  z-index: 3;
}
.zone-detail {
  position: absolute;
  top: 70px;
  right: 14px;
  padding: 14px;
  background: rgba(4, 23, 48, 0.96);
  border: 1px solid #318bbb;
  z-index: 6;
  min-width: 170px;
  font-size: 12px;
}
.zone-detail button {
  float: right;
}
.zone-detail :deep(dl) {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.zone-detail :deep(dd) {
  margin: 0;
  color: #57dcff;
}
.scene-error {
  position: absolute;
  top: 40%;
  left: 10%;
  right: 10%;
  padding: 18px;
  background: #12344e;
  z-index: 8;
}
.selection-info {
  position: absolute;
  bottom: 22px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 9px 14px;
  border: 1px solid #266e9b;
  border-radius: 4px;
  background: rgba(4, 29, 56, 0.92);
  font-size: 12px;
  white-space: nowrap;
  z-index: 4;
}
.selection-info b {
  margin-left: 10px;
  color: #69d8ef;
  font-weight: normal;
}
button {
  color: #b4e7ff;
  background: #0c3357;
  border: 1px solid #297ba9;
  padding: 5px 10px;
  border-radius: 3px;
  cursor: pointer;
}
button:focus-visible {
  outline: 2px solid #68e3ff;
  outline-offset: 2px;
}
:deep(.active-alarm-pin),
:deep(.patrol-target-pin) {
  display: none;
}
:deep(.patrol-label) {
  position: absolute;
  transform: translate(-50%, -100%);
  padding: 4px 8px;
  border: 1px solid #31dfc1;
  border-radius: 4px;
  background: #092a49;
  font-size: 11px;
  pointer-events: none;
}
</style>
