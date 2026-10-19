import { consoleApi } from './console';
/** Open-Meteo is a public, key-free provider. Browser fallback uses its HTTPS API. */
export async function getWeather() {
  const backend = await consoleApi<any>('/weather');
  if (backend.available) return backend;
  // Some Windows Java runtimes do not share the browser's certificate store.
  // Use the same real provider from the browser when the server cannot reach it.
  const latitude = backend.latitude ?? 31.2304,
    longitude = backend.longitude ?? 121.4737;
  const response = await fetch(
    `https://api.open-meteo.com/v1/forecast?latitude=${latitude}&longitude=${longitude}&current=temperature_2m,weather_code&timezone=Asia%2FShanghai`,
    { signal: AbortSignal.timeout(10000) },
  );
  if (!response.ok) throw new Error('天气服务暂不可用');
  const data = await response.json(),
    current = data.current;
  if (
    !current ||
    typeof current.temperature_2m !== 'number' ||
    typeof current.weather_code !== 'number'
  )
    throw new Error('天气数据不可用');
  return {
    city: backend.city,
    available: true,
    temperature: current.temperature_2m,
    code: current.weather_code,
    updatedAt: current.time,
    provider: 'Open-Meteo',
  };
}
