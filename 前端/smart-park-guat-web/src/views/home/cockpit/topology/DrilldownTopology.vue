<template>
  <div class="drilldown-topology">
    <div class="toolbar">
      <input
        v-model="keyword"
        placeholder="搜索设备或机柜，直接定位"
        aria-label="搜索网络设备"
        @keydown.enter="openFirstResult"
      />
      <select
        :value="scope.zone ?? ''"
        aria-label="筛选机房区域"
        @change="navigate({ zone: ($event.target as HTMLSelectElement).value || undefined })"
      >
        <option value="">整个机房</option>
        <option v-for="zone in zones" :key="zone" :value="zone">{{ zone }}区</option>
      </select>
      <button @click="zoom = Math.max(0.7, zoom - 0.15)" aria-label="缩小拓扑">−</button>
      <button @click="zoom = Math.min(1.8, zoom + 0.15)" aria-label="放大拓扑">＋</button>
      <button @click="navigate({})">重置</button>
    </div>
    <div v-if="keyword.trim()" class="search-results" role="region" aria-label="拓扑搜索结果">
      <button v-for="node in searchResults" :key="node.id" @click="openSearchResult(node)">
        {{ node.cabinetCode }} · {{ node.name }}
      </button>
      <span v-if="!searchResults.length">暂无匹配设备或机柜</span>
    </div>
    <nav class="breadcrumbs" aria-label="拓扑查看路径">
      <button :aria-current="!scope.zone ? 'page' : undefined" @click="navigate({})">
        整个机房
      </button>
      <template v-if="scope.zone"
        ><span>›</span
        ><button
          :aria-current="!scope.cabinetId ? 'page' : undefined"
          @click="navigate({ zone: scope.zone })"
        >
          {{ scope.zone }}区
        </button></template
      >
      <template v-if="scope.cabinetId"
        ><span>›</span><b>{{ cabinetCode }}</b></template
      >
    </nav>
    <div class="scope-summary">
      {{
        scope.cabinetId
          ? `${cabinetCode} · 设备连接与运行状态`
          : scope.zone
            ? `${scope.zone}区 · ${scopeCabinets} 台机柜 · ${scopeDevices} 台设备`
            : `全机房 · ${cabinetCount} 台机柜 · ${nodes.length} 台设备 · ${links.length} 条链路`
      }}
    </div>
    <div class="scope-hint">
      {{
        scope.cabinetId
          ? '点击设备或连线查看详情；淡色节点为跨机柜连接设备'
          : scope.zone
            ? '点击机柜查看内部设备与上联连接'
            : '核心 → 汇聚 → 机房区域，点击区域继续查看'
      }}<span v-if="simulated"> · 开发模拟连接</span>
    </div>
    <div ref="viewport" class="drilldown-viewport">
      <svg
        :viewBox="`0 0 ${layout.width} ${layout.height}`"
        :style="{ width: `${layout.width * zoom}px`, height: `${layout.height * zoom}px` }"
        role="img"
        :aria-label="
          scope.cabinetId
            ? '机柜内部网络拓扑'
            : scope.zone
              ? '区域机柜网络拓扑'
              : '机房骨干网络拓扑'
        "
      >
        <text
          v-for="heading in layout.headings"
          :key="heading.title"
          x="16"
          :y="heading.y"
          class="tier-heading"
        >
          {{ heading.title }}
        </text>
        <path
          v-for="link in layout.links"
          :key="link.id"
          class="topology-link"
          :d="linkPath(link)"
          :stroke="link.status === 'error' ? '#ff746e' : '#329cc6'"
          :opacity="selectedId && link.from !== selectedId && link.to !== selectedId ? 0.12 : 0.8"
          :stroke-width="selectedLinkId === link.id ? 4 : 2"
          fill="none"
          tabindex="0"
          role="button"
          :aria-label="`${boxName(link.from)}到${boxName(link.to)}，${link.memberCount}条链路`"
          @click="selectLink(link)"
          @keydown.enter="selectLink(link)"
        >
          <title>
            {{ boxName(link.from) }} → {{ boxName(link.to) }} · {{ link.memberCount }} 条链路
          </title>
        </path>
        <g
          v-for="box in layout.boxes"
          :key="box.id"
          class="topology-box"
          :data-kind="box.kind"
          :class="{ external: box.subtitle.startsWith('上联') }"
          :opacity="layer && box.node && box.node.layer !== layer ? 0.35 : 1"
          tabindex="0"
          role="button"
          :aria-label="
            box.kind === 'zone'
              ? `查看${box.title}机柜`
              : box.kind === 'cabinet'
                ? `查看机柜 ${box.title}`
                : `${box.title}，${box.detail}`
          "
          @click="openBox(box)"
          @keydown.enter="openBox(box)"
          @keydown.space.prevent="openBox(box)"
        >
          <title>{{ box.title }} · {{ box.subtitle }} · {{ box.detail }}</title>
          <rect
            :x="box.x"
            :y="box.y"
            :width="box.width"
            :height="box.height"
            rx="9"
            :fill="
              selectedId === box.id ? '#175674' : box.kind === 'device' ? '#0d344c' : '#103b56'
            "
            :stroke="box.abnormal ? '#ff746e' : box.node ? layerColor(box.node.layer) : '#35d3fa'"
            :stroke-width="box.abnormal || selectedId === box.id ? 2 : 1"
          />
          <text :x="box.x + box.width / 2" :y="box.y + 23" class="box-title" text-anchor="middle">
            {{ box.title.length > 11 ? box.title.slice(0, 10) + '…' : box.title }}
          </text>
          <text :x="box.x + box.width / 2" :y="box.y + 44" text-anchor="middle">
            {{ box.subtitle }}
          </text>
          <text
            :x="box.x + box.width / 2"
            :y="box.y + 65"
            :fill="box.abnormal ? '#ff9a95' : '#91c7d6'"
            text-anchor="middle"
          >
            {{ box.detail }}{{ box.kind !== 'device' ? ' ›' : '' }}
          </text>
        </g>
      </svg>
      <div v-if="!layout.boxes.length" class="empty" role="status">
        {{ error || '暂无当前范围的拓扑数据' }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch, onMounted, onBeforeUnmount } from 'vue';
