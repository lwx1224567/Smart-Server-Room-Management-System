import { ref, computed, watch, onBeforeUnmount } from 'vue';
import { useMonitoringStore } from '@/stores/monitoring';
import { usePlatformStore } from '@/stores/platform';
import { inventoryApi } from '@/api/platform/inventory';
import { adaptPlatformScene, adaptCabinetInventory } from '../../../scene/platform-scene-adapter';
import { syncZoneMonitoring } from '../../../scene/zone-monitoring';
import { syncCabinetMonitoring } from '../../../scene/cabinet-monitoring';
import { originLabel } from '@/utils/platform-display';

// Asset statistics, scene synchronization and cabinet detail requests.
export function useAssetDashboard() {
  const monitoring = useMonitoringStore();
  const platform = usePlatformStore(),
    snapshot = computed(() => platform.scene);
  const root = ref(null),
    assetScene = ref(null);
  const selectedCameraId = ref('zone-a'),
    databaseScene = ref(),
    assetDetail = ref(),
    assetLoading = ref(false),
    assetError = ref('');
  let assetRequest = 0;
  const sourceText = computed(
    () =>
      platform.error ||
      (platform.loading
        ? '正在查询数据库台账…'
        : snapshot.value
          ? originLabel(platform.room?.meta)
          : '台账尚未加载'),
  );
  const typeCounts = computed(() => [
    {
      type: 'CABINET',
      name: '机柜',
      color: '#45d9a5',
      count: snapshot.value?.cabinets.length ?? 0,
    },
    ...[
      ['SERVER', '服务器', '#35d3fa'],
      ['SWITCH', '交换机', '#4a91ff'],
      ['STORAGE', '存储', '#8a7aff'],
      ['OTHER', '其他', '#889aa5'],
    ].map(([type, name, color]) => ({
      type,
      name,
      color,
      count: snapshot.value?.devices.filter((d) => d.type === type).length ?? 0,
    })),
    {
      type: 'AC',
      name: '空调',
      color: '#ffb95d',
      count: monitoring.snapshot?.facilities.filter((f) => f.type === 'AC').length,
    },
    {
      type: 'CAMERA',
      name: '摄像头',
      color: '#ec83ae',
      count: monitoring.snapshot?.facilities.filter((f) => f.type === 'CAMERA').length,
    },
  ]);
  const runtimeCounts = computed(() => {
    if (!snapshot.value || !monitoring.snapshot)
      return { normal: '--', abnormal: '--', unknown: '--' };
    let normal = 0,
      abnormal = 0,
      unknown = 0;
    for (const device of snapshot.value.devices) {
      const reading = monitoring.reading('DEVICE', device.id, 'online');
      if (!reading) unknown++;
      else if (reading.value === 1) normal++;
      else abnormal++;
    }
    return { normal, abnormal, unknown };
  });
  const capacity = computed(() => {
    const data = snapshot.value;
    if (
      !data?.cabinets.length ||
      data.cabinets.some((c) => !data.devices.some((d) => d.cabinetId === c.id)) ||
      data.devices.some((d) => d.uStart == null || d.uHeight == null)
    )
      return { used: null, free: null, percent: null };
    const total = data.cabinets.reduce((n, c) => n + c.capacityU, 0),
      used = data.devices.reduce((n, d) => n + (d.uHeight ?? 0), 0);
    return { used, free: total - used, percent: `${((used / total) * 100).toFixed(1)}%` };
  });
  function statusPercent(value) {
    const count = snapshot.value?.devices.length ?? 0;
    return typeof value === 'number' && count ? `${(value / count) * 100}%` : '0%';
  }
  const assetTotal = computed(() =>
    typeCounts.value.reduce((sum, item) => sum + (item.count ?? 0), 0),
  );
  const pieBackground = computed(() => {
    const total = assetTotal.value;
    if (!snapshot.value || !total) return '#0c3045';
    let start = 0;
    return `conic-gradient(${typeCounts.value
      .map((item) => {
        const end = start + ((item.count ?? 0) / total) * 100,
          part = `${item.color} ${start}% ${end}%`;
        start = end;
        return part;
      })
      .join(',')})`;
  });
  const zoneCounts = computed(() =>
    (snapshot.value?.zones ?? []).map((z) => {
      const cabinets = snapshot.value.cabinets.filter((c) => c.zoneId === z.id);
      const ids = new Set(cabinets.map((c) => c.id));
      return {
        id: z.id,
        code: z.code,
        cabinets: cabinets.length,
        devices: snapshot.value.devices.filter((d) => ids.has(d.cabinetId)).length,
      };
    }),
  );
  watch(
    snapshot,
    (value) => {
      assetRequest++;
      assetDetail.value = undefined;
      assetError.value = '';
      assetLoading.value = false;
      databaseScene.value = value ? adaptPlatformScene(value) : undefined;
      if (databaseScene.value) databaseScene.value.inventory = [];
    },
    { immediate: true },
  );
  watch(
    [() => monitoring.snapshot, snapshot],
    () => {
      if (!databaseScene.value) return;
      syncCabinetMonitoring(databaseScene.value, snapshot.value, monitoring);
      syncZoneMonitoring(databaseScene.value, monitoring.reading);
    },
    { deep: true, immediate: true },
  );
  onBeforeUnmount(() => {
    assetRequest++;
  });
  function returnToRoom() {
    assetRequest++;
    assetDetail.value = undefined;
    assetError.value = '';
    assetLoading.value = false;
    if (databaseScene.value) databaseScene.value.inventory = [];
    assetScene.value?.overview();
  }
  async function selectModelAsset(key) {
    const resolved = platform.identityIndex.resolveCabinetSceneKey(key);
    if (resolved.state !== 'LINKED') {
      assetError.value = '未关联数据库机柜';
      return;
    }
    const ticket = ++assetRequest,
      original = snapshot.value;
    assetDetail.value = undefined;
    assetError.value = '';
    assetLoading.value = true;
    if (databaseScene.value) databaseScene.value.inventory = [];
    try {
      const [detail, list] = await Promise.all([
        inventoryApi.cabinet(resolved.ref.cabinetId),
        inventoryApi.devices(resolved.ref.cabinetId),
      ]);
      if (ticket !== assetRequest || original !== snapshot.value) return;
      if (
        detail.id !== resolved.ref.cabinetId ||
        detail.roomId !== original.roomId ||
        list.items.some((d) => d.cabinetId !== detail.id)
      )
        throw new Error('机柜详情归属不一致');
      assetDetail.value = detail;
      if (databaseScene.value)
        databaseScene.value.inventory = adaptCabinetInventory(list.items, detail.sceneModelKey);
    } catch (error) {
      if (ticket === assetRequest)
        assetError.value = error instanceof Error ? error.message : '台账查询失败';
    } finally {
      if (ticket === assetRequest) assetLoading.value = false;
    }
  }
  function assetSelectZone(id) {
    const cabinet = snapshot.value?.cabinets.find((c) => c.zoneId === id);
    if (cabinet) {
      void selectModelAsset(cabinet.sceneModelKey);
      assetScene.value?.select(cabinet.sceneModelKey);
    }
  }
  return {
    platform,
    snapshot,
    root,
    assetScene,
    selectedCameraId,
    databaseScene,
    assetDetail,
    assetLoading,
    assetError,
    sourceText,
    typeCounts,
    runtimeCounts,
    capacity,
    statusPercent,
    assetTotal,
    pieBackground,
    zoneCounts,
    returnToRoom,
    selectModelAsset,
    assetSelectZone,
  };
}
