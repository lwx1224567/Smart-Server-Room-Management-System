<template>
  <div class="menu-container">
    <MenuItem v-for="item in menuData" :items="item" />
  </div>
</template>
<script setup>
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import { useMenuStore } from '@/stores/menu';
import MenuItem from './src/menu-item.vue';
const menuStore = useMenuStore();
const route = useRoute();
const menuData = computed(() =>
  menuStore.menuList.filter(
    (item) => !['user', 'maintenance'].includes(route.name) || item.path !== '/home',
  ),
);
</script>

<style scoped lang="scss">
.menu-container {
  width: 100%;
  padding: 0 10px 15px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
</style>