import type { MonitorRecord } from '@/stores/monitoring';
import type { TopologyLink } from './topology-layout';
import { buildDrilldownTopology, type TopologyScope, type TopologyBox } from './topology-drilldown';
import { searchTopologyNodes } from './topology-search';
const props = defineProps<{
  nodes: MonitorRecord[];
  links: MonitorRecord[];
  status: (node: MonitorRecord) => string;
  layer: string;
  roomId?: string;
  error?: string;
}>();
const emit = defineEmits<{
  node: [node: MonitorRecord | undefined];
  link: [link: TopologyLink | undefined];
}>();
const scope = ref<TopologyScope>({}),
  keyword = ref(''),
  zoom = ref(1),
  width = ref(960);
const viewport = ref<HTMLElement>(),
  selectedId = ref(''),
  selectedLinkId = ref('');
let observer: ResizeObserver | undefined;
onMounted(() => {
  observer = new ResizeObserver(([entry]) => {
    if (entry) width.value = entry.contentRect.width;
  });
  if (viewport.value) observer.observe(viewport.value);
});
onBeforeUnmount(() => observer?.disconnect());
const zones = computed(() =>
  [
    ...new Set(props.nodes.map((node) => node.zoneCode).filter((code): code is string => !!code)),
  ].sort(),
);
const cabinetCount = computed(
  () => new Set(props.nodes.map((node) => node.cabinetId).filter(Boolean)).size,
);
const scopeCabinets = computed(
  () =>
    new Set(
      props.nodes
        .filter((node) => node.zoneCode === scope.value.zone)
        .map((node) => node.cabinetId),
    ).size,
);
const scopeDevices = computed(
  () => props.nodes.filter((node) => node.zoneCode === scope.value.zone).length,
);
const cabinetCode = computed(
  () => props.nodes.find((node) => node.cabinetId === scope.value.cabinetId)?.cabinetCode ?? '',
);
const simulated = computed(() =>
  props.nodes.some((node) => node.developmentSimulated || node.source?.startsWith('INTEGRATION_')),
);
const layout = computed(() =>
  buildDrilldownTopology(props.nodes, props.links, scope.value, width.value, props.status),
);
const searchResults = computed(() => searchTopologyNodes(props.nodes, keyword.value));
function navigate(next: TopologyScope) {
  scope.value = next;
  keyword.value = '';
  zoom.value = 1;
  selectedId.value = '';
  selectedLinkId.value = '';
  emit('node', undefined);
  emit('link', undefined);
  viewport.value?.scrollTo(0, 0);
}
function openBox(box: TopologyBox) {
  if (box.kind === 'zone') navigate({ zone: box.zone });
  else if (box.kind === 'cabinet') navigate({ zone: box.zone, cabinetId: box.cabinetId });
  else if (box.node) {
    selectedId.value = box.id;
    selectedLinkId.value = '';
    emit('node', box.node);
    emit('link', undefined);
  }
}
function openSearchResult(node: MonitorRecord) {
  navigate({ zone: node.zoneCode, cabinetId: node.cabinetId });
  selectedId.value = node.id;
  emit('node', node);
}
function openFirstResult() {
  if (searchResults.value[0]) openSearchResult(searchResults.value[0]);
}
function boxName(id?: string) {
  return layout.value.boxes.find((box) => box.id === id)?.title ?? id ?? '';
}
function selectLink(link: TopologyLink) {
  selectedLinkId.value = link.id;
  selectedId.value = '';
  emit('node', undefined);
  emit('link', { ...link, from: boxName(link.from), to: boxName(link.to) });
}
function linkPath(link: TopologyLink) {
  const from = layout.value.boxes.find((box) => box.id === link.from),
    to = layout.value.boxes.find((box) => box.id === link.to);
  if (!from || !to) return '';
  const x1 = from.x + from.width / 2,
    x2 = to.x + to.width / 2;
  if (from.y === to.y)
    return `M ${x1} ${from.y} C ${x1} ${from.y - 30}, ${x2} ${to.y - 30}, ${x2} ${to.y}`;
  const y1 = from.y < to.y ? from.y + from.height : from.y,
    y2 = from.y < to.y ? to.y : to.y + to.height;
  const mid = (y1 + y2) / 2;
  return `M ${x1} ${y1} C ${x1} ${mid}, ${x2} ${mid}, ${x2} ${y2}`;
}
function layerColor(layer?: string) {
  return (
    (
      { core: '#bb8aff', aggregate: '#4a91ff', access: '#35d3fa', terminal: '#45d9a5' } as Record<
        string,
        string
      >
    )[layer ?? ''] ?? '#889aa5'
  );
}
watch(
  () => props.roomId,
  () => navigate({}),
);
watch(layout, () => {
  if (selectedLinkId.value) {
    const link = layout.value.links.find((link) => link.id === selectedLinkId.value);
    if (link) emit('link', { ...link, from: boxName(link.from), to: boxName(link.to) });
    else {
      selectedLinkId.value = '';
      emit('link', undefined);
    }
  }
});
</script>

