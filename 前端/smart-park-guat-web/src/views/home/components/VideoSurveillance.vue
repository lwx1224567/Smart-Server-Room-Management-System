<template>
  <div class="surveillance">
    <div class="video-display">
      <video
        v-if="playUrl"
        :key="playUrl"
        class="video"
        ref="player"
        controls
        autoplay
        muted
        playsinline
        :loop="!live"
        :poster="preview"
        @error="videoError = true"
      >
        <source v-if="protocol === 'MP4'" :src="playUrl" type="video/mp4" />
      </video>
      <div v-else class="preview" aria-hidden="true"><img :src="preview" alt="" /></div>
      <div class="camera-info">
        <label class="camera-selector">
          <span class="sr-only">选择摄像头</span>
          <select v-model="selectedCameraId">
            <option v-for="item in cameras" :key="item.id" :value="item.id">{{ item.name }}</option>
          </select>
        </label>
        <span class="camera-status"
          ><i :class="status" />{{ playUrl && !videoError ? '播放中' : statusLabels[status] }}</span
        >
      </div>
      <div v-if="live" class="demo-badge">实时监控</div>
      <div v-if="sourceError && !playUrl" class="preview-label preview-note">{{ sourceError }}</div>
      <div v-else-if="!playUrl" class="preview-label preview-note">视频未配置</div>
      <div v-if="videoError" class="preview-label" role="status">
        视频加载失败 <button @click="retryVideo">重试</button>
      </div>
      <button
        type="button"
        class="control-btn prev"
        aria-label="上一台摄像头"
        @click="navigate(-1)"
      >
        ‹
      </button>
      <button type="button" class="control-btn next" aria-label="下一台摄像头" @click="navigate(1)">
        ›
      </button>
    </div>
    <div class="controls">
      <span class="camera-count">{{ cameraIndex + 1 }} / {{ cameras.length }}</span>
      <button
        type="button"
        class="control-btn"
        :aria-pressed="isAutoPlaying"
        @click="isAutoPlaying = !isAutoPlaying"
      >
        {{ isAutoPlaying ? '暂停轮播' : '开始轮播' }}
      </button>
      <span class="interval">5 秒切换</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import Hls from 'hls.js';
import preview from '@/assets/monitoring/视频画面.png';

import { cameras, type CameraStatus } from '../scene/cameras';
import { useMonitoringStore } from '@/stores/monitoring';
import { integrationApi } from '@/api/platform/integrations';
const monitor = useMonitoringStore();
const remoteUrl = ref(''),
  live = ref(false),
  protocol = ref<'MP4' | 'HLS'>('MP4'),
  sourceError = ref('');
const playUrl = computed(() => remoteUrl.value || (live.value ? '' : camera.value.url));
let sourceTicket = 0;
async function loadVideo() {
  const ticket = ++sourceTicket;
  remoteUrl.value = '';
  live.value = false;
  sourceError.value = '';
  protocol.value = 'MP4';
  videoError.value = false;
  const room = monitor.snapshot?.roomId,
    facility = monitor.snapshot?.facilities.find(
      (f) => f.type === 'CAMERA' && f.sceneModelKey === camera.value.id,
    );
  if (!room || !facility) return;
  try {
    const source = await integrationApi.video(room, facility.id);
    if (ticket !== sourceTicket) return;
    remoteUrl.value = source.url;
    live.value = source.live;
    protocol.value = source.protocol;
  } catch (e) {
    if (ticket === sourceTicket) sourceError.value = (e as Error).message;
  }
}
const selectedCameraId = defineModel<string>('cameraId', { default: 'zone-a' });
const cameraIndex = computed(() =>
  Math.max(
    0,
    cameras.findIndex((item) => item.id === selectedCameraId.value),
  ),
);
const camera = computed(() => cameras[cameraIndex.value]!);
const videoError = ref(false);
const player = ref<HTMLVideoElement>();
let hls: Hls | undefined;
watch([playUrl, protocol], async ([url, format]) => {
  hls?.destroy();
  hls = undefined;
  await nextTick();
  if (url !== playUrl.value || format !== protocol.value || !player.value || format !== 'HLS')
    return;
  if (player.value.canPlayType('application/vnd.apple.mpegurl')) {
    player.value.src = url;
    return;
  }
  if (!Hls.isSupported()) {
    sourceError.value = '当前浏览器不支持此视频格式';
    return;
  }
  const instance = new Hls();
  hls = instance;
  instance.on(Hls.Events.ERROR, (_event, data) => {
    if (data.fatal && hls === instance) videoError.value = true;
  });
  instance.loadSource(url);
  instance.attachMedia(player.value);
});
function retryVideo() {
  videoError.value = false;
  void loadVideo();
  player.value?.load();
}
const status = computed(() => (videoError.value ? 'error' : camera.value.status));
const statusLabels: Record<CameraStatus, string> = {
  online: '在线',
  offline: '离线',
  error: '故障',
};
const isAutoPlaying = ref(false);
let timer: ReturnType<typeof setInterval> | undefined;

