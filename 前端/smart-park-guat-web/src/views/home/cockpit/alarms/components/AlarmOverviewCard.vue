<template>
  <div class="alarm-overview">
    <small v-if="monitor.error">{{ monitor.error }}</small>
    <div class="kpis">
      <div>
        <span>当前告警</span><strong>{{ data.current }}</strong>
      </div>
      <div>
        <span>关联工单</span><strong>{{ data.today }}</strong>
      </div>
    </div>
    <div class="levels">
      <div v-for="item in data.levels" :key="item.name">
        <i :style="{ background: item.color }" /><span>{{ item.name }}</span
        ><b :style="{ color: item.color }">{{ item.count }}</b>
      </div>
    </div>
    <div class="statuses">
      <div v-for="item in data.statuses" :key="item.name">
        <span
          >{{ item.name }} <b>{{ item.count }}</b></span
        >
        <div class="track">
          <i
            :style="{
              width: `${data.current ? (item.count / data.current) * 100 : 0}%`,
              background: item.color,
            }"
          />
        </div>
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import { useMonitoringStore } from '@/stores/monitoring';
import { alarmLevels } from '@/utils/alarm-levels';
const monitor = useMonitoringStore();
import { frontendOrders } from '../../../../../services/work-orders';
const data = computed(() => {
  const active = (monitor.snapshot?.alarms ?? []).filter((alarm) => alarm.status !== '已恢复');
  const linked = active.filter((device) =>
    frontendOrders.some((order) => order.alarmEvent === device.id && order.status !== '已完成'),
  );
  return {
    current: active.length,
    today: linked.length,
    levels: alarmLevels.map((level) => ({
      name: level.name,
      color: level.color,
      count: active.filter((device) => device.severity === level.key).length,
    })),
    statuses: [
      { name: '未派单', count: active.length - linked.length, color: '#FF596B' },
      {
        name: '处理中',
        count: linked.filter((device) =>
          frontendOrders.some(
            (order) =>
              order.alarmEvent === device.id &&
              ['待受理', '已接单', '处理中'].includes(order.status),
          ),
        ).length,
        color: '#36A7FF',
      },
      {
        name: '待复测',
        count: linked.filter((device) =>
          frontendOrders.some(
            (order) => order.alarmEvent === device.id && order.status === '待复测',
          ),
        ).length,
        color: '#31DFC1',
      },
    ],
  };
});
</script>
<style scoped lang="scss">
.alarm-overview {
  display: flex;
  flex: 1;
  flex-direction: column;
  justify-content: space-between;
  gap: 18px;
  min-height: 0;
  padding: 8px 4px;
  box-sizing: border-box;
  font-size: 15px;
  line-height: 1.4;
}
.kpis {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}
.kpis > div {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 5px;
  border-bottom: 1px solid rgba(116, 174, 224, 0.2);
  padding: 0 6px 10px;
}
.kpis span {
  color: #91abc7;
  font-size: 16px;
}
.kpis strong {
  color: #5cdcf3;
  font-size: 38px;
  line-height: 1.1;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.levels {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}
.levels > div {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 8px 4px;
  background: rgba(54, 167, 255, 0.07);
}
.levels i {
  position: absolute;
  left: 0;
  top: 10px;
  bottom: 10px;
  width: 4px;
}
.levels b {
  font-size: 24px;
  line-height: 1.1;
}
.statuses {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}
.statuses span {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 6px;
  color: #91abc7;
  font-size: 14px;
  white-space: nowrap;
}
.statuses b {
  color: #c6e2ff;
  font-size: 21px;
  line-height: 1.2;
}
.track {
  height: 5px;
  margin-top: 9px;
  background: rgba(116, 174, 224, 0.12);
}
.track i {
  display: block;
  height: 100%;
}
@media (max-height: 800px), (max-width: 1500px) {
  .alarm-overview {
    padding: 2px;
    gap: 8px;
    font-size: 12px;
  }
  .kpis {
    gap: 16px;
  }
  .kpis > div {
    gap: 2px;
    padding: 0 3px 5px;
  }
  .kpis span {
    font-size: 13px;
  }
  .kpis strong {
    font-size: 29px;
  }
  .levels {
    gap: 6px;
  }
  .levels > div {
    padding: 5px 2px;
    gap: 2px;
  }
  .levels b {
    font-size: 19px;
  }
  .levels i {
    width: 3px;
    top: 7px;
    bottom: 7px;
  }
  .statuses {
    gap: 10px;
  }
  .statuses span {
    font-size: 12px;
    gap: 3px;
  }
  .statuses b {
    font-size: 17px;
  }
  .track {
    height: 4px;
    margin-top: 5px;
  }
}
</style>
