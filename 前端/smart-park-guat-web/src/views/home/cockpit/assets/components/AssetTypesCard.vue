<template>
  <div class="twin-body">
    <div class="asset-type-chart">
      <div
        class="asset-pie"
        :data-total="snapshot ? `${assetTotal} 台` : '--'"
        :style="{ background: pieBackground }"
      ></div>
      <div class="asset-legend">
        <div v-for="item in visibleTypes" :key="item.type">
          <i :style="{ background: item.color }"></i>{{ item.name }}
          {{ snapshot ? `${item.count ?? '--'} 台` : '--' }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps(['snapshot', 'assetTotal', 'pieBackground', 'typeCounts']);
const visibleTypes = computed(() => (props.typeCounts ?? []).filter((item) => item.count > 0));
</script>
