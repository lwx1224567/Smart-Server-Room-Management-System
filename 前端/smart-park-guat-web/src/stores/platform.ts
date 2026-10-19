import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import { inventoryApi } from '@/api/platform/inventory';
import type { Room, Cabinet, Device, SceneSnapshot } from '@/types/platform';
import { buildPlatformIdentityIndex } from '@/utils/platform-identity';
const selectionKey = 'platform-stage1-cabinet';
export const usePlatformStore = defineStore('platform-inventory', () => {
  const room = ref<Room>();
  const scene = ref<SceneSnapshot>();
  const cabinets = ref<Cabinet[]>([]);
  const identityIndex = computed(() => buildPlatformIdentityIndex(scene.value));
  const detail = ref<Cabinet>();
  const devices = ref<Device[]>([]);
  const selectedId = ref('');
  const loading = ref(false);
  const detailLoading = ref(false);
  const error = ref('');
  const detailError = ref('');
  let generation = 0;
  let loadGeneration = 0;
  const message = (e: unknown) => (e instanceof Error ? e.message : '台账查询失败');
  function remember(id: string) {
    try {
      if (id) sessionStorage.setItem(selectionKey, id);
      else sessionStorage.removeItem(selectionKey);
    } catch {
      /* Storage unavailable: DB querying still works. */
    }
  }
  function clearSelection() {
    generation++;
    selectedId.value = '';
    detail.value = undefined;
    devices.value = [];
    detailLoading.value = false;
    detailError.value = '';
    remember('');
  }
  async function selectCabinet(id: string): Promise<boolean> {
    const ticket = ++generation;
    selectedId.value = id;
    detail.value = undefined;
    devices.value = [];
    detailError.value = '';
    detailLoading.value = true;
    try {
      const [cabinet, list] = await Promise.all([
        inventoryApi.cabinet(id),
        inventoryApi.devices(id),
      ]);
      if (ticket !== generation) return false;
      if (
        !room.value ||
        cabinet.roomId !== room.value.id ||
        !scene.value?.cabinets.some((v) => v.id === cabinet.id)
      ) {
        clearSelection();
        return false;
      }
      detail.value = cabinet;
      devices.value = list.items;
      remember(cabinet.id);
      return true;
    } catch (e) {
      if (ticket !== generation) return false;
      detailError.value = message(e);
      remember('');
      // Only invalid/nonexistent IDs restore overview; outages remain visible and retryable.
      if (/不存在|ID格式|ID超出/.test(detailError.value)) {
        clearSelection();
        return false;
      }
      return false;
    } finally {
      if (ticket === generation) detailLoading.value = false;
    }
  }
  async function load() {
    const ticket = ++loadGeneration;
    generation++;
    loading.value = true;
    detailLoading.value = false;
    error.value = '';
    room.value = undefined;
    scene.value = undefined;
    cabinets.value = [];
    detail.value = undefined;
    devices.value = [];
    selectedId.value = '';
    detailError.value = '';
    try {
      const rooms = await inventoryApi.rooms();
      const first = rooms.items[0];
      if (!first) throw new Error('尚未登记机房');
      const [info, snapshot] = await Promise.all([
        inventoryApi.room(first.id),
        inventoryApi.scene(first.id, first.meta.recordOrigin),
      ]);
      if (ticket !== loadGeneration) return;
      room.value = info;
      scene.value = snapshot;
      cabinets.value = snapshot.cabinets;
      let saved = '';
      try {
        saved = sessionStorage.getItem(selectionKey) || '';
      } catch {
        /* optional UI state */
      }
      if (saved && snapshot.cabinets.some((v) => v.id === saved)) await selectCabinet(saved);
      else remember('');
    } catch (e) {
      if (ticket === loadGeneration) {
        error.value = message(e);
        room.value = undefined;
        scene.value = undefined;
        cabinets.value = [];
      }
    } finally {
      if (ticket === loadGeneration) loading.value = false;
    }
  }
  return {
    room,
    scene,
    cabinets,
    identityIndex,
    detail,
    devices,
    selectedId,
    loading,
    detailLoading,
    error,
    detailError,
    load,
    selectCabinet,
    clearSelection,
  };
});
