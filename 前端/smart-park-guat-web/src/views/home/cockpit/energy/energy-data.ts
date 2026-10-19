import { computed, ref } from 'vue';
import { useMonitoringStore } from '@/stores/monitoring';
export const energyPeriods = ['7天', '30天'] as const;
export const energyPeriod = ref<(typeof energyPeriods)[number]>('7天');
export const energyData = computed(() => {
  const data = useMonitoringStore().energy;
  const shares = data?.shares ?? [];
  return {
    labels: data?.labels ?? [],
    totals: data?.totals ?? [],
    total: data?.total ?? null,
    shares,
    topTwo: shares
      .filter((s) => s.consumption !== null)
      .sort((a, b) => b.consumption! - a.consumption!)
      .slice(0, 2),
  };
});
