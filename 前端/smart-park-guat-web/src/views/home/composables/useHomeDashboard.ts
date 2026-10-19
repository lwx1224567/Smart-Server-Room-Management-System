import { useCabinetControls } from './useCabinetControls';
import { useHomeSceneData } from './useHomeSceneData';
import { cardGroups } from '../cockpit/card-groups';
import type { CabinetRef, DeviceRef } from '@/types/platform-identity';
import { createLegacyDemoBridge } from '../demo/legacy-demo-scene-bridge';
import { createAgentAssistant } from '../assistant/agent-assistant';
import { computed, ref, watch, onBeforeUnmount, nextTick, onMounted } from 'vue';
import { loadOrders } from '@/services/work-orders';
import { demoFacilities } from '../scene/facilities';
import { ElMessage } from 'element-plus';
import { integrationApi } from '@/api/platform/integrations';
import RoomScene from '../scene/RoomScene.vue';

export interface HomeProps {
  activeBusiness: string;
}
export type HomeEmit = {
  (event: 'back-home'): void;
  (event: 'select-device' | 'enter-cabinet', device: { id: string; name: string }): void;
};

export function useHomeDashboard(props: HomeProps, emit: HomeEmit) {
  const { platform, monitoring, formalSceneData, refreshMonitoring } = useHomeSceneData();
  // Preview removed: legacy demo actions never receive the formal scene.
  const bridge = createLegacyDemoBridge(
    () => {},
    () => undefined,
  );
  const roomScene = ref<InstanceType<typeof RoomScene>>();
  const assistant = createAgentAssistant(() => roomScene.value, {
    resolveCabinet: (key: string) => platform.identityIndex.resolveCabinetSceneKey(key),
  });
  const patrolTimer = setInterval(() => assistant.poll(), 250);
  const ordersTimer = setInterval(() => {
    void loadOrders();
    void monitoring.refresh();
  }, 15000);
  onBeforeUnmount(() => {
    clearInterval(patrolTimer);
    clearInterval(ordersTimer);
    assistant.dispose();
    bridge.dispose();
  });
  const hardwareControls = ref(false);
  watch(() => platform.scene?.roomId, room => { void assistant.enableAgent(room || ''); }, { immediate: true });
  onMounted(() => {
    void platform.load();
    void loadOrders();
    void integrationApi
      .capabilities()
      .then((capabilities) => {
        hardwareControls.value = capabilities.controlGatewayConfigured;

      })
      .catch(() => {});
  });
  function selectForAssistant(key: string) {
    const result = platform.identityIndex.resolveCabinetSceneKey(key);
    selectedDevice.value = result.state === 'LINKED' ? result.ref.cabinetId : '';
    assistant.select(key);
  }
  async function inspectFromAssistant(ref: CabinetRef) {
    await showDeviceDetail(ref.cabinetId);
  }
  async function locateAlarm(key: string) {
    const identity = platform.identityIndex.resolveCabinetSceneKey(key);
    if (identity.state !== 'LINKED') {
      ElMessage.warning('告警尚未关联机柜台账');
      return;
    }
    assistant.select(key);
    assistant.state.busy = true;
    assistant.state.progress = 0;
    assistant.state.status = `正在前往 ${key}`;
    try {
      const receipt = await assistant.scene.execute({ type: 'visit', cabinetSceneModelKey: key });
      assistant.state.status = receipt.result;
      if (receipt.status !== 'SUCCEEDED') ElMessage.warning(receipt.result);
    } finally {
      assistant.state.busy = false;
    }
  }
  async function createAlarmOrder(key: string) {
    assistant.select(key);
    await assistant.command(`创建工单 ${key}`);
  }
  const selectedDevice = ref('');
  const detailDeviceId = computed(() => platform.selectedId);
  const targetInventoryDeviceId = ref('');
  const currentCabinet = computed(() =>
    platform.detail ? { ...platform.detail, zone: platform.detail.zoneCode || '' } : undefined,
  );
  const isDeviceDetail = computed(() => !!detailDeviceId.value);
  const isRoomOverview = computed(
    () => !isDeviceDetail.value && (!props.activeBusiness || props.activeBusiness === 'operations'),
  );
  const platformMode = computed(() => true);
  const { currentOperations, updateControl } = useCabinetControls({
    platform,
    monitoring,
    formalSceneData,
    currentCabinet,
    roomScene,
    hardwareControls,
    refreshMonitoring,
  });
  watch(
    () => props.activeBusiness,
    () => {
      leaveDeviceDetail();
      roomScene.value?.overview();
    },
  );
  function cabinetStatus(id: string) {
    return monitoring.cabinetStatus(
      id,
      (platform.scene?.devices ?? []).filter((d) => d.cabinetId === id).map((d) => d.id),
    );
  }
  const deviceGroups = computed(() => {
    return [
      {
        id: 'cabinet',
        name: '机柜',
        devices: platform.cabinets.map((c) => ({
          id: c.id,
          name: c.name,
          status: cabinetStatus(c.id),
          alarmSeverity: monitoring.cabinetAlarm(
            c.id,
            (platform.scene?.devices ?? []).filter((d) => d.cabinetId === c.id).map((d) => d.id),
          )?.severity,
        })),
      },
      ...[
        { id: 'camera', name: '摄像头', type: 'CAMERA' },
        { id: 'aircondition', name: '空调', type: 'AC' },
      ].map((group) => ({
        ...group,
        devices: (monitoring.snapshot?.facilities ?? [])
          .filter((f) => f.type === group.type)
          .map((f) => ({ id: f.id, name: f.name ?? f.id, status: f.status ?? '未采集' })),
      })),
    ];
  });
  let navigation = 0;
  async function showDeviceDetail(id: string) {
    const result = platform.identityIndex.resolveCabinetId(id);
    if (result.state !== 'LINKED') return false;
    navigation++;
    targetInventoryDeviceId.value = '';
    selectedDevice.value = result.ref.cabinetId;
    return platform.selectCabinet(result.ref.cabinetId);
  }
  function inspectFromScene(key: string) {
    const result = platform.identityIndex.resolveCabinetSceneKey(key);
    if (result.state === 'LINKED' && platform.selectedId !== result.ref.cabinetId)
      void showDeviceDetail(result.ref.cabinetId);
  }
  function leaveDeviceDetail() {
    navigation++;
    platform.clearSelection();
    selectedDevice.value = '';
    targetInventoryDeviceId.value = '';
  }
  function extractInventoryDevice(id: string) {
    const result = platform.identityIndex.resolveDeviceId(id);
    if (
      result.state === 'LINKED' &&
      result.ref.cabinetId === platform.detail?.id &&
      platform.devices.some((d) => d.id === id && d.cabinetId === result.ref.cabinetId)
    )
      roomScene.value?.extractDevice(result.ref.sceneModelKey);
  }
  async function locateTopologyDevice(target: DeviceRef) {
    const result = platform.identityIndex.resolveDeviceId(target.deviceId);
    if (
      result.state !== 'LINKED' ||
      result.ref.cabinetId !== target.cabinetId ||
      result.ref.sceneModelKey !== target.sceneModelKey
    )
      return;
    const loading = showDeviceDetail(result.ref.cabinetId);
    const ticket = navigation,
      snapshot = platform.scene;
    if (!(await loading) || ticket !== navigation || snapshot !== platform.scene) return;
    const device = platform.devices.find(
      (d) =>
        d.id === result.ref.deviceId &&
        d.cabinetId === result.ref.cabinetId &&
        d.sceneModelKey === result.ref.sceneModelKey,
    );
    if (!device || platform.detail?.id !== result.ref.cabinetId) return;
    await nextTick();
    if (
      ticket !== navigation ||
      snapshot !== platform.scene ||
      platform.detail?.id !== result.ref.cabinetId
    )
      return;
    targetInventoryDeviceId.value = device.id;
    roomScene.value?.inspect(result.ref.cabinetSceneModelKey);
    roomScene.value?.extractDevice(device.sceneModelKey);
  }
  async function selectFacility(device: { id: string; name: string }) {
    const facility = monitoring.snapshot?.facilities.find((f) => f.id === device.id);
    if (facility) {
      if (!facility.sceneModelKey || !demoFacilities.some((f) => f.id === facility.sceneModelKey))
        ElMessage.warning('未关联场景');
      else if (!roomScene.value?.locateFacility(facility.sceneModelKey))
        ElMessage.warning('场景尚未加载，暂时无法定位');
      else selectedDevice.value = device.id;
      return;
    }
    await enterCabinet(device);
  }
  async function enterCabinet(device: { id: string; name: string }) {
    await showDeviceDetail(device.id);
    if (currentCabinet.value?.id !== device.id) return;
    await nextTick();
    roomScene.value?.inspect(currentCabinet.value.sceneModelKey);
    emit('enter-cabinet', device);
  }
  async function retryDetail() {
    await platform.selectCabinet(platform.selectedId);
  }
  watch(
    () => platform.detail,
    async (value) => {
      if (!value) return;
      const ticket = navigation,
        snapshot = platform.scene;
      selectedDevice.value = value.id;
      await nextTick();
      if (ticket !== navigation || snapshot !== platform.scene || platform.detail?.id !== value.id)
        return;
      if (roomScene.value?.getState()?.inspected !== value.sceneModelKey)
        roomScene.value?.inspect(value.sceneModelKey);
    },
  );

  function goBack() {
    roomScene.value?.overview();
    leaveDeviceDetail();
    emit('back-home');
  }
  const cards = computed(
    () =>
      cardGroups[
        isDeviceDetail.value ? 'operations' : isRoomOverview.value ? 'home' : props.activeBusiness
      ] ?? cardGroups.home!,
  );

  return {
    platform,
    monitoring,
    formalSceneData,
    roomScene,
    assistant,
    selectedDevice,
    targetInventoryDeviceId,
    currentCabinet,
    isDeviceDetail,
    isRoomOverview,
    platformMode,
    currentOperations,
    deviceGroups,
    cards,
    refreshMonitoring,
    selectForAssistant,
    inspectFromAssistant,
    locateAlarm,
    createAlarmOrder,
    updateControl,
    inspectFromScene,
    leaveDeviceDetail,
    extractInventoryDevice,
    locateTopologyDevice,
    selectFacility,
    retryDetail,
    goBack,
    resetOverview: () => {
      roomScene.value?.overview();
      leaveDeviceDetail();
    },
  };
}
export type HomeDashboard = ReturnType<typeof useHomeDashboard>;
