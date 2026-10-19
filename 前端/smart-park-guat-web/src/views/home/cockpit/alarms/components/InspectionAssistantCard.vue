<template>
  <InspectionAssistant :assistant="assistant" @inspect="emit('inspect', $event)" />
</template>

<script setup lang="ts">
import InspectionAssistant from '../../../assistant/InspectionAssistant.vue';
import type { AgentAssistant } from '../../../assistant/agent-assistant';
import type { CabinetRef } from '@/types/platform-identity';
defineProps<{ assistant: AgentAssistant }>();
const emit = defineEmits<{ inspect: [ref: CabinetRef] }>();
</script>
