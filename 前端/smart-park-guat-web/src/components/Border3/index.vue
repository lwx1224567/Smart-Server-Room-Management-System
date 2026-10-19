<template>
  <section class="border-card" :style="animationStyle">
    <div ref="revealRef" class="border-card__reveal" aria-hidden="true"></div>
    <div ref="innerRef" class="border-card__inner">
      <header class="border-card__title">
        <TitleSequence />
        <img class="border-card__arrow" src="./assets/icon_right.png" alt="" />
        <h2 :aria-label="name">
          <span
            v-for="(char, index) in Array.from(name)"
            :key="index"
            aria-hidden="true"
            :style="{ '--letter-delay': `${delay + duration + 0.1 + index * 0.1}s` }"
            >{{ char === ' ' ? '\u00a0' : char }}</span
          >
        </h2>
        <div v-if="$slots.action" class="border-card__action"><slot name="action" /></div>
      </header>
      <div class="border-card__content"><slot /></div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue';
import gsap from 'gsap';
import TitleSequence from './sequence/index.vue';

const props = withDefaults(defineProps<{ name?: string; delay?: number; duration?: number }>(), {
  name: '',
  delay: 0,
  duration: 0.5,
});
const animationStyle = computed(() => ({
  '--card-delay': `${props.delay}s`,
  '--card-duration': `${props.duration}s`,
  '--content-delay': `${props.delay + props.duration + 0.1}s`,
}));
const revealRef = ref<HTMLElement>();
const innerRef = ref<HTMLElement>();
let entrance: gsap.core.Timeline | undefined;
onMounted(() => {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  entrance = gsap
    .timeline()
    .to(revealRef.value!, { scaleY: 1, duration: props.duration, delay: props.delay, ease: 'none' })
    .set(innerRef.value!, { visibility: 'visible' }, '+=0.1')
    .set(revealRef.value!, { visibility: 'hidden' });
});
onBeforeUnmount(() => entrance?.kill());
</script>

<style scoped lang="scss">
.border-card {
  position: relative;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  color: #c6e2ff;
}
.border-card__reveal {
  position: absolute;
  inset: 0;
  background: linear-gradient(to bottom, rgba(6, 45, 108, 0.3), rgba(6, 45, 108, 0.6));
  transform: scaleY(0);
  transform-origin: top;
  pointer-events: none;
}
.border-card__inner {
  display: flex;
  flex-direction: column;
  height: 100%;
  border: 1px solid rgba(1, 11, 39, 0.7);
  background: rgba(5, 20, 63, 0.5);
  visibility: hidden;
}
.border-card__title {
  position: relative;
  display: flex;
  align-items: center;
  flex-shrink: 0;
  height: 30px;
  isolation: isolate;
  h2 {
    display: flex;
    margin: 0 10px 0 0;
    font-size: 16px;
    font-weight: bold;
    white-space: nowrap;
    span {
      opacity: 0;
      transform: translateY(1em);
      background: linear-gradient(180deg, #fff, #88b8ff);
      background-clip: text;
      -webkit-text-fill-color: transparent;
      animation: letter-appear 0.5s var(--letter-delay) forwards;
    }
  }
}
.border-card__arrow {
  width: 15px;
  margin: 0 7.5px;
}
.border-card__action {
  margin-left: auto;
  padding-right: 10px;
  display: flex;
  align-items: center;
  :deep(.el-button) {
    height: 26px;
  }
}
.border-card__content {
  flex: 1;
  min-height: 0;
  padding: 10px;
  overflow: auto;
  background: url('./assets/itembg.png') no-repeat;
  background-size: 100% 100%;
}
@keyframes letter-appear {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
@media (prefers-reduced-motion: reduce) {
  .border-card__reveal {
    display: none;
  }
  .border-card__inner {
    animation: none;
    visibility: visible;
  }
  .border-card__title h2 span {
    animation: none;
    opacity: 1;
    transform: none;
  }
}
</style>
