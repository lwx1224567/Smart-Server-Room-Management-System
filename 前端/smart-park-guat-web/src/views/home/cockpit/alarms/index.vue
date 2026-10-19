<template>
  <div class="home-side home-side--left" style="--card-count: 3">
    <Border3 name="告警概览" class="alarm-statistics-card"
      ><div class="room-alarm-overview"><AlarmOverviewCard /></div
    ></Border3>
    <Border3 name="告警分析" class="alarm-list-card"
      ><AlarmAnalysisCard @locate="locateAlarm" @create="createAlarmOrder"
    /></Border3>
  </div>
  <div class="home-side home-side--right" style="--card-count: 1">
    <Border3 name="小维·巡检助手"
      ><InspectionAssistantCard :assistant="assistant" @inspect="inspectFromAssistant"
    /></Border3>
  </div>
</template>

<script setup lang="ts">
import Border3 from '@/components/Border3/index.vue';
import type { HomeDashboard } from '../../composables/useHomeDashboard';
import { AlarmOverviewCard, AlarmAnalysisCard, InspectionAssistantCard } from './components';
const props = defineProps<{ dashboard: HomeDashboard }>();
const { locateAlarm, createAlarmOrder, assistant, inspectFromAssistant } = props.dashboard;
</script>

<style scoped lang="scss" src="../../styles/dashboard.scss"></style>
