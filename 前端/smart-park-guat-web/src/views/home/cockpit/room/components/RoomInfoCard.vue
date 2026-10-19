<template>
  <dl class="room-basic-info">
    <div v-for="item in displayedInfo" :key="item.label" class="room-basic-info__row">
      <dt>{{ item.label }}</dt>
      <dd>{{ item.value }}</dd>
    </div>
  </dl>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { Room } from '@/types/platform';
const props = defineProps<{ room?: Room; platform?: boolean }>();
const displayedInfo = computed(() =>
  props.platform
    ? [
        { label: '园区名称', value: props.room?.parkName ?? '--' },
        { label: '机房名称', value: props.room?.name ?? '--' },
        { label: '位置', value: props.room?.location ?? '--' },
        { label: '负责人', value: props.room?.managerDisplayName ?? '--' },
      ]
    : basicInfo,
);
const basicInfo = [
  { label: '园区名称', value: '智慧园区' },
  { label: '机房名称', value: '研发大厦 1F 机房' },
  { label: '位置', value: '研发大厦 1F' },
  { label: '负责人', value: '张三' },
];
</script>

<style scoped lang="scss">
.room-basic-info {
  display: grid;
  grid-template-rows: repeat(4, minmax(0, 1fr));
  height: 100%;
  box-sizing: border-box;
  margin: 0;
  padding: 0 8px;
  color: #dff7ff;
  font-size: clamp(14px, 1vw, 20px);
  line-height: 1.5;
}

.room-basic-info__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 0;
  border-bottom: 1px dashed rgba(100, 188, 220, 0.12);

  dt {
    flex-shrink: 0;
  }

  dd {
    min-width: 0;
    margin: 0;
    color: #fff;
    font-size: clamp(16px, 1.15vw, 22px);
    font-weight: 700;
    overflow-wrap: anywhere;
    text-align: right;
  }
}
</style>
