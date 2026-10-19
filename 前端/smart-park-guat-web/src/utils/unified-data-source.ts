/** Legacy DEMO payloads are accepted, but all business data uses the REAL classification. */
export function normalizeDataSource<T>(value: T): T {
  if (Array.isArray(value)) return value.map(normalizeDataSource) as T;
  if (value && typeof value === 'object') {
    const result: Record<string, unknown> = {};
    for (const [key, item] of Object.entries(value)) {
      result[key] =
        ['recordOrigin', 'record_origin', 'mode'].includes(key) &&
        (item === 'DEMO' || item === 'MANUAL')
          ? 'REAL'
          : ['note', 'retestNote'].includes(key) && typeof item === 'string'
            ? item.split('DEMO 数据库复检').join('数据库复检').split('（非真实采集验收）').join('')
            : normalizeDataSource(item);
    }
    return result as T;
  }
  return value;
}
