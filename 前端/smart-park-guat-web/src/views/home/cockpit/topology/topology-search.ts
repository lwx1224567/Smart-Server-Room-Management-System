import type { MonitorRecord } from '@/stores/monitoring';

export function searchTopologyNodes(nodes: MonitorRecord[], keyword: string, limit = 8) {
  const query = keyword.trim().toLowerCase();
  if (!query) return [];
  const zone = query.match(/^([a-e])(?:区)?$/)?.[1];
  const cabinet = query.match(/^([a-e])[-－\s]?(\d{1,2})$/);
  const cabinetCode = cabinet ? `${cabinet[1]}-${cabinet[2]!.padStart(2, '0')}` : undefined;
  return nodes
    .filter((node) => {
      if (zone)
        return (node.zoneCode ?? node.cabinetCode?.split('-')[0] ?? '').toLowerCase() === zone;
      if (cabinetCode) return node.cabinetCode?.toLowerCase() === cabinetCode;
      // Internal topology IDs are matched exactly; generic words such as "device"
      // must not make unrelated cabinets appear in letter/name searches.
      return (
        node.id.toLowerCase() === query ||
        [node.name, node.code, node.cabinetCode].some((value) =>
          value?.toLowerCase().includes(query),
        )
      );
    })
    .sort(
      (a, b) =>
        (a.cabinetCode ?? '').localeCompare(b.cabinetCode ?? '', undefined, { numeric: true }) ||
        (a.code ?? a.name ?? a.id).localeCompare(b.code ?? b.name ?? b.id, undefined, {
          numeric: true,
        }) ||
        a.id.localeCompare(b.id, undefined, { numeric: true }),
    )
    .slice(0, limit);
}
