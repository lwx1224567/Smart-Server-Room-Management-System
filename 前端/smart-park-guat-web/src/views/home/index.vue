<template>
  <AssetCockpit v-if="!isDeviceDetail && activeBusiness === 'assets'" mode="assets" />
  <TopologyCockpit
    v-else-if="!isDeviceDetail && activeBusiness === 'topology'"
    @locate-device="locateTopologyDevice"
  />
  <div v-else class="home-dashboard">
    <div class="home-center">
      <RoomScene
        v-if="!platformMode || formalSceneData"
        ref="roomScene"
        :scene-data="platformMode ? formalSceneData : undefined"
        @select="selectForAssistant"
        @inspect="inspectFromScene"
        @overview="leaveDeviceDetail"
      />
      <div class="scene-controls">
        <button
          type="button"
          class="scene-control"
          title="重置视角"
          aria-label="重置视角"
          @click="roomScene?.resetView()"
        >
          <img src="@/assets/icon/global-perspective.png" alt="" />
        </button>
        <button
          v-if="isDeviceDetail"
          type="button"
          class="scene-control"
          title="返回首页"
          aria-label="返回首页"
          @click="goBack"
        >
          <img src="@/assets/icon/back.png" alt="" />
        </button>
      </div>
    </div>
    <CabinetCockpit v-if="isDeviceDetail" :dashboard="dashboard" />
    <EnergyCockpit v-else-if="activeBusiness === 'energy'" :dashboard="dashboard" />
    <AlarmCockpit v-else-if="activeBusiness === 'alarm'" :dashboard="dashboard" />
    <RoomCockpit v-else :dashboard="dashboard" />
  </div>
</template>

<script setup lang="ts">
import RoomScene from './scene/RoomScene.vue';
import RoomCockpit from './cockpit/room/index.vue';
import CabinetCockpit from './cockpit/cabinet/index.vue';
import EnergyCockpit from './cockpit/energy/index.vue';
import AlarmCockpit from './cockpit/alarms/index.vue';
import TopologyCockpit from './cockpit/topology/index.vue';
import AssetCockpit from './cockpit/assets/index.vue';
import { useHomeDashboard, type HomeEmit } from './composables/useHomeDashboard';

const props = withDefaults(defineProps<{ activeBusiness?: string }>(), { activeBusiness: '' });
const emit = defineEmits<HomeEmit>();
const dashboard = useHomeDashboard(props, emit);
const {
  formalSceneData,
  roomScene,
  isDeviceDetail,
  platformMode,
  selectForAssistant,
  inspectFromScene,
  leaveDeviceDetail,
  locateTopologyDevice,
  goBack,
} = dashboard;
defineExpose({
  resetOverview: dashboard.resetOverview,
});
</script>

<style scoped lang="scss" src="./styles/dashboard.scss"></style>
