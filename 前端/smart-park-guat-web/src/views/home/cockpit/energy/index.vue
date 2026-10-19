<template>
  <div class="home-side home-side--left" style="--card-count: 3">
    <Border3 name="实时能耗概览" class="energy-overview-card"><EnergyOverviewCard /></Border3>
    <Border3 name="能耗分类统计"><EnergyCategoryCard /></Border3>
    <Border3 name="用电异常告警" class="energy-alerts-card"><ElectricalAlarmsCard /></Border3>
  </div>
  <div class="home-side home-side--right" style="--card-count: 3">
    <Border3 name="PUE 指标"><PueCard /></Border3>
    <Border3 name="机柜能耗 TOP5"><CabinetEnergyRankingCard /></Border3>
    <Border3 name="机房能耗趋势分析"><EnergyTrendCard /></Border3>
  </div>
</template>

<script setup lang="ts">
import Border3 from '@/components/Border3/index.vue';
import type { HomeDashboard } from '../../composables/useHomeDashboard';
import {
  EnergyOverviewCard,
  EnergyCategoryCard,
  ElectricalAlarmsCard,
  PueCard,
  CabinetEnergyRankingCard,
  EnergyTrendCard,
} from './components';
defineProps<{ dashboard: HomeDashboard }>();
</script>

<style scoped lang="scss" src="../../styles/dashboard.scss"></style>
