<template>
  <div class="pageTop wow fadeInDown">
    <sequence :fileLength="74" :IntervalTime="50" />
    <div class="title">
      <span :title="name">{{ name }}</span>
    </div>
    <div class="left">
      <span>{{ currentDate }}</span>
      <span>{{ currentTime }}</span>
    </div>
    <div class="right">
      <button
        class="weather weather-button"
        type="button"
        :title="
          weather.available
            ? `Open-Meteo · 更新于 ${weather.updatedAt} · 点击刷新`
            : '点击重试天气查询'
        "
        @click="loadWeather"
      >
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.3"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <path d="M12 3V1.5M5.6 5.6 4.5 4.5M3 12H1.5M18.4 5.6l1.1-1.1" />
          <path d="M7 12a5 5 0 0 1 9.7-1.7" />
          <path d="M7.5 20a3.5 3.5 0 0 1-.4-7 5 5 0 0 1 9.4-1.2 4.2 4.2 0 1 1 1.3 8.2Z" />
        </svg>
        <span
          >{{ weather.city || '上海' }}
          {{
            weather.available
              ? `${weatherText} ${weather.temperature}°C`
              : weatherLoading
                ? '加载中…'
                : '天气暂不可用'
          }}</span
        >
      </button>
      <span class="action-divider" aria-hidden="true"></span>
      <button
        class="top-action"
        type="button"
        aria-label="设置"
        title="设置"
        @click="settingsOpen = true"
      >
        <el-badge :value="unread" :hidden="!unread"><Setting /></el-badge>
      </button>
      <span class="action-divider" aria-hidden="true"></span>
      <button
        class="top-action"
        type="button"
        aria-label="退出登录"
        title="退出登录"
        :disabled="leaving"
        @click="logout"
      >
        <SwitchButton />
      </button>
    </div>
  </div>
  <ConsoleSettings
    ref="settings"
    v-model="settingsOpen"
    :unread="unread"
    @count="unread = $event"
  />
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import ConsoleSettings from '@/components/ConsoleSettings.vue';
import { getWeather } from '@/services/weather';
import { Setting, SwitchButton } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { signOut } from '@/services/console';
import dayjs from 'dayjs';
import localeData from 'dayjs/plugin/localeData';
import updateLocale from 'dayjs/plugin/updateLocale';
import 'dayjs/locale/zh-cn';
import sequence from './sequence/index.vue';
dayjs.locale('zh-cn');

defineProps<{
  name: { type: String; default: string }['default'];
}>();

const currentTime = ref(dayjs().format('HH:mm:ss'));
const settings = ref<InstanceType<typeof ConsoleSettings>>();
const route = useRoute(),
  router = useRouter();
const leaving = ref(false);
async function logout() {
  if (leaving.value) return;
  leaving.value = true;
  try {
    await signOut();
    await router.replace('/login');
  } catch (e) {
    ElMessage.error(`退出失败：${(e as Error).message}，请重试`);
  } finally {
    leaving.value = false;
  }
}
function openRequestedOrders() {
  if (route.name === 'maintenance') return;
  if (!settings.value || (!route.query.order && route.query.orders !== 'all')) return;
  settings.value.openOrders();
  const query = { ...route.query };
  delete query.order;
  delete query.orders;
  void router.replace({ path: route.path, query });
}
watch(() => route.query, openRequestedOrders);
const settingsOpen = ref(false),
  unread = ref(0);
const weather = ref<any>({ city: '上海', available: false }),
  weatherLoading = ref(false);
const weatherText = computed(() => {
  const code = Number(weather.value.code);
  return code === 0
    ? '晴'
    : code <= 3
      ? '多云'
      : code <= 48
        ? '雾'
        : code <= 67
          ? '雨'
          : code <= 77
            ? '雪'
            : code <= 82
              ? '阵雨'
              : code <= 86
                ? '阵雪'
                : '雷雨';
});
async function loadWeather() {
  if (weatherLoading.value) return;
  weatherLoading.value = true;
  try {
    weather.value = await getWeather();
  } catch {
    weather.value = { city: '上海', available: false };
  } finally {
    weatherLoading.value = false;
  }
}
let refreshTimer: ReturnType<typeof setInterval> | undefined;
const currentDate = ref(dayjs().format('YYYY/MM/DD dddd'));
const timer = ref<string | number | NodeJS.Timeout | undefined>(undefined);

function updateTime() {
  currentTime.value = dayjs().format('HH:mm:ss');
  currentDate.value = dayjs().format('YYYY/MM/DD dddd');
}

onMounted(() => {
  openRequestedOrders();
  updateTime();
  timer.value = setInterval(updateTime, 1000);
  void loadWeather();
  void settings.value?.loadNotices();
  refreshTimer = setInterval(() => {
    void loadWeather();
    void settings.value?.loadNotices();
  }, 60000);
});

onBeforeUnmount(() => {
  clearInterval(timer.value);
  clearInterval(refreshTimer);
});

dayjs.extend(localeData);
dayjs.extend(updateLocale);

dayjs.updateLocale('zh-cn', {
  weekdays: ['周日', '周一', '周二', '周三', '周四', '周五', '周六'],
});
</script>

<style lang="scss" scoped>
:deep(.headers) {
  pointer-events: none;
}
.weather-button {
  border: 0;
  padding: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
}
.pageTop {
  display: flex;
  position: relative;
  z-index: 101;
  flex-flow: row nowrap;
  flex-shrink: 0;
  align-items: flex-start;
  width: 100%;
  height: 100px;
  color: #fff;
  place-content: flex-start space-between;

  .title {
    position: absolute;
    top: 10px;
    left: 50%;
    transform: translateX(-50%);

    span {
      display: inline-block;
      background: linear-gradient(180deg, #f2fcff 0%, #69cbf5 100%);
      background-clip: text;
      color: transparent;
      font-family: 'Franklin Gothic Medium', 'Arial Narrow', Arial, sans-serif;
      font-size: 36px;
      font-style: normal;
      font-weight: 400;
      font-weight: bold;
      letter-spacing: 3px;
      text-shadow: 0 0 5px rgb(255 255 255 / 30%);
      text-transform: none;
      white-space: nowrap;
      -webkit-text-fill-color: transparent;

      &::before {
        content: attr(title);
        position: absolute;
        z-index: -1;
        top: 0;
        left: 0;
        color: transparent;
        text-shadow: 0 4px 6px rgb(0 0 0 / 51%);
      }
    }
  }

  .left,
  .right {
    color: #b9d5e8;

    .weather {
      display: flex;
      align-items: center;
      gap: 10px;
      font-size: 14px;
      white-space: nowrap;

      svg {
        width: 24px;
        height: 24px;
        color: #69cbf5;
      }
    }

    .action-divider {
      width: 1px;
      height: 16px;
      background: rgb(105 203 245 / 25%);
    }

    display: flex;
    align-items: center;
    padding: 25px 15px 0;
    gap: 15px;
  }

  .left {
    font-size: 16px;
    font-weight: bold;
    white-space: nowrap;
  }

  .right {
    .top-action {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 24px;
      height: 24px;
      padding: 0;
      border: 0;
      background: transparent;
      color: #69cbf5;
      cursor: pointer;

      svg {
        width: 22px;
        height: 22px;
      }

      &:hover,
      &:focus-visible {
        color: #69cbf5;
      }
    }
  }
}
</style>
