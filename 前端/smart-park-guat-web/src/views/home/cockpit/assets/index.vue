<template>
  <div ref="root" class="asset-topology">
    <section id="assets" v-show="mode === 'assets'" class="business-page">
      <div class="twin-layout">
        <div class="twin-side">
          <Border3 class="asset-type-card" name="资产类型分布"
            ><AssetTypesCard
              :snapshot="snapshot"
              :asset-total="assetTotal"
              :pie-background="pieBackground"
              :type-counts="typeCounts"
          /></Border3>
          <Border3 class="asset-monitor-card" name="监控画面"
            ><SurveillanceCard :mode="mode" v-model:camera-id="selectedCameraId"
          /></Border3>
          <Border3 class="asset-status-card" name="资产状态统计"
            ><AssetStatusCard :runtime-counts="runtimeCounts" :status-percent="statusPercent"
          /></Border3>
        </div>
        <div class="twin-center asset-model">
          <RoomScene
            v-if="mode === 'assets' && databaseScene"
            :scene-data="databaseScene"
            ref="assetScene"
            @select="selectModelAsset"
            @camera-select="selectedCameraId = $event"
          />
          <div v-if="!snapshot" class="asset-load-status" role="status">
            {{ sourceText }}
            <button v-if="platform.error" @click="platform.load()">重试台账查询</button>
          </div>
          <button
            v-if="assetDetail || assetLoading || assetError"
            class="asset-return"
            type="button"
            title="返回首页"
            aria-label="返回首页"
            @click="returnToRoom"
          >
            <img src="@/assets/icon/back.png" alt="" /></button
          ><button
            class="model-reset"
            type="button"
            aria-label="重置视角"
            title="重置视角"
            @click="assetScene?.resetView()"
          >
            <img src="@/assets/icon/global-perspective.png" alt="" />
          </button>
        </div>
        <div class="twin-side">
          <Border3 class="asset-capacity-card" name="机柜空间利用率"
            ><SpaceUsageCard :snapshot="snapshot" :capacity="capacity" /></Border3
          ><Border3 name="资产位置分布"
            ><AssetLocationsCard
              :zone-counts="zoneCounts"
              :snapshot="snapshot"
              :source-text="sourceText"
              @select-zone="assetSelectZone" /></Border3
          ><Border3 class="asset-detail-card" name="机柜容量排行"
            ><CabinetCapacityCard
              :snapshot="snapshot"
              :loading="platform.loading"
              :error="platform.error"
          /></Border3>
        </div>
      </div>
    </section>
  </div>
</template>
<script setup>
import Border3 from '@/components/Border3/index.vue';
import {
  AssetTypesCard,
  SurveillanceCard,
  AssetStatusCard,
  SpaceUsageCard,
  AssetLocationsCard,
  CabinetCapacityCard,
} from './components';
import RoomScene from '../../scene/RoomScene.vue';
import { useAssetDashboard } from './composables/useAssetDashboard';

defineProps({ mode: String });
const {
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
} = useAssetDashboard();
</script>
<style src="./styles/assets.css"></style>
