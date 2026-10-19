<template><canvas ref="canvasRef" class="title-sequence" aria-hidden="true"></canvas></template>

<script lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue';

const frameUrls = Object.entries(
  import.meta.glob('./bg/*.png', {
    eager: true,
    import: 'default',
    query: '?url',
  }),
)
  .sort(([a], [b]) => Number(a.match(/(\d+)\.png$/)?.[1]) - Number(b.match(/(\d+)\.png$/)?.[1]))
  .map(([, url]) => url as string);

// All cards share the decoded PNG frames.
let framesPromise: Promise<HTMLImageElement[]> | undefined;
function loadFrames() {
  return (framesPromise ??= Promise.all(
    frameUrls.map(
      (url) =>
        new Promise<HTMLImageElement>((resolve, reject) => {
          const image = new Image();
          image.onload = () => resolve(image);
          image.onerror = reject;
          image.src = url;
        }),
    ),
  ));
}
</script>

<script setup lang="ts">
const canvasRef = ref<HTMLCanvasElement>();
let observer: ResizeObserver | undefined;
let timer: ReturnType<typeof setInterval> | undefined;
let disposed = false;
onMounted(async () => {
  const canvas = canvasRef.value;
  const ctx = canvas?.getContext('2d');
  if (!canvas || !ctx) return;
  let frames: HTMLImageElement[] = [];
  let index = 0;
  const draw = () => {
    const frame = frames[index];
    if (!frame) return;
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.drawImage(frame, 0, 0, canvas.width, canvas.height);
  };
  const resize = () => {
    const ratio = window.devicePixelRatio || 1;
    canvas.width = Math.round(canvas.clientWidth * ratio);
    canvas.height = Math.round(canvas.clientHeight * ratio);
    draw();
  };
  observer = new ResizeObserver(resize);
  observer.observe(canvas);
  resize();
  try {
    frames = await loadFrames();
    if (disposed) return;
    draw();
    if (!window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      timer = setInterval(() => {
        index = (index + 1) % frames.length;
        draw();
      }, 50);
    }
  } catch {
    // The title remains usable if decorative frames cannot be loaded.
  }
});
onBeforeUnmount(() => {
  disposed = true;
  observer?.disconnect();
  if (timer !== undefined) clearInterval(timer);
});
</script>

<style scoped>
.title-sequence {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  z-index: -1;
  pointer-events: none;
}
</style>
