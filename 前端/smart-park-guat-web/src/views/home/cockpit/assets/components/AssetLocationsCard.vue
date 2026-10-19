<template>
  <div class="twin-body">
    <div
      v-for="zone in zoneCounts"
      :key="zone.id"
      class="asset-location"
      @click="emit('select-zone', zone.id)"
    >
      <span>{{ zone.code }} 区机柜</span><b>{{ zone.cabinets }} 柜 · {{ zone.devices }} 台</b>
    </div>
    <span v-if="!snapshot">{{ sourceText }}</span>
  </div>
</template>

<script setup>
defineProps(['zoneCounts', 'snapshot', 'sourceText']);
const emit = defineEmits(['select-zone']);
</script>
