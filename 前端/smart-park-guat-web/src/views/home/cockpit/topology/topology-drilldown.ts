import type { MonitorRecord } from '@/stores/monitoring';
import type { TopologyLink } from './topology-layout';

export interface TopologyScope {
  zone?: string;
  cabinetId?: string;
}
export interface TopologyBox {
  id: string;
  kind: 'device' | 'zone' | 'cabinet';
  title: string;
  subtitle: string;
  detail: string;
  zone?: string;
  cabinetId?: string;
  node?: MonitorRecord;
  abnormal: boolean;
  x: number;
  y: number;
  width: number;
  height: number;
}

export function buildDrilldownTopology(
  nodes: MonitorRecord[],
  links: MonitorRecord[],
  scope: TopologyScope,
  availableWidth = 960,
  status: (node: MonitorRecord) => string = (node) => node.status ?? '未采集',
) {
  const width = Math.max(540, availableWidth - 20);
  const boxes: TopologyBox[] = [];
  const headings: { title: string; y: number }[] = [];
  const mapping = new Map<string, string>();
  let rowY = 54;
  const deviceBox = (node: MonitorRecord, external = false): TopologyBox => ({
    id: node.id,
    kind: 'device',
    title: node.name ?? node.id,
    subtitle: `${external ? '上联 · ' : ''}${node.cabinetCode ?? '未关联机柜'}`,
    detail: status(node),
    node,
    abnormal: status(node) === '异常',
    x: 0,
    y: 0,
    width: 168,
    height: 82,
  });
  function row(title: string, items: TopologyBox[], columns = 5) {
    if (!items.length) return;
    headings.push({ title, y: rowY - 14 });
    const count = Math.min(columns, Math.max(1, Math.floor(width / 176)), items.length);
    const rows = Math.ceil(items.length / count);
    items.forEach((item, i) => {
      const rowCount = Math.min(count, items.length - Math.floor(i / count) * count);
      item.x = (width - rowCount * 176) / 2 + (i % count) * 176 + 4;
      item.y = rowY + Math.floor(i / count) * 118;
      boxes.push(item);
    });
    rowY += rows * 118 + 48;
  }
  const summary = (items: MonitorRecord[]) => {
    const abnormal = items.filter((node) => status(node) === '异常').length;
    const unknown = items.filter((node) => status(node) === '未采集').length;
    return { abnormal, detail: `异常 ${abnormal} · 未采集 ${unknown}` };
  };
  if (scope.cabinetId) {
    const local = nodes.filter((node) => node.cabinetId === scope.cabinetId);
    const localIds = new Set(local.map((node) => node.id));
    const outsideIds = new Set<string>();
    for (const link of links) {
      if (localIds.has(link.from ?? '') && !localIds.has(link.to ?? ''))
        outsideIds.add(link.to ?? '');
      if (localIds.has(link.to ?? '') && !localIds.has(link.from ?? ''))
        outsideIds.add(link.from ?? '');
    }
    const outside = nodes.filter((node) => outsideIds.has(node.id));
    row(
      '上联与跨柜连接设备',
      outside.map((node) => deviceBox(node, true)),
    );
    row(
      '柜内交换机',
      local.filter((node) => node.type === 'SWITCH').map((node) => deviceBox(node)),
    );
    row(
      '服务器与终端设备',
      local.filter((node) => node.type !== 'SWITCH').map((node) => deviceBox(node)),
    );
    for (const node of [...local, ...outside]) mapping.set(node.id, node.id);
  } else if (scope.zone) {
    const local = nodes.filter((node) => node.zoneCode === scope.zone);
    const upstream = local.filter((node) => node.layer === 'aggregate');
    row(
      `${scope.zone}区汇聚节点`,
      upstream.map((node) => deviceBox(node)),
    );
    const cabinets = [
      ...new Set(local.map((node) => node.cabinetId).filter((id): id is string => !!id)),
    ].sort((a, b) =>
      (local.find((node) => node.cabinetId === a)?.cabinetCode ?? a).localeCompare(
        local.find((node) => node.cabinetId === b)?.cabinetCode ?? b,
      ),
    );
    row(
      `${scope.zone}区机柜 · 点击查看内部连接`,
      cabinets.map((id) => {
        const devices = local.filter((node) => node.cabinetId === id),
          stats = summary(devices);
        return {
          id: `cabinet:${id}`,
          kind: 'cabinet',
          title: devices[0]?.cabinetCode ?? id,
          subtitle: `${devices.length} 台设备`,
          detail: stats.detail,
          cabinetId: id,
          zone: scope.zone,
          abnormal: stats.abnormal > 0,
          x: 0,
          y: 0,
          width: 168,
          height: 82,
        };
      }),
    );
    for (const node of local)
      mapping.set(node.id, node.layer === 'aggregate' ? node.id : `cabinet:${node.cabinetId}`);
  } else {
    row(
      '核心交换机',
      nodes.filter((node) => node.layer === 'core').map((node) => deviceBox(node)),
    );
    row(
      '区域汇聚节点',
      nodes
        .filter((node) => node.layer === 'aggregate')
        .sort(
          (a, b) => (a.zoneCode ?? '').localeCompare(b.zoneCode ?? '') || a.id.localeCompare(b.id),
        )
        .map((node) => deviceBox(node)),
    );
    const zones = [
      ...new Set(nodes.map((node) => node.zoneCode).filter((code): code is string => !!code)),
    ].sort();
    row(
      '机房区域 · 点击查看机柜连接',
      zones.map((zone) => {
        const devices = nodes.filter((node) => node.zoneCode === zone),
          stats = summary(devices);
        return {
          id: `zone:${zone}`,
          kind: 'zone',
          title: `${zone}区`,
          subtitle: `${new Set(devices.map((node) => node.cabinetId)).size} 台机柜 · ${devices.length} 台设备`,
          detail: stats.detail,
          zone,
          abnormal: stats.abnormal > 0,
          x: 0,
          y: 0,
          width: 168,
          height: 82,
        };
      }),
    );
    for (const node of nodes)
      mapping.set(
        node.id,
        node.layer === 'core' || node.layer === 'aggregate' ? node.id : `zone:${node.zoneCode}`,
      );
  }
  const boxIds = new Set(boxes.map((box) => box.id));
  const bundles = new Map<string, TopologyLink>();
  for (const link of links) {
    // In a cabinet view, include only connections that actually touch that cabinet.
    if (
      scope.cabinetId &&
      !nodes.some(
        (node) =>
          node.cabinetId === scope.cabinetId && (node.id === link.from || node.id === link.to),
      )
    )
      continue;
    const from = mapping.get(link.from ?? ''),
      to = mapping.get(link.to ?? '');
    if (!from || !to || from === to || !boxIds.has(from) || !boxIds.has(to)) continue;
    const key = `${from}->${to}`,
      existing = bundles.get(key);
    if (existing) {
      existing.memberCount++;
      existing.bandwidth = existing.latency = existing.loss = undefined;
      existing.status =
        existing.status === 'error' || link.status === 'error'
          ? 'error'
          : existing.status === 'normal' && link.status === 'normal'
            ? 'normal'
            : 'unknown';
    } else bundles.set(key, { ...link, id: key, from, to, memberCount: 1 });
  }
  return { boxes, links: [...bundles.values()], headings, width, height: Math.max(280, rowY - 20) };
}
