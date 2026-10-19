<template>
  <el-container class="main-container" :class="{ 'cockpit-layout': route.name === 'home' }">
    <el-header>
      <Top name="智慧机房管控系统" />
      <Menu v-if="route.name !== 'home' && route.name !== 'maintenance'" />
    </el-header>
    <el-main>
      <RouterView v-slot="{ Component }">
        <component
          :is="Component"
          ref="dashboard"
          v-if="route.name === 'home'"
          :active-business="activeBusiness"
          @back-home="setBusiness('operations')"
        />
        <component :is="Component" v-else />
      </RouterView>
    </el-main>
    <footer v-if="route.name === 'home'" class="action-bottom">
      <BusinessMenu :model-value="activeBusiness" @select="selectBusiness" />
    </footer>
  </el-container>
</template>
<script setup lang="ts">
import Top from './components/top/index.vue';
import Menu from './components/menu/index.vue';
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import BusinessMenu from '@/components/BusinessMenu/index.vue';
import type { BusinessMenuItem } from '@/components/BusinessMenu/menu';

const route = useRoute(),
  router = useRouter();
const businessKeys = ['operations', 'energy', 'alarm', 'assets', 'topology'];
const activeBusiness = computed(() =>
  typeof route.query.business === 'string' && businessKeys.includes(route.query.business)
    ? route.query.business
    : 'operations',
);
const dashboard = ref<{ resetOverview?: () => void }>();
function setBusiness(key: string) {
  if (key !== activeBusiness.value)
    void router.push({ path: '/home', query: { ...route.query, business: key } });
}

function selectBusiness(item: BusinessMenuItem) {
  if (
    route.name === 'home' &&
    ['operations', 'energy', 'alarm', 'assets', 'topology'].includes(item.key)
  ) {
    if (item.key === 'operations') dashboard.value?.resetOverview?.();
    setBusiness(item.key);
  }
}
</script>

<style scoped lang="scss">
.main-container {
  position: relative;
  width: 100%;
  height: 100%;
  background: url('@/assets/layouts/layout-bg.png') no-repeat center;
  background-size: 100% 100%;
}
.el-header,
.el-main {
  margin: 0;
  padding: 0;
}
.action-bottom {
  flex: 0 0 auto;
  width: 50%;
  margin: 0 auto;
  z-index: 10;
}

@media (max-width: 1200px) {
  .action-bottom {
    width: 75%;
  }
}

@media (max-width: 640px) {
  .action-bottom {
    width: 100%;
  }
}
.el-header {
  height: auto;
}
.el-main {
  padding: 0 15px 15px;
  position: relative;
}

.cockpit-layout {
  background:
    radial-gradient(ellipse at 50% 55%, rgba(13, 100, 179, 0.32), transparent 58%),
    linear-gradient(rgba(32, 128, 211, 0.045) 1px, transparent 1px),
    linear-gradient(90deg, rgba(32, 128, 211, 0.045) 1px, transparent 1px),
    linear-gradient(180deg, #071a3b 0%, #04132c 58%, #08234a 100%);
  background-size:
    auto,
    48px 48px,
    48px 48px,
    auto;

  .el-header {
    position: relative;
    z-index: 5;
  }
  .el-main {
    position: absolute;
    inset: 0;
    padding: 0;
  }

  .action-bottom {
    position: absolute;
    bottom: 0;
    left: 50%;
    transform: translateX(-50%);
  }
}
</style>
