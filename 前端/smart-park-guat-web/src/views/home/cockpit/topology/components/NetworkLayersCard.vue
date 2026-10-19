<template>
  <div class="card-body layers">
    <button
      v-for="item in layers"
      :key="item.key"
      :class="{ active: layer === item.key }"
      @click="layer = item.key"
    >
      <span>{{ item.name }}</span
      ><b>{{
        monitor.snapshot
          ? item.key
            ? nodes.filter((n) => n.layer === item.key).length
            : nodes.length
          : '--'
      }}</b>
    </button>
  </div>
</template>

<script setup>
defineProps(['monitor', 'layers', 'nodes']);
const layer = defineModel('layer');
</script>

<style scoped>
.database-topology {
  position: absolute;
  inset: 0;
  color: #c6e2ff;
  font-size: 12px;
}
.topology-side {
  position: absolute;
  top: 5vw;
  bottom: 0;
  width: 25%;
  box-sizing: border-box;
  padding: 0 15px 15px;
  display: grid;
  grid-template-rows: repeat(3, minmax(0, 1fr));
  gap: 15px;
  min-height: 0;
}
.topology-side--left {
  left: 0;
}
.topology-side--right {
  right: 0;
}
.card-body {
  height: 100%;
  min-height: 0;
  box-sizing: border-box;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  overflow: auto;
}
.metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  flex: 1;
  min-height: 0;
}
.metrics > div {
  background: #082b4380;
  border: 1px solid #164b67;
  padding: 8px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 5px;
  color: #91abc7;
}
.metrics b {
  color: #5cddff;
  font-size: 18px;
}
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 7px 0;
  border-bottom: 1px dashed #245276;
  flex: 1;
  min-height: 0;
}
.total {
  font-size: 26px;
  color: #5cddff;
}
.total small {
  font-size: 11px;
}
small {
  color: #91abc7;
  font-size: 10px;
  overflow-wrap: anywhere;
}
button,
input {
  padding: 7px;
  background: #0c3357;
  border: 1px solid #297ba9;
  color: #b4e7ff;
  cursor: pointer;
}
.layers button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: 1;
  min-height: 0;
  border: 0;
  border-bottom: 1px dashed #245276;
  background: transparent;
}
.layers button.active {
  color: #5cddff;
  background: #0d4961;
}
.graph {
  position: absolute;
  top: 5vw;
  bottom: 170px;
  left: 25%;
  right: 25%;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: radial-gradient(ellipse at 50% 45%, #0d355080, transparent 65%);
}
.toolbar {
  display: flex;
  gap: 8px;
  padding: 10px;
}
.graph svg {
  flex: 1;
  min-height: 0;
  width: 100%;
}
.empty {
  text-align: center;
  margin: auto;
  padding: 15px;
  color: #91abc7;
}
text {
  fill: #d5f2ff;
  font-size: 11px;
  pointer-events: none;
}
g,
line {
  cursor: pointer;
}
</style>
