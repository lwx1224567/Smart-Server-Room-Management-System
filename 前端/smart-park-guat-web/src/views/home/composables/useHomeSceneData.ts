import { ref, watch } from 'vue';
import { usePlatformStore } from '@/stores/platform';
import { useMonitoringStore } from '@/stores/monitoring';
import { energyPeriod } from '../cockpit/energy/energy-data';
import { adaptPlatformScene, adaptCabinetInventory } from '../scene/platform-scene-adapter';
import type { SceneInput } from '../scene/scene.ts';
import { syncZoneMonitoring } from '../scene/zone-monitoring';
import { syncCabinetMonitoring } from '../scene/cabinet-monitoring';

// Synchronize database inventory and monitoring with the scene input.
export function useHomeSceneData() {
  const platform = usePlatformStore();
  const monitoring = useMonitoringStore();
  async function refreshMonitoring() {
    if (platform.room) {
      await monitoring.load(platform.room.id);
      await Promise.all([
        monitoring.loadEnergy(platform.room.id, energyPeriod.value === '7天' ? '7d' : '30d'),
        monitoring.loadWeek(platform.room.id),
      ]);
    }
  }
  watch(
    () => platform.room?.id,
    (id) => {
      if (id) void refreshMonitoring();
      else monitoring.clear();
    },
  );
  watch(energyPeriod, () => {
    if (platform.room)
      void monitoring.loadEnergy(platform.room.id, energyPeriod.value === '7天' ? '7d' : '30d');
  });
  const formalSceneData = ref<SceneInput>();
  watch(
    () => platform.scene,
    (value) => {
      formalSceneData.value = value ? adaptPlatformScene(value) : undefined;
    },
    { immediate: true },
  );
  watch(
    [() => monitoring.snapshot, () => platform.scene],
    () => {
      const scene = formalSceneData.value;
      if (!scene) return;
      syncCabinetMonitoring(scene, platform.scene, monitoring);
      syncZoneMonitoring(scene, monitoring.reading);
    },
    { deep: true, immediate: true },
  );
  watch([() => platform.devices, () => platform.detail, () => platform.detailLoading], () => {
    if (formalSceneData.value)
      formalSceneData.value.inventory =
        platform.detail && !platform.detailLoading
          ? adaptCabinetInventory(platform.devices, platform.detail.sceneModelKey)
          : [];
  });

  return { platform, monitoring, formalSceneData, refreshMonitoring };
}
