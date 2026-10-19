<template>
  <OperationsCard
    kind="设备状态"
    v-bind="props"
    @control="(key, value) => emit('control', key, value)"
  />
</template>

<script setup lang="ts">
import OperationsCard from '../shared/OperationsCard.vue';
import type { CabinetOperations } from '../shared/OperationsCard.vue';
const props = defineProps<{
  device: {
    id: string;
    name: string;
    zone: string;
    code?: string;
    sceneModelKey?: string;
    roomName?: string;
    location?: string;
  };
  state: CabinetOperations;
  platform?: boolean;
}>();
const emit = defineEmits<{ control: [key: string, value: boolean] }>();
</script>