<style scoped>
.drilldown-topology {
  position: absolute;
  top: 5vw;
  bottom: 170px;
  left: 25%;
  right: 25%;
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 10px;
  box-sizing: border-box;
  color: #c6e2ff;
  background: radial-gradient(ellipse at 50% 40%, #0d355080, transparent 70%);
}
.toolbar {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
input,
select,
button {
  border: 1px solid #297ba9;
  color: #b4e7ff;
  background: #0c3357;
  padding: 7px 9px;
  font: inherit;
  font-size: 12px;
}
button,
select {
  cursor: pointer;
}
input {
  flex: 1;
  min-width: 150px;
}
.breadcrumbs {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 12px 0 8px;
  font-size: 14px;
}
.breadcrumbs button {
  border: 0;
  background: transparent;
  padding: 0;
  font-size: 14px;
  color: #65ddff;
}
.breadcrumbs [aria-current='page'],
.breadcrumbs b {
  color: #e3f5ff;
}
.scope-summary {
  color: #65ddff;
  font-size: 14px;
  margin-bottom: 5px;
}
.scope-hint {
  color: #91abc7;
  font-size: 12px;
  padding-bottom: 8px;
}
.drilldown-viewport {
  overflow: auto;
  flex: 1;
  min-height: 0;
}
.drilldown-viewport,
.search-results {
  scrollbar-width: thin;
  scrollbar-color: #287c9e #071d35;
}
.drilldown-viewport::-webkit-scrollbar,
.search-results::-webkit-scrollbar {
  width: 7px;
  height: 7px;
}
.drilldown-viewport::-webkit-scrollbar-track,
.search-results::-webkit-scrollbar-track {
  background: #071d35;
  border-radius: 8px;
}
.drilldown-viewport::-webkit-scrollbar-thumb,
.search-results::-webkit-scrollbar-thumb {
  background: #287c9e;
  border: 2px solid #071d35;
  border-radius: 8px;
}
.drilldown-viewport::-webkit-scrollbar-thumb:hover,
.search-results::-webkit-scrollbar-thumb:hover {
  background: #43bdd9;
}
.drilldown-viewport::-webkit-scrollbar-corner {
  background: #071d35;
}
svg {
  display: block;
  max-width: none;
}
text {
  fill: #b9dce9;
  font-size: 13px;
  pointer-events: none;
}
.tier-heading {
  fill: #91abc7;
  font-size: 13px;
}
.box-title {
  fill: #e3f5ff;
  font-size: 15px;
  font-weight: 600;
}
.topology-box,
.topology-link {
  cursor: pointer;
}
.external rect {
  fill: #0b253c;
  stroke-dasharray: 5 3;
}
.topology-box:focus rect {
  stroke: #fff;
  stroke-width: 2;
}
.search-results {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  max-height: 140px;
  overflow: auto;
  margin-top: 6px;
}
.empty {
  padding: 30px;
  text-align: center;
  color: #91abc7;
}
</style>
