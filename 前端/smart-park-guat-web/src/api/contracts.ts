export interface ApiResponse<T> {
  code: number;
  msg: string;
  data: T;
  requestId: string;
  serverTime: string;
  error: { type: string; fields: Record<string, unknown> } | null;
}

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
