export interface ApiErrorContext {
  status: number;
  code?: number;
  type: string;
  fields?: Record<string, unknown>;
  requestId?: string;
  serverTime?: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code?: number;
  readonly type: string;
  readonly fields: Record<string, unknown>;
  readonly requestId?: string;
  readonly serverTime?: string;

  constructor(message: string, context: ApiErrorContext) {
    super(message);
    this.name = 'ApiError';
    this.status = context.status;
    this.code = context.code;
    this.type = context.type;
    this.fields = context.fields ?? {};
    this.requestId = context.requestId;
    this.serverTime = context.serverTime;
  }
}