function navigate(direction: number) {
  selectedCameraId.value =
    cameras[(cameraIndex.value + direction + cameras.length) % cameras.length]!.id;
}
watch(
  [selectedCameraId, () => monitor.snapshot?.roomId, () => monitor.snapshot?.facilities],
  () => {
    void loadVideo();
  },
  { immediate: true },
);
watch(isAutoPlaying, (playing) => {
  clearInterval(timer);
  timer = playing ? setInterval(() => navigate(1), 5000) : undefined;
});
onBeforeUnmount(() => {
  sourceTicket++;
  hls?.destroy();
  clearInterval(timer);
});
</script>

<style scoped lang="scss">
.demo-badge {
  position: absolute;
  right: 6px;
  bottom: 34px;
  padding: 4px 7px;
  color: #ffdb69;
  background: #092a49e8;
  border: 1px solid #846d38;
  font-size: 10px;
  pointer-events: none;
}
.surveillance {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  min-height: 0;
}
.video-display {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: #000;
  border: 1px solid rgba(98, 147, 212, 0.3);
}
.video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
// 原图是完整卡片截图，裁出中央画面，避免重复显示截图中的标题和控件。
.preview {
  position: absolute;
  inset: 0;
  overflow: hidden;
}
.preview img {
  position: absolute;
  width: 160%;
  height: 160%;
  left: -30%;
  top: -35%;
  object-fit: cover;
  opacity: 0.65;
}
.camera-info {
  position: absolute;
  top: 6px;
  left: 6px;
  display: flex;
  align-items: center;
  gap: 4px;
  max-width: calc(100% - 26px);
  padding: 3px 4px;
  border: 1px solid rgba(98, 147, 212, 0.3);
  border-radius: 4px;
  background: rgba(22, 48, 82, 0.8);
  backdrop-filter: blur(8px);
}
.camera-selector {
  min-width: 0;
  width: 90px;
}
select {
  width: 100%;
  padding: 1px 0;
  border: 0;
  background: transparent;
  color: #fff;
  font: inherit;
  font-size: 11px;
  cursor: pointer;
}
option {
  background: #142033;
  color: #fff;
}
.camera-status {
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
  font-size: 11px;
  color: #fff;
}
.camera-status i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ff9800;
  box-shadow: 0 0 6px #ff980080;
}
.camera-status .online {
  background: #4caf50;
  box-shadow: 0 0 6px #4caf5080;
}
.camera-status .error {
  background: #f44336;
  box-shadow: 0 0 6px #f4433680;
}
.preview-label.preview-note {
  top: 35px;
  bottom: auto;
}
.preview-label {
  position: absolute;
  bottom: 8px;
  left: 50%;
  transform: translateX(-50%);
  padding: 4px 8px;
  background: rgba(0, 0, 0, 0.65);
  color: #d4dfeb;
  font-size: 11px;
  white-space: nowrap;
}
.controls {
  position: absolute;
  z-index: 2;
  bottom: 8px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 4px 8px;
  border-radius: 4px;
  background: rgba(22, 48, 82, 0.65);
  backdrop-filter: blur(8px);
  white-space: nowrap;
}
.camera-count,
.interval {
  color: #91abc7;
  font-size: 11px;
}
.control-btn {
  border: 1px solid rgba(98, 147, 212, 0.4);
  border-radius: 4px;
  background: rgba(22, 48, 82, 0.8);
  color: #fff;
  padding: 5px 12px;
  font-size: 12px;
  cursor: pointer;
  backdrop-filter: blur(8px);
}
.control-btn:hover {
  background: #163052;
  border-color: #6293d4;
}
.control-btn:focus-visible,
select:focus-visible {
  outline: 2px solid #68e3ff;
  outline-offset: 2px;
}
.prev,
.next {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  padding: 0 8px;
  font-size: 30px;
  line-height: 36px;
}
.prev {
  left: 8px;
}
.next {
  right: 8px;
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
</style>
