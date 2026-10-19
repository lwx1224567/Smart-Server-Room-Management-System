<template>
  <nav class="business-menu" aria-label="园区业务菜单">
    <div class="menu-box" :style="{ '--menu-columns': items.length }">
      <button
        v-for="item in items"
        :key="item.key"
        type="button"
        class="item"
        :class="{ active: modelValue === item.key }"
        :aria-pressed="modelValue === item.key"
        @click="selectItem(item)"
      >
        <span class="menu-content">
          <span class="item-icon"><img :src="item.icon" alt="" draggable="false" /></span>
        </span>
        <span class="name">{{ item.name }}</span>
      </button>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { defaultBusinessMenuItems, type BusinessMenuItem } from './menu';

withDefaults(defineProps<{ items?: BusinessMenuItem[]; modelValue?: string }>(), {
  items: () => defaultBusinessMenuItems,
  modelValue: '',
});

const emit = defineEmits<{
  'update:modelValue': [key: string];
  select: [item: BusinessMenuItem];
}>();

function selectItem(item: BusinessMenuItem) {
  emit('update:modelValue', item.key);
  emit('select', item);
}
</script>

<style scoped lang="scss">
.business-menu {
  width: 100%;
  background: url('@/assets/business-menu/business-menu-bg.png') no-repeat center bottom;
  background-size: 100% auto;
  user-select: none;
}

.menu-box {
  display: grid;
  grid-template-columns: repeat(var(--menu-columns), minmax(0, 1fr));
  align-items: end;
  padding: 0 clamp(12px, 3vw, 58px);
  gap: clamp(8px, 2vw, 38px);
}

.item {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 0 0 10px;
  border: 0;
  background: transparent;
  color: #d8effc;
  font: inherit;
  cursor: pointer;
  transition: transform 0.5s;

  &:hover,
  &:focus-visible {
    transform: translateY(-3px);
  }

  &:focus-visible {
    outline: 2px solid #80dfff;
    outline-offset: 3px;
    border-radius: 6px;
  }

  &:hover .menu-content,
  &:focus-visible .menu-content,
  &.active .menu-content {
    background-image: url('@/assets/business-menu/business-menu-item-bg-active.png');
  }

  &.active .name {
    text-shadow:
      0 0 1px #d8effc,
      0 0 2px #d8effc;
  }
}

.menu-content {
  display: flex;
  justify-content: center;
  width: 100%;
  max-width: 110px;
  padding-bottom: clamp(8px, 1.2vw, 20px);
  background: url('@/assets/business-menu/business-menu-item-bg.png') no-repeat center bottom;
  background-size: 100% auto;
}

.item-icon {
  box-sizing: border-box;
  width: 100%;
  aspect-ratio: 1;
  padding: clamp(3px, 0.5vw, 10px);

  img {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: contain;
    filter: brightness(0) invert(1);
    opacity: 0.94;
  }
}

.name {
  font-size: clamp(12px, 1.4vw, 24px);
  font-weight: bold;
  white-space: nowrap;
}

@media (prefers-reduced-motion: reduce) {
  .item {
    transition: none;
  }
}
</style>
