let nextId = 1;

type JsonPrimitiveId = number | string;
type JsonObject = Record<string, unknown>;

function serialize(payload: JsonObject): string {
  return JSON.stringify(payload);
}

function requestPayload(method: string, params: JsonObject): JsonObject {
  const id = nextId;
  nextId += 1;
  return { id, method, params };
}

export function rpcCall(method: string, params: Record<string, unknown> = {}): string {
  return serialize(requestPayload(method, params));
}

export function rpcEvent(method: string, params: Record<string, unknown> = {}): string {
  return serialize({ method, params });
}

export interface RpcReply {
  id?: JsonPrimitiveId;
  result?: unknown;
  error?: { code: number; message: string };
}

export interface RpcEvent {
  method: string;
  params: Record<string, unknown>;
}

export type RpcFrame = RpcReply | RpcEvent;

export type CodexTurnInputItem =
  | { type: 'text'; text: string }
  | { type: 'image'; url: string }
  | { type: 'localImage'; path: string };

export function parseRpcFrame(line: string): RpcFrame | null {
  try {
    return JSON.parse(line);
  } catch {
    return null;
  }
}

export function isRpcEvent(msg: RpcFrame): msg is RpcEvent {
  return 'method' in msg && !('id' in msg);
}

export function isRpcReply(msg: RpcFrame): msg is RpcReply {
  return 'id' in msg;
}

export function codexInitializeCall(clientName: string, version: string): string {
  return rpcCall('initialize', {
    clientInfo: { name: clientName, title: clientName, version },
    capabilities: { experimentalApi: true },
  });
}

export function codexInitializedEvent(): string {
  return rpcEvent('initialized', {});
}

function withOptionalValues(base: JsonObject, entries: Array<[string, unknown, boolean?]>): JsonObject {
  for (const [key, value, enabled = true] of entries) {
    if (enabled && value !== undefined && value !== '') base[key] = value;
  }
  return base;
}

export function codexThreadStartCall(
  model: string,
  cwd?: string,
  approvalPolicy = 'never',
  serviceTier?: string,
  includeServiceTier = true,
): string {
  const params: Record<string, unknown> = {
    model,
    approvalPolicy,
    sandbox: 'danger-full-access',
  };
  withOptionalValues(params, [
    ['cwd', cwd],
    ['serviceTier', serviceTier, includeServiceTier],
  ]);
  return rpcCall('thread/start', params);
}

export function codexThreadResumeCall(
  threadId: string,
  options: {
    model?: string;
    cwd?: string;
    approvalPolicy?: string;
    serviceTier?: string;
    includeServiceTier?: boolean;
  } = {},
): string {
  const params: Record<string, unknown> = { threadId };
  withOptionalValues(params, [
    ['model', options.model],
    ['cwd', options.cwd],
    ['approvalPolicy', options.approvalPolicy],
    ['serviceTier', options.serviceTier, options.includeServiceTier !== false],
  ]);
  return rpcCall('thread/resume', params);
}

export function codexThreadListCall(params: {
  limit?: number;
  cursor?: string;
  cwd?: string;
  archived?: boolean;
} = {}): string {
  return rpcCall('thread/list', params);
}

export function codexThreadReadCall(threadId: string, includeTurns = true): string {
  return rpcCall('thread/read', { threadId, includeTurns });
}

export function codexThreadArchiveCall(threadId: string): string {
  return rpcCall('thread/archive', { threadId });
}

export function codexThreadTurnsListCall(
  threadId: string,
  params: {
    limit?: number;
    cursor?: string;
    sortDirection?: 'asc' | 'desc';
  } = {},
): string {
  return rpcCall('thread/turns/list', {
    threadId,
    limit: params.limit,
    cursor: params.cursor,
    sortDirection: params.sortDirection,
  });
}

export function codexModelListCall(includeHidden = true): string {
  return rpcCall('model/list', { includeHidden });
}

export function codexTurnStartCall(
  threadId: string,
  text: string,
  options: {
    model?: string;
    effort?: string;
    serviceTier?: string;
    includeEffort?: boolean;
    includeServiceTier?: boolean;
    approvalPolicy?: string;
    cwd?: string;
    input?: CodexTurnInputItem[];
  } = {},
): string {
  const params: Record<string, unknown> = {
    threadId,
    input: options.input && options.input.length > 0 ? options.input : [{ type: 'text', text }],
  };
  withOptionalValues(params, [
    ['model', options.model],
    ['effort', options.effort, options.includeEffort !== false],
    ['serviceTier', options.serviceTier, options.includeServiceTier !== false],
    ['approvalPolicy', options.approvalPolicy],
    ['cwd', options.cwd],
  ]);
  return rpcCall('turn/start', params);
}

export function codexTurnInterruptCall(threadId: string, turnId: string): string {
  return rpcCall('turn/interrupt', { threadId, turnId });
}
