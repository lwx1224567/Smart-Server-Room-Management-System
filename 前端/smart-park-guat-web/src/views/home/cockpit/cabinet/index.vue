<template>
  <div class="home-side home-side--left" style="--card-count: 3">
    <Border3 name="设备定位">
      <CabinetLocationCard
        v-if="currentCabinet && currentOperations"
        :device="currentCabinet"
        :state="currentOperations"
        platform
        @control="updateControl"
      />
      <div v-else class="inventory-error" role="status">
        {{ platform.detailError || '正在读取机柜详情…'
        }}<button v-if="platform.detailError" @click="retryDetail">重试</button>
      </div>
    </Border3>
    <Border3 name="设备状态">
      <CabinetStatusCard
        v-if="currentCabinet && currentOperations"
        :device="currentCabinet"
        :state="currentOperations"
        platform
        @control="updateControl"
      />
      <div v-else class="inventory-error" role="status">
        {{ platform.detailError || '正在读取机柜详情…'
        }}<button v-if="platform.detailError" @click="retryDetail">重试</button>
      </div>
    </Border3>
    <Border3 name="柜内设备列表">
      <DeviceListCard
        v-if="currentCabinet"
        :cabinet-id="currentCabinet.id"
        :cabinet-code="currentCabinet.code"
        :platform-devices="platform.devices"
        :target-device-id="targetInventoryDeviceId"
        @select-device="extractInventoryDevice"
      />
      <div v-else class="inventory-error" role="status">
        {{ platform.detailError || '正在读取机柜详情…'
        }}<button v-if="platform.detailError" @click="retryDetail">重试</button>
      </div>
    </Border3>
  </div>
  <div class="home-side home-side--right" style="--card-count: 3">
    <Border3 name="当前设备告警" class="alarm-statistics-card">
      <CabinetAlarmsCard
        v-if="currentCabinet && currentOperations"
        :device="currentCabinet"
        :state="currentOperations"
        platform
        @control="updateControl"
      />
      <div v-else class="inventory-error" role="status">
        {{ platform.detailError || '正在读取机柜详情…' }}
      </div>
    </Border3>
    <Border3 name="控制面板">
      <CabinetControlCard
        v-if="currentCabinet && currentOperations"
        :device="currentCabinet"
        :state="currentOperations"
        platform
        @control="updateControl"
      />
      <div v-else class="inventory-error" role="status">
        {{ platform.detailError || '正在读取机柜详情…' }}
      </div>
    </Border3>
    <Border3 name="操作日志">
      <OperationLogsCard
        v-if="currentCabinet && currentOperations"
        :device="currentCabinet"
        :state="currentOperations"
        platform
        @control="updateControl"
      />
      <div v-else class="inventory-error" role="status">
        {{ platform.detailError || '正在读取机柜详情…' }}
      </div>
    </Border3>
  </div>
</template>

<script setup lang="ts">
import Border3 from '@/components/Border3/index.vue';
import type { HomeDashboard } from '../../composables/useHomeDashboard';
import {
  CabinetLocationCard,
  CabinetStatusCard,
  DeviceListCard,
  CabinetAlarmsCard,
  CabinetControlCard,
  OperationLogsCard,
} from './components';
const props = defineProps<{ dashboard: HomeDashboard }>();
const {
  currentCabinet,
  currentOperations,
  updateControl,
  platform,
  targetInventoryDeviceId,
  extractInventoryDevice,
  retryDetail,
} = props.dashboard;
</script>

<style scoped lang="scss" src="../../styles/dashboard.scss"></style>
