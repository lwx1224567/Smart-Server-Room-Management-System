<template>
  <div class="twin-body">
    <div class="status-row">
      <span>正常</span><b>{{ runtimeCounts.normal }}</b>
      <div class="status-track">
        <i :style="{ width: statusPercent(runtimeCounts.normal), background: '#3ce3a3' }"></i>
      </div>
    </div>
    <div class="status-row">
      <span>异常</span><b>{{ runtimeCounts.abnormal }}</b>
      <div class="status-track">
        <i :style="{ width: statusPercent(runtimeCounts.abnormal), background: '#ff5b67' }"></i>
      </div>
    </div>
    <div class="status-row">
      <span>未采集</span><b>{{ runtimeCounts.unknown }}</b>
      <div class="status-track">
        <i :style="{ width: statusPercent(runtimeCounts.unknown), background: '#7c91a2' }"></i>
      </div>
    </div>
    <small class="tiny-note"></small>
  </div>
</template>

<script setup>
defineProps(['runtimeCounts', 'statusPercent']);
</script>
