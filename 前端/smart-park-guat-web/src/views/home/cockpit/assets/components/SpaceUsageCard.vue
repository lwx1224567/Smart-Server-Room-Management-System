<template>
  <div class="twin-body">
    <div class="twin-metrics">
      <div>
        已登记机柜<b>{{ snapshot?.cabinets.length ?? '--' }}</b>
      </div>
      <div>
        已用 U 位<b>{{ capacity.used ?? '未登记' }}</b>
      </div>
      <div>
        可用 U 位<b>{{ capacity.free ?? '未登记' }}</b>
      </div>
      <div>
        平均利用率<b>{{ capacity.percent ?? '--' }}</b>
      </div>
    </div>
    <div class="status-track" style="margin-top: 14px">
      <i
        :style="{
          width: capacity.percent ?? '0%',
          background: 'linear-gradient(90deg,#168ab8,#42d9ff)',
        }"
      ></i>
    </div>
  </div>
</template>

<script setup>
defineProps(['snapshot', 'capacity']);
</script>
