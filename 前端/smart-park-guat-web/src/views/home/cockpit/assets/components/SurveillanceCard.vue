<template>
  <VideoSurveillance v-if="mode === 'assets'" v-model:camera-id="selectedCameraId" />
</template>

<script setup>
import VideoSurveillance from '../../../components/VideoSurveillance.vue';
defineProps(['mode']);
const selectedCameraId = defineModel('cameraId');
</script>
