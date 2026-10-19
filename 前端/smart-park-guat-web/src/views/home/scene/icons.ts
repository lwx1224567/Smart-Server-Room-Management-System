const paths: Record<string, string> = {
  overview: 'M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z',
  monitor: 'M3 4h18v13H3zM8 21h8M12 17v4M6 11h3l2-4 3 7 2-3h2',
  server: 'M5 3h14v18H5zM5 9h14M5 15h14M8 6h1M8 12h1M8 18h1M12 6h4M12 12h4M12 18h4',
  energy: 'M13 2 5 13h6l-1 9 9-13h-7z',
  power: 'M12 2v9M7 4a9 9 0 1 0 10 0',
  bell: 'M5 17h14l-2-3V9a5 5 0 0 0-10 0v5zM10 21h4M12 2v2',
  user: 'M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0M4 21v-2a8 6 0 0 1 16 0v2',
  weather:
    'M6 3v2M2 8h2M3 3l2 2M11 3 9 5M4 13a5 5 0 1 1 7-7M7 19h12a4 4 0 0 0 0-8 6 6 0 0 0-11 1 3.5 3.5 0 0 0-1 7',
  temperature: 'M10 14.5V5a2 2 0 0 1 4 0v9.5a5 5 0 1 1-4 0M12 9v9M17 6h3M17 10h3',
  humidity: 'M12 2c-2 4-7 8-7 13a7 7 0 0 0 14 0c0-5-5-9-7-13zM8 15a4 4 0 0 0 4 4',
  work: 'M8 5H4v16h16V5h-4M8 3h8v4H8zM8 12h8M8 16h5',
  shield: 'M12 2 3 6v6c0 5 4 8 9 10 5-2 9-5 9-10V6zM8 12l3 3 5-6',
  alert: 'm12 3 10 18H2zM12 9v5M12 17v1',
  pin: 'M19 9c0 5-7 12-7 12S5 14 5 9a7 7 0 0 1 14 0M15 9a3 3 0 1 1-6 0 3 3 0 0 1 6 0',
  back: 'M20 12H4m6-6-6 6 6 6',
  down: 'M12 3v17m-6-6 6 6 6-6',
};
export function icon(name: string) {
  return `<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="${paths[name] || paths.server}"/></svg>`;
}
