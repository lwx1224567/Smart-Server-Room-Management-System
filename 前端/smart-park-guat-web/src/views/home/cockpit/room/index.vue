<template>
  <template v-if="isRoomOverview">
    <div class="home-side home-side--left" style="--card-count: 3">
      <Border3 name="机房基础信息" class="room-basic-card"
        ><RoomInfoCard :room="platform.room" platform
      /></Border3>
      <Border3 name="机房设施" class="room-facilities-card"
        ><FacilityListCard
          :groups="deviceGroups"
          :selected-id="selectedDevice"
          @select="selectFacility"
      /></Border3>
    </div>
    <div class="home-side home-side--right" style="--card-count: 3">
      <Border3 name="环境监控" class="environment-card"><EnvironmentCard /></Border3>
      <Border3 name="近 7 天能耗"><WeeklyEnergyCard /></Border3>
      <Border3 name="机房事件列表"><RoomEventsCard /></Border3>
    </div>
  </template>
  <template v-else>
    <div
      v-for="side in ['left', 'right'] as const"
      :key="side"
      :class="['home-side', 'home-side--' + side]"
      :style="{ '--card-count': cards[side].length }"
    >
      <Border3 v-for="name in cards[side]" :key="name" :name="name"><EmptyContent /></Border3>
    </div>
  </template>
</template>

<script setup lang="ts">
import EmptyContent from '../../components/EmptyContent.vue';
import Border3 from '@/components/Border3/index.vue';
import type { HomeDashboard } from '../../composables/useHomeDashboard';
import {
  RoomInfoCard,
  FacilityListCard,
  EnvironmentCard,
  WeeklyEnergyCard,
  RoomEventsCard,
} from './components';
const props = defineProps<{ dashboard: HomeDashboard }>();
const { cards, isRoomOverview, platform, deviceGroups, selectedDevice, selectFacility } =
  props.dashboard;
</script>

<style scoped lang="scss" src="../../styles/dashboard.scss"></style>
