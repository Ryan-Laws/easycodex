import { spawn, ChildProcess, execFileSync, type SpawnOptions } from 'child_process';
import { v4 as uuid } from 'uuid';
import fs from 'fs';
import os from 'os';
import path from 'path';
import {
  codexInitializeCall,
  codexInitializedEvent,
  codexThreadStartCall,
  codexThreadResumeCall,
  codexThreadListCall,
  codexThreadReadCall,
  codexThreadArchiveCall,
  codexThreadTurnsListCall,
  codexModelListCall,
  codexTurnStartCall,
  codexTurnInterruptCall,
  parseRpcFrame,
  isRpcEvent,
  isRpcReply,
  RpcReply,
} from './codex-rpc';
import { notifyMobileClients } from './notifier';

interface AgentInfo {
  id: string;
  name: string;
  model: string;
  cwd: string;
  approvalPolicy: string;
  serviceTier: string;
  reasoningEffort: string;
  systemPrompt: string;
  status: 'initializing' | 'ready' | 'working' | 'error' | 'stopped';
  createdAt: number;
  updatedAt: number;
  threadId: string | null;
  currentTurnId: string | null;
  codexThreadId: string | null;
  codexPath: string | null;
  source: string | null;
  messages: { role: string; type: string; text: string; timestamp: number; _itemId?: string }[];
  messageItemIds: Map<string, number>;
  fileSnapshots: Map<string, { exists: boolean; content: string | null }>;
  toolCalls: Map<string, { name: string; text: string }>;
  turnQueue: { text: string; timestamp: number }[];
  queueDraining: boolean;
  process: ChildProcess;
  buffer: string;
  pendingResponses: Map<number | string, (res: RpcReply) => void>;
  pendingAgentRequests: Map<string, { id: number | string; method: string; params: Record<string, unknown>; timestamp: number }>;
}

type BroadcastFn = (agentId: string, event: string, data: unknown) => void;

// Terminal colors
const RESET = '\x1b[0m';
const BOLD = '\x1b[1m';
const DIM = '\x1b[2m';
const GREEN = '\x1b[32m';
const YELLOW = '\x1b[33m';
const BLUE = '\x1b[34m';
const MAGENTA = '\x1b[35m';
const CYAN = '\x1b[36m';
const RED = '\x1b[31m';
const BG_BLUE = '\x1b[44m';
const WHITE = '\x1b[37m';
const AGENTS_STATE_PATH = path.join(os.homedir(), '.easycodex', 'agents.json');
const CODEX_GLOBAL_STATE_PATH = path.join(os.homedir(), '.codex', '.codex-global-state.json');
const CODEX_STATE_DB_PATH = path.join(os.homedir(), '.codex', 'state_5.sqlite');
const FILE_SNAPSHOT_LIMIT_BYTES = 512 * 1024;
const FILE_DIFF_LIMIT_CHARS = 16000;
const MOBILE_MESSAGE_TEXT_LIMIT = Number(process.env.EASY_CODEX_MOBILE_MESSAGE_TEXT_LIMIT || 20000);
const MOBILE_DETAIL_TEXT_LIMIT = Number(process.env.EASY_CODEX_MOBILE_DETAIL_TEXT_LIMIT || 12000);
const MOBILE_THREAD_TURN_PAGE_LIMIT = Number(process.env.EASY_CODEX_MOBILE_THREAD_TURN_PAGE_LIMIT || 50);
const MOBILE_THREAD_TURN_MAX_PAGES = Number(process.env.EASY_CODEX_MOBILE_THREAD_TURN_MAX_PAGES || 5);
const STOPPED_THREAD_OVERRIDE_TTL_MS = 6 * 60 * 60 * 1000;
const CODEX_SESSION_ACTIVE_GRACE_MS = Number(process.env.EASY_CODEX_SESSION_ACTIVE_GRACE_MS || 20000);
const MOBILE_TRUNCATED_NOTICE = '\n\n[EasyCodex mobile truncated this long output. Use the desktop relay/Codex session for the full text.]';
const PLAN_MODE_PREFIX = '请先进入计划模式处理下面的需求。';
const PLAN_MODE_DEMAND_MARKER = '需求：';
const CONTEXT_PLACEHOLDER = '已加载项目上下文。';
const DEFAULT_SERVICE_TIER = 'default';
const USER_INPUT_REQUEST_METHOD = 'item/tool/requestUserInput';

interface CodexSessionRuntimeState {
  running: boolean;
  updatedAt: number;
  activityLabel: string | null;
}

const sessionRuntimeCache = new Map<string, { size: number; mtimeMs: number; state: CodexSessionRuntimeState | null }>();

function cleanExecutablePath(value: unknown): string {
  return String(value || '').trim().replace(/^"+|"+$/g, '');
}

function commandForCmd(value: string): string {
  return `"${value.replace(/"/g, '""')}"`;
}

function codexAppServerInvocation(): { command: string; args: string[]; options: SpawnOptions } {
  const configured = cleanExecutablePath(process.env.CODEX_EXECUTABLE || process.env.EASY_CODEX_CODEX_PATH);
  const command = configured || 'codex';
  if (process.platform === 'win32' && /\.(cmd|bat)$/i.test(command)) {
    return {
      command: process.env.ComSpec || 'cmd.exe',
      args: ['/d', '/s', '/c', `call ${commandForCmd(command)} app-server`],
      options: { windowsVerbatimArguments: true },
    };
  }
  return { command, args: ['app-server'], options: {} };
}

function spawnCodexAppServer(cwd: string): ChildProcess {
  const invocation = codexAppServerInvocation();
  return spawn(invocation.command, invocation.args, {
    cwd,
    stdio: ['pipe', 'pipe', 'pipe'],
    env: { ...process.env },
    ...invocation.options,
  });
}

interface PersistedAgent {
  id: string;
  name: string;
  model: string;
  cwd: string;
  approvalPolicy?: string;
  serviceTier?: string;
  reasoningEffort?: string;
  systemPrompt?: string;
  codexThreadId?: string;
}

export interface CodexThreadSummary {
  id: string;
  name: string | null;
  preview: string;
  cwd: string;
  projectRoot: string | null;
  projectless: boolean;
  path: string;
  source: string | null;
  createdAt: number;
  updatedAt: number;
  status: string;
  pinned: boolean;
  activityLabel?: string | null;
  queuedFollowUpCount: number;
  queuedFollowUps: QueuedFollowUpSummary[];
}

export interface QueuedFollowUpSummary {
  id: string;
  text: string;
  cwd: string;
  createdAt: number;
  pausedReason: string | null;
}

export interface CodexModelInfo {
  id: string;
  model: string;
  displayName: string;
  description: string;
  hidden: boolean;
  defaultReasoningEffort: string;
  supportedReasoningEfforts: Array<{ reasoningEffort: string; description: string }>;
  additionalSpeedTiers: string[];
  isDefault: boolean;
  supportsPersonality: boolean;
  inputModalities: string[];
}

export interface RuntimeCapabilities {
  providerMode: 'official' | 'compatible';
  supportsServiceTier: boolean;
  supportsReasoningEffort: boolean;
  reason: string;
}

export interface CodexThreadDetail extends CodexThreadSummary {
  model: string;
  approvalPolicy: string;
  serviceTier: string;
  reasoningEffort: string;
  activityLabel?: string | null;
  messages: { role: string; type: string; text: string; timestamp: number; _itemId?: string }[];
}

export interface CodexSidebarSnapshot {
  projectRoots: string[];
  pinnedThreadIds: string[];
  projectThreadIds: string[];
  visibleThreadIds: string[];
  pinnedThreads: CodexThreadSummary[];
  projectThreads: CodexThreadSummary[];
  data: CodexThreadSummary[];
  nextCursor: string | null;
}

function mobileTextLimit(type: string): number {
  return ['command_output', 'file_change', 'sub_agent', 'thinking'].includes(type)
    ? MOBILE_DETAIL_TEXT_LIMIT
    : MOBILE_MESSAGE_TEXT_LIMIT;
}

function truncateForMobile(text: string, type: string): string {
  const limit = mobileTextLimit(type);
  if (!text || text.length <= limit || text.endsWith(MOBILE_TRUNCATED_NOTICE)) return text;
  return `${text.slice(0, limit).trimEnd()}${MOBILE_TRUNCATED_NOTICE}`;
}

function diffSummary(text: string): { files: string[]; additions: number; deletions: number } {
  const files = new Set<string>();
  let additions = 0;
  let deletions = 0;
  for (const line of text.split(/\r?\n/)) {
    if (line.startsWith('+') && !line.startsWith('+++')) additions += 1;
    if (line.startsWith('-') && !line.startsWith('---')) deletions += 1;

    const diffPath = line.match(/^diff --git a\/(.+?) b\/(.+)$/)?.[2];
    const newPath = line.match(/^\+\+\+ b\/(.+)$/)?.[1];
    const oldPath = line.match(/^--- a\/(.+)$/)?.[1];
    const plainPath = !line.startsWith('+') && !line.startsWith('-') && !line.startsWith('@@')
      ? line.trim()
      : '';
    for (const candidate of [diffPath, newPath, oldPath, plainPath]) {
      const normalized = normalizeFilePath(candidate || '');
      if (normalized && normalized !== '/dev/null' && (normalized.includes('/') || normalized.includes('\\') || normalized.includes('.'))) {
        files.add(normalized);
      }
    }
  }
  return { files: Array.from(files), additions, deletions };
}

function summarizeFileChangeForMobile(text: string): string {
  const summary = diffSummary(text);
  if (summary.files.length === 0) return '文件已修改。';
  const lines = summary.files.slice(0, 8).map((filePath) => {
    const stats = summary.additions + summary.deletions > 0 ? ` (+${summary.additions} -${summary.deletions})` : '';
    return `- ${filePath}${stats}`;
  });
  if (summary.files.length > lines.length) lines.push(`- 另有 ${summary.files.length - lines.length} 个文件`);
  return `文件改动\n${lines.join('\n')}`;
}

function summarizeCommandForMobile(text: string, completed = false): string {
  const command = text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .find((line) =>
      line &&
      !line.toLowerCase().startsWith('cwd:') &&
      !line.toLowerCase().startsWith('status:') &&
      !line.toLowerCase().startsWith('exit:') &&
      !line.toLowerCase().startsWith('duration:') &&
      !['正在运行命令。', '命令执行完成。', '命令已完成，输出已省略。'].includes(line)
    );
  if (!command) return completed ? '命令已完成，输出已省略。' : '运行命令';
  const compact = compactSingleLine(command);
  const visible = compact.length > 160 ? `${compact.slice(0, 160).trimEnd()}...` : compact;
  return `${completed ? '命令已完成' : '运行命令'}\n${visible}`;
}

function summarizeMessageForMobile<T extends { type: string; text: string }>(message: T): T {
  if ((message as T & { role?: string }).role === 'user' || message.type === 'user') {
    return { ...message, text: truncateForMobile(simplifyUserMessageForMobile(message.text), 'user') };
  }
  switch (message.type) {
    case 'command':
      return { ...message, text: summarizeCommandForMobile(message.text) };
    case 'command_output':
      return { ...message, text: summarizeCommandForMobile(message.text, true) };
    case 'file_change':
      return { ...message, text: summarizeFileChangeForMobile(message.text) };
    case 'sub_agent':
      return { ...message, text: '子代理已返回结果，详细内容已省略。' };
    case 'thinking':
      return { ...message, text: '正在思考中。' };
    default:
      return { ...message, text: truncateForMobile(message.text, message.type) };
  }
}

function simplifyUserMessageForMobile(text: string): string {
  const withoutContext = stripInjectedContextForMobile(text.trim());
  if (!withoutContext.startsWith(PLAN_MODE_PREFIX)) return withoutContext;
  const markerIndex = withoutContext.lastIndexOf(PLAN_MODE_DEMAND_MARKER);
  if (markerIndex < 0) return withoutContext;
  return withoutContext.slice(markerIndex + PLAN_MODE_DEMAND_MARKER.length).trim() || withoutContext;
}

function stripInjectedContextForMobile(text: string): string {
  if (!looksLikeInjectedContext(text)) return text;
  const endMarkers = ['</environment_context>', '</INSTRUCTIONS>'];
  let cursor = -1;
  for (const marker of endMarkers) {
    const index = text.lastIndexOf(marker);
    if (index >= 0) cursor = Math.max(cursor, index + marker.length);
  }
  if (cursor < 0) return '';
  const rest = text.slice(cursor).trim();
  if (!rest || looksLikeInjectedContext(rest)) return '';
  return rest;
}

function looksLikeInjectedContext(text: string): boolean {
  const trimmed = text.trimStart();
  return trimmed.startsWith('# AGENTS.md instructions for ') ||
    trimmed.startsWith('<INSTRUCTIONS>') ||
    trimmed.startsWith('<environment_context>') ||
    trimmed.includes('\n<INSTRUCTIONS>') ||
    trimmed.includes('\n<environment_context>');
}

function toTimestampMs(value: unknown, fallback = 0): number {
  let raw: number | null = null;
  if (typeof value === 'number' && !Number.isNaN(value)) {
    raw = value;
  } else if (typeof value === 'string') {
    const trimmed = value.trim();
    const numeric = Number(trimmed);
    if (trimmed && Number.isFinite(numeric)) {
      raw = numeric;
    } else {
      const parsed = Date.parse(trimmed);
      if (!Number.isNaN(parsed)) return parsed;
    }
  }
  if (raw === null) return fallback;
  if (raw > 1_000_000_000_000) return raw;
  if (raw > 1_000_000_000) return raw * 1000;
  return fallback;
}

function extractThreadStatus(raw: unknown): string {
  if (typeof raw === 'string') return raw.trim() || 'unknown';
  if (!raw || typeof raw !== 'object') return 'unknown';
  const type = (raw as Record<string, unknown>).type;
  return typeof type === 'string' && type.trim() ? type.trim() : 'unknown';
}

const HIDDEN_THREAD_STATUSES = new Set([
  'archived',
  'deleted',
  'removed',
  'trashed',
]);

const ACTIVE_THREAD_STATUSES = new Set([
  'initializing',
  'resuming',
  'working',
  'running',
  'active',
  'in_progress',
  'inprogress',
  'in-progress',
  'pending',
  'processing',
  'queued',
  'starting',
  'streaming',
]);

function isHiddenThreadStatus(status: string): boolean {
  return HIDDEN_THREAD_STATUSES.has(status.trim().toLowerCase());
}

function isActiveThreadStatus(status: string): boolean {
  return ACTIVE_THREAD_STATUSES.has(status.trim().toLowerCase());
}

type SqliteRow = Record<string, unknown>;
type SqliteStatement = { all: () => SqliteRow[] };
type SqliteDatabase = { prepare: (sql: string) => SqliteStatement; close: () => void };
type SqliteDatabaseConstructor = new (filename: string, options?: { readOnly?: boolean }) => SqliteDatabase;

let warnedCodexStateDbReadFailure = false;

function nodeSqliteDatabase(): SqliteDatabaseConstructor | null {
  try {
    const nodeRequire = eval('require') as (moduleName: string) => unknown;
    const sqlite = nodeRequire('node:sqlite') as { DatabaseSync?: SqliteDatabaseConstructor };
    return typeof sqlite?.DatabaseSync === 'function' ? sqlite.DatabaseSync : null;
  } catch {
    return null;
  }
}

function codexArchivedThreadIds(): Set<string> {
  if (!fs.existsSync(CODEX_STATE_DB_PATH)) return new Set();
  const DatabaseSync = nodeSqliteDatabase();
  if (!DatabaseSync) return new Set();

  let db: SqliteDatabase | null = null;
  try {
    db = new DatabaseSync(CODEX_STATE_DB_PATH, { readOnly: true });
    const rows = db.prepare('select id from threads where archived = 1 or archived_at is not null').all();
    return new Set(rows.map((row) => usableString(row.id)).filter(Boolean));
  } catch (err) {
    if (!warnedCodexStateDbReadFailure) {
      warnedCodexStateDbReadFailure = true;
      console.warn('[threads] Failed to read Codex archived thread state:', err);
    }
    return new Set();
  } finally {
    try { db?.close(); } catch {}
  }
}

function booleanField(value: Record<string, unknown>, keys: string[]): boolean {
  return keys.some((key) => value[key] === true);
}

function shouldShowCodexThread(thread: Record<string, unknown>, archivedThreadIds: Set<string> = new Set()): boolean {
  const id = usableString(thread.id);
  if (id && archivedThreadIds.has(id)) {
    return false;
  }
  if (booleanField(thread, ['archived', 'deleted', 'removed', 'trashed', 'isArchived', 'isDeleted'])) {
    return false;
  }
  return !isHiddenThreadStatus(extractThreadStatus(thread.status));
}

function stringArray(value: unknown): string[] {
  return Array.isArray(value)
    ? value.filter((entry): entry is string => typeof entry === 'string' && entry.trim() !== '' && entry.trim().toLowerCase() !== 'null')
    : [];
}

function usablePathString(value: unknown): string {
  if (typeof value !== 'string') return '';
  const trimmed = value.trim();
  return trimmed && trimmed.toLowerCase() !== 'null' ? trimmed : '';
}

function usableString(value: unknown): string {
  if (typeof value !== 'string') return '';
  const trimmed = value.trim();
  return trimmed && trimmed.toLowerCase() !== 'null' ? trimmed : '';
}

function normalizeServiceTier(value: unknown): string {
  const normalized = usableString(value).toLowerCase();
  if (!normalized || normalized === 'default' || normalized === 'standard' || normalized === 'auto') return DEFAULT_SERVICE_TIER;
  return normalized;
}

function codexServiceTierParam(value: string): string | undefined {
  const normalized = normalizeServiceTier(value);
  return normalized === DEFAULT_SERVICE_TIER ? undefined : normalized;
}

function readCodexDesktopState(): Record<string, unknown> | null {
  try {
    if (!fs.existsSync(CODEX_GLOBAL_STATE_PATH)) return null;
    const parsed = JSON.parse(fs.readFileSync(CODEX_GLOBAL_STATE_PATH, 'utf8')) as unknown;
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? parsed as Record<string, unknown>
      : null;
  } catch (err) {
    console.warn('[codex-state] Failed to read desktop state:', err);
    return null;
  }
}

function codexDesktopAtomState(): Record<string, unknown> | null {
  const state = readCodexDesktopState();
  const atomState = state?.['electron-persisted-atom-state'];
  return atomState && typeof atomState === 'object' && !Array.isArray(atomState)
    ? atomState as Record<string, unknown>
    : null;
}

function codexQueuedFollowUpsForThread(threadId: string): QueuedFollowUpSummary[] {
  if (!threadId.trim()) return [];
  const state = readCodexDesktopState();
  const queued = state?.['queued-follow-ups'];
  if (!queued || typeof queued !== 'object' || Array.isArray(queued)) return [];
  const rawItems = (queued as Record<string, unknown>)[threadId];
  if (!Array.isArray(rawItems)) return [];
  return rawItems
    .filter((item): item is Record<string, unknown> => !!item && typeof item === 'object' && !Array.isArray(item))
    .map((item) => {
      const context = item.context && typeof item.context === 'object' && !Array.isArray(item.context)
        ? item.context as Record<string, unknown>
        : {};
      return {
        id: usableString(item.id),
        text: usableString(item.text) || usableString(context.prompt),
        cwd: usablePathString(item.cwd),
        createdAt: toTimestampMs(item.createdAt),
        pausedReason: usableString(item.pausedReason) || null,
      };
    })
    .filter((item) => item.text.trim());
}

function codexPinnedThreadIds(): Set<string> {
  const state = readCodexDesktopState();
  const atomState = codexDesktopAtomState();
  return new Set(
    stringArray(state?.['pinned-thread-ids'])
      .concat(stringArray(atomState?.['pinned-thread-ids'])),
  );
}

function uniqueResolvedPaths(values: string[]): string[] {
  const seen = new Set<string>();
  const result: string[] = [];
  for (const value of values) {
    const resolved = path.resolve(value);
    const key = process.platform === 'win32' ? resolved.toLowerCase() : resolved;
    if (seen.has(key)) continue;
    seen.add(key);
    result.push(resolved);
  }
  return result;
}

export function codexDesktopVisibleWorkspaceRoots(): string[] {
  const state = readCodexDesktopState();
  const atomState = codexDesktopAtomState();
  if (!state && !atomState) return [];
  const projectOrder = stringArray(state?.['project-order']).concat(stringArray(atomState?.['project-order']));
  const activeRoots = stringArray(state?.['active-workspace-roots']).concat(stringArray(atomState?.['active-workspace-roots']));
  const savedRoots = stringArray(state?.['electron-saved-workspace-roots']).concat(stringArray(atomState?.['electron-saved-workspace-roots']));
  return uniqueResolvedPaths(projectOrder.length > 0 ? projectOrder : [...activeRoots, ...savedRoots]);
}

function codexThreadWorkspaceRootHints(): Map<string, string> {
  const state = readCodexDesktopState();
  const atomState = codexDesktopAtomState();
  const rawHints = state?.['thread-workspace-root-hints'] ?? atomState?.['thread-workspace-root-hints'];
  if (!rawHints || typeof rawHints !== 'object' || Array.isArray(rawHints)) return new Map();
  const hints = new Map<string, string>();
  for (const [threadId, root] of Object.entries(rawHints as Record<string, unknown>)) {
    const cleanRoot = usablePathString(root);
    if (cleanRoot) hints.set(threadId, path.resolve(cleanRoot));
  }
  return hints;
}

function codexProjectlessThreadIds(): Set<string> {
  const state = readCodexDesktopState();
  const atomState = codexDesktopAtomState();
  return new Set(
    stringArray(state?.['projectless-thread-ids'])
      .concat(stringArray(atomState?.['projectless-thread-ids'])),
  );
}

function isWithinAnyBase(bases: string[], targetPath: string): boolean {
  return bases.some((base) => isWithinBase(base, targetPath));
}

function isNestedCodexWorktree(cwd: string, projectRoot: string | null): boolean {
  if (!projectRoot) return false;
  const relative = path.relative(projectRoot, cwd);
  if (!relative || relative.startsWith('..') || path.isAbsolute(relative)) return false;
  const parts = relative.split(/[\\/]+/).map((part) => part.toLowerCase());
  return parts[0] === '.claude' && parts[1] === 'worktrees';
}

function shouldShowCodexThreadInDesktopWorkspace(
  thread: Record<string, unknown>,
  visibleWorkspaceRoots: string[],
  workspaceRootHints: Map<string, string>,
): boolean {
  if (visibleWorkspaceRoots.length === 0) return true;
  const id = typeof thread.id === 'string' ? thread.id : '';
  const hintedRoot = id ? workspaceRootHints.get(id) : undefined;
  if (hintedRoot && isWithinAnyBase(visibleWorkspaceRoots, hintedRoot)) return true;
  const cleanCwd = usablePathString(thread.cwd);
  const cwd = cleanCwd ? path.resolve(cleanCwd) : '';
  return Boolean(cwd && isWithinAnyBase(visibleWorkspaceRoots, cwd));
}

function codexThreadProjectRoot(
  thread: Record<string, unknown>,
  visibleWorkspaceRoots: string[],
  workspaceRootHints: Map<string, string>,
  projectlessThreadIds: Set<string> = new Set(),
): string | null {
  const id = typeof thread.id === 'string' ? thread.id : '';
  if (id && projectlessThreadIds.has(id)) return null;
  const hintedRoot = id ? workspaceRootHints.get(id) : undefined;
  if (hintedRoot && (visibleWorkspaceRoots.length === 0 || isWithinAnyBase(visibleWorkspaceRoots, hintedRoot))) {
    return hintedRoot;
  }
  const cleanCwd = usablePathString(thread.cwd);
  const cwd = cleanCwd ? path.resolve(cleanCwd) : '';
  if (!cwd) return null;
  return visibleWorkspaceRoots.find((root) => isWithinBase(root, cwd)) || cwd;
}

function isCodexConversationThread(
  thread: Record<string, unknown>,
  workspaceRootHints: Map<string, string>,
  projectlessThreadIds: Set<string> = new Set(),
): boolean {
  const id = typeof thread.id === 'string' ? thread.id : '';
  if (id && projectlessThreadIds.has(id)) return true;
  if (id && workspaceRootHints.has(id)) return false;
  return !usablePathString(thread.cwd);
}

function codexDesktopProjectRootForCwd(cwd: string): string | null {
  const cleanCwd = usablePathString(cwd);
  if (!cleanCwd) return null;
  const resolvedCwd = path.resolve(cleanCwd);
  return codexDesktopVisibleWorkspaceRoots().find((root) => isWithinBase(root, resolvedCwd)) || resolvedCwd;
}

function parseBooleanEnv(value: string | undefined): boolean {
  if (!value) return false;
  return ['1', 'true', 'yes', 'on'].includes(value.trim().toLowerCase());
}

function contentItemsToText(content: unknown): string {
  if (!Array.isArray(content)) return '';
  return content
    .map((entry) => {
      if (!entry || typeof entry !== 'object') return '';
      const text = (entry as Record<string, unknown>).text;
      return typeof text === 'string' ? text : '';
    })
    .filter(Boolean)
    .join('\n')
    .trim();
}

function valueToText(value: unknown): string {
  if (typeof value === 'string') return value.trim();
  if (Array.isArray(value)) {
    return value
      .map((entry) => {
        if (typeof entry === 'string') return entry;
        if (!entry || typeof entry !== 'object') return '';
        const record = entry as Record<string, unknown>;
        return valueToText(record.text ?? record.message ?? record.content);
      })
      .filter(Boolean)
      .join('\n')
      .trim();
  }
  if (value && typeof value === 'object') {
    const record = value as Record<string, unknown>;
    return valueToText(record.text ?? record.message ?? record.content);
  }
  return '';
}

function userInputRequestText(params: Record<string, unknown>): string {
  const questions = Array.isArray(params.questions) ? params.questions : [];
  const text = questions
    .map((entry) => {
      if (!entry || typeof entry !== 'object') return '';
      const question = entry as Record<string, unknown>;
      const header = typeof question.header === 'string' ? question.header.trim() : '';
      const prompt = typeof question.question === 'string' ? question.question.trim() : '';
      return [header, prompt].filter(Boolean).join('：');
    })
    .filter(Boolean)
    .join('\n');
  return text || valueToText(params.message) || 'Codex 需要你回答一个问题';
}

function turnsToMessages(thread: Record<string, unknown> | undefined) {
  const baseTimestamp = toTimestampMs(thread?.createdAt, Date.now());
  const turns = Array.isArray(thread?.turns) ? thread?.turns : [];
  const messages: AgentInfo['messages'] = [];
  const toolCalls = new Map<string, { name: string; text: string }>();
  let index = 0;

  for (const turn of turns) {
    if (!turn || typeof turn !== 'object') continue;
    const turnRecord = turn as Record<string, unknown>;
    const turnStartedAt = toTimestampMs(turnRecord.startedAt, baseTimestamp + index);
    const turnCompletedAt = toTimestampMs(turnRecord.completedAt, turnStartedAt);
    const itemTimestamp = (record: Record<string, unknown>, type: string) => {
      const fallback = type === 'userMessage' ? turnStartedAt : turnCompletedAt;
      return toTimestampMs(record.timestamp ?? record.createdAt ?? record.updatedAt, fallback) + index;
    };
    const items = Array.isArray(turnRecord.items)
      ? (turnRecord.items as unknown[])
      : [];

    for (const item of items) {
      if (!item || typeof item !== 'object') continue;
      const record = item as Record<string, unknown>;
      const type = typeof record.type === 'string' ? record.type : '';

      if (type === 'userMessage') {
        const text = contentItemsToText(record.content);
        if (text) {
          messages.push({ role: 'user', type: 'user', text, timestamp: itemTimestamp(record, type) });
          index += 1;
        }
        continue;
      }

      if (type === 'agentMessage') {
        const text = typeof record.text === 'string' ? record.text : '';
        if (text) {
          messages.push({ role: 'agent', type: 'agent', text, timestamp: itemTimestamp(record, type) });
          index += 1;
        }
        continue;
      }

      if (type === 'reasoning') {
        const text = formatReasoningItem(record);
        if (text) {
          messages.push({ role: 'agent', type: 'thinking', text, timestamp: itemTimestamp(record, type) });
          index += 1;
        }
        continue;
      }

      if (type === 'plan') {
        const text = valueToText(record.text);
        if (text) {
          messages.push({ role: 'agent', type: 'plan', text, timestamp: itemTimestamp(record, type) });
          index += 1;
        }
        continue;
      }

      if (type === 'commandExecution') {
        const text = formatCommandExecutionItem(record);
        if (text) {
          messages.push({ role: 'agent', type: 'command_output', text, timestamp: itemTimestamp(record, type) });
          index += 1;
        }
        continue;
      }

      if (type === 'fileChange') {
        const text = formatFileChangeItem(record);
        if (text) {
          messages.push({ role: 'agent', type: 'file_change', text, timestamp: itemTimestamp(record, type) });
          index += 1;
        }
        continue;
      }

      if (type === 'mcpToolCall') {
        const text = formatMcpToolCallItem(record);
        if (text) {
          messages.push({ role: 'agent', type: 'command_output', text, timestamp: itemTimestamp(record, type) });
          index += 1;
        }
        continue;
      }

      if (type === 'dynamicToolCall' || type === 'collabAgentToolCall') {
        const text = formatDynamicToolCallItem(record);
        if (text) {
          messages.push({ role: 'agent', type: isSubAgentToolItem(record) ? 'sub_agent' : 'command_output', text, timestamp: itemTimestamp(record, type) });
          index += 1;
        }
        continue;
      }

      if (type === 'webSearch') {
        const text = formatWebSearchItem(record);
        if (text) {
          messages.push({ role: 'agent', type: 'command', text, timestamp: itemTimestamp(record, type) });
          index += 1;
        }
        continue;
      }

      if (type === 'function_call' || type === 'custom_tool_call' || type === 'web_search_call') {
        const id = itemCallId(record) || itemIdentifier(record) || `tool_${index}`;
        const name = itemToolName(record);
        const text = formatToolCallText(record);
        toolCalls.set(id, { name, text });
        messages.push({ role: 'agent', type: 'command', text, timestamp: itemTimestamp(record, type) });
        index += 1;
        continue;
      }

      if (type === 'function_call_output' || type === 'custom_tool_call_output') {
        const id = itemCallId(record) || itemIdentifier(record) || `output_${index}`;
        const call = toolCalls.get(id);
        const text = extractToolOutputText(record);
        messages.push({
          role: 'agent',
          type: call && isPatchToolName(call.name) ? 'file_change' : 'command_output',
          text: text || call?.text || '',
          timestamp: itemTimestamp(record, type),
        });
        index += 1;
      }
    }
  }

  if (messages.length > 0) return messages;

  const entries = Array.isArray(thread?.entries)
    ? thread.entries
    : Array.isArray(thread?.items)
      ? thread.items
      : Array.isArray(thread?.events)
        ? thread.events
        : [];

  for (const entry of entries) {
    if (!entry || typeof entry !== 'object') continue;
    const record = entry as Record<string, unknown>;
    const payload = record.payload && typeof record.payload === 'object'
      ? record.payload as Record<string, unknown>
      : record;
    const eventType = typeof record.type === 'string' ? record.type : '';
    const payloadType = typeof payload.type === 'string' ? payload.type : '';
    const role = typeof payload.role === 'string' ? payload.role : '';
    const timestamp = toTimestampMs(record.timestamp ?? payload.timestamp, baseTimestamp + index);

    if (eventType === 'event_msg' && payloadType === 'user_message') {
      const text = valueToText(payload.message ?? payload.text ?? payload.content);
      if (text) messages.push({ role: 'user', type: 'user', text, timestamp });
      index += 1;
      continue;
    }

    if (eventType === 'event_msg' && payloadType === 'agent_message') {
      const text = valueToText(payload.message ?? payload.text ?? payload.content);
      if (text) messages.push({ role: 'agent', type: 'agent', text, timestamp });
      index += 1;
      continue;
    }

    if (eventType === 'event_msg' && payloadType === 'task_started') {
      messages.push({ role: 'agent', type: 'thinking', text: 'Thinking...', timestamp });
      index += 1;
      continue;
    }

    if (eventType === 'event_msg' && payloadType === 'error') {
      const text = valueToText(payload.message ?? payload.error);
      if (text) messages.push({ role: 'agent', type: 'status', text, timestamp });
      index += 1;
      continue;
    }

    if (eventType === 'event_msg' && payloadType === 'turn_aborted') {
      const reason = valueToText(payload.reason);
      messages.push({ role: 'agent', type: 'status', text: reason ? `Turn aborted: ${reason}` : 'Turn aborted.', timestamp });
      index += 1;
      continue;
    }

    if (eventType === 'event_msg' && payloadType === 'task_complete') {
      const text = valueToText(payload.last_agent_message);
      const lastAgent = [...messages].reverse().find((message) => message.role === 'agent' && message.type === 'agent');
      if (text && lastAgent?.text.trim() !== text.trim()) {
        messages.push({ role: 'agent', type: 'agent', text, timestamp });
      }
      index += 1;
      continue;
    }

    if (eventType === 'event_msg' && payloadType === 'exec_command_begin') {
      messages.push({
        role: 'agent',
        type: 'command',
        text: formatExecCommandBegin(payload),
        timestamp,
      });
      index += 1;
      continue;
    }

    if (eventType === 'event_msg' && payloadType === 'mcp_tool_call_begin') {
      messages.push({
        role: 'agent',
        type: 'command',
        text: formatMcpToolCallBegin(payload),
        timestamp,
      });
      index += 1;
      continue;
    }

    if (eventType === 'event_msg' && payloadType === 'patch_apply_begin') {
      messages.push({ role: 'agent', type: 'command', text: 'apply_patch', timestamp });
      index += 1;
      continue;
    }

    if (eventType === 'response_item' && payloadType === 'message') {
      const text = valueToText(payload.content ?? payload.message ?? payload.text);
      if (text && (role === 'user' || role === 'assistant')) {
        messages.push({ role: role === 'user' ? 'user' : 'agent', type: role === 'user' ? 'user' : 'agent', text, timestamp });
      }
      index += 1;
      continue;
    }

    if (eventType === 'response_item' && (payloadType === 'function_call' || payloadType === 'custom_tool_call' || payloadType === 'web_search_call')) {
      const id = itemCallId(payload) || itemIdentifier(payload) || `tool_${index}`;
      const name = itemToolName(payload);
      const text = formatToolCallText(payload);
      toolCalls.set(id, { name, text });
      messages.push({ role: 'agent', type: 'command', text, timestamp });
      index += 1;
      continue;
    }

    if (eventType === 'response_item' && (payloadType === 'function_call_output' || payloadType === 'custom_tool_call_output')) {
      const id = itemCallId(payload) || itemIdentifier(payload) || `output_${index}`;
      const call = toolCalls.get(id);
      const text = extractToolOutputText(payload);
      messages.push({
        role: 'agent',
        type: call && isPatchToolName(call.name) ? 'file_change' : 'command_output',
        text: text || call?.text || '',
        timestamp,
      });
      index += 1;
      continue;
    }

    if (eventType === 'event_msg' && payloadType === 'patch_apply_end') {
      messages.push({ role: 'agent', type: 'file_change', text: formatPatchApplyEnd(payload), timestamp });
      index += 1;
      continue;
    }

    if (eventType === 'event_msg' && (payloadType === 'exec_command_end' || payloadType === 'mcp_tool_call_end')) {
      const text = valueToText(payload.aggregated_output ?? payload.output ?? payload.stdout ?? payload.stderr);
      if (text) messages.push({ role: 'agent', type: 'command_output', text, timestamp });
      index += 1;
    }
  }

  return messages;
}

function readSessionMessages(sessionPath: unknown) {
  if (typeof sessionPath !== 'string' || !sessionPath.trim()) return [];
  try {
    if (!fs.existsSync(sessionPath)) return [];
    const entries = fs.readFileSync(sessionPath, 'utf8')
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter(Boolean)
      .map((line) => {
        try {
          return JSON.parse(line) as unknown;
        } catch {
          return null;
        }
      })
      .filter(Boolean);
    return turnsToMessages({ entries });
  } catch {
    return [];
  }
}

function mergeMessageHistory(
  primary: AgentInfo['messages'],
  secondary: AgentInfo['messages'],
): AgentInfo['messages'] {
  if (primary.length === 0) return secondary;
  if (secondary.length === 0) return primary;
  const byKey = new Map<string, AgentInfo['messages'][number]>();
  for (const message of [...secondary, ...primary]) {
    const itemId = message._itemId ? `item:${message._itemId}` : '';
    const key = itemId || `${message.role}\0${message.type}\0${message.text}`;
    const existing = byKey.get(key);
    if (!existing || message.timestamp >= existing.timestamp) byKey.set(key, message);
  }
  return Array.from(byKey.values()).sort((a, b) => a.timestamp - b.timestamp);
}

function inferThreadActivity(messages: AgentInfo['messages']): string | null {
  const last = [...messages].reverse().find((message) => message.role === 'agent');
  switch (last?.type) {
    case 'thinking':
      return '正在思考中，推理内容持续返回';
    case 'command':
      return '正在运行命令，等待执行结果';
    case 'file_change':
      return '正在修改文件，改动内容持续更新';
    case 'plan':
      return '正在规划步骤，准备继续执行';
    default:
      return null;
  }
}

function objectRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null;
}

function sessionEventPayload(record: Record<string, unknown>): Record<string, unknown> {
  const direct = objectRecord(record.payload);
  if (direct) return direct;
  const msg = objectRecord(record.msg);
  return objectRecord(msg?.payload) || {};
}

function sessionActivityLabel(topType: string, payload: Record<string, unknown>): string | null {
  const payloadType = usableString(payload.type);
  if (topType === 'response_item') {
    switch (payloadType) {
      case 'reasoning':
        return '正在思考中，推理内容持续返回';
      case 'function_call':
        return '正在运行命令，等待执行结果';
      case 'function_call_output':
        return '正在读取命令输出';
      case 'message':
        return '正在生成回复';
      default:
        return null;
    }
  }
  if (topType === 'event_msg') {
    switch (payloadType) {
      case 'agent_message':
        return '正在生成回复';
      case 'exec_command_begin':
      case 'mcp_tool_call_begin':
        return '正在运行命令，等待执行结果';
      case 'exec_command_end':
      case 'mcp_tool_call_end':
        return '正在读取命令输出';
      case 'patch_apply_begin':
        return '正在修改文件，改动内容持续更新';
      case 'patch_apply_end':
        return '正在整理文件改动';
      default:
        return null;
    }
  }
  return null;
}

function readCodexSessionRuntimeState(sessionPath: unknown): CodexSessionRuntimeState | null {
  const cleanPath = usablePathString(sessionPath);
  if (!cleanPath) return null;
  try {
    const stat = fs.statSync(cleanPath);
    if (!stat.isFile()) return null;
    const cached = sessionRuntimeCache.get(cleanPath);
    if (cached && cached.size === stat.size && cached.mtimeMs === stat.mtimeMs) return cached.state;

    const lines = fs.readFileSync(cleanPath, 'utf8').split(/\r?\n/);
    let lifecycle: 'started' | 'terminal' | null = null;
    let updatedAt = 0;
    let activityLabel: string | null = null;
    let sawActiveEvent = false;
    for (const line of lines) {
      if (!line.trim()) continue;
      let parsed: unknown;
      try {
        parsed = JSON.parse(line);
      } catch {
        continue;
      }
      const record = objectRecord(parsed);
      if (!record) continue;
      const topType = usableString(record.type);
      const payload = sessionEventPayload(record);
      const payloadType = usableString(payload.type);
      const timestamp = toTimestampMs(record.timestamp, updatedAt);
      if (timestamp > 0) updatedAt = Math.max(updatedAt, timestamp);

      const activeLabel = sessionActivityLabel(topType, payload);
      if (activeLabel) {
        sawActiveEvent = true;
        if (lifecycle !== 'terminal') activityLabel = activeLabel;
      }
      if (topType === 'event_msg' && payloadType === 'task_started') {
        lifecycle = 'started';
        activityLabel = '正在运行中，AI 正在接手任务';
        continue;
      }
      if (
        topType === 'event_msg'
        && ['task_complete', 'task_failed', 'turn_aborted', 'error'].includes(payloadType)
      ) {
        lifecycle = 'terminal';
        activityLabel = null;
        continue;
      }
      if (lifecycle === 'started') {
        activityLabel = activeLabel || activityLabel;
      }
    }

    const recentlyActive = sawActiveEvent && lifecycle !== 'terminal' && Date.now() - stat.mtimeMs <= CODEX_SESSION_ACTIVE_GRACE_MS;
    const state = lifecycle
      ? {
          running: lifecycle === 'started',
          updatedAt,
          activityLabel: lifecycle === 'started' ? activityLabel : null,
        }
      : sawActiveEvent
        ? {
            running: recentlyActive,
            updatedAt,
            activityLabel: recentlyActive ? activityLabel : null,
          }
        : null;
    sessionRuntimeCache.set(cleanPath, { size: stat.size, mtimeMs: stat.mtimeMs, state });
    return state;
  } catch {
    return null;
  }
}

function isWithinBase(base: string, targetPath: string): boolean {
  const resolvedBaseRaw = path.resolve(base);
  const resolvedRaw = path.resolve(targetPath);
  const resolvedBase = process.platform === 'win32' ? resolvedBaseRaw.toLowerCase() : resolvedBaseRaw;
  const resolved = process.platform === 'win32' ? resolvedRaw.toLowerCase() : resolvedRaw;
  return resolved === resolvedBase || resolved.startsWith(`${resolvedBase}${path.sep}`);
}

function findStringField(value: unknown, keys: string[], depth = 0): string | null {
  if (!value || depth > 4) return null;
  if (typeof value !== 'object') return null;
  if (Array.isArray(value)) {
    for (const entry of value) {
      const found = findStringField(entry, keys, depth + 1);
      if (found) return found;
    }
    return null;
  }
  const record = value as Record<string, unknown>;
  for (const key of keys) {
    const direct = record[key];
    if (typeof direct === 'string' && direct.trim()) return direct.trim();
  }
  for (const entry of Object.values(record)) {
    const found = findStringField(entry, keys, depth + 1);
    if (found) return found;
  }
  return null;
}

function normalizeFilePath(value: string): string {
  return value
    .trim()
    .replace(/^["'`]+|["'`]+$/g, '')
    .replace(/^file:\/\//i, '');
}

function resolveAgentFile(agent: AgentInfo, rawPath: string): { absolute: string; relative: string } | null {
  const normalized = normalizeFilePath(rawPath);
  if (!normalized) return null;
  const cwd = path.resolve(agent.cwd || process.cwd());
  const absolute = path.isAbsolute(normalized)
    ? path.resolve(normalized)
    : path.resolve(cwd, normalized);
  if (!isWithinBase(cwd, absolute)) return null;
  return {
    absolute,
    relative: (path.relative(cwd, absolute) || path.basename(absolute)).split(path.sep).join('/'),
  };
}

function readTextSnapshot(absolutePath: string): { exists: boolean; content: string | null } {
  try {
    const stat = fs.statSync(absolutePath);
    if (!stat.isFile()) return { exists: false, content: null };
    if (stat.size > FILE_SNAPSHOT_LIMIT_BYTES) return { exists: true, content: null };
    return { exists: true, content: fs.readFileSync(absolutePath, 'utf8') };
  } catch {
    return { exists: false, content: null };
  }
}

function limitDiff(text: string): string {
  if (text.length <= FILE_DIFF_LIMIT_CHARS) return text;
  return `${text.slice(0, FILE_DIFF_LIMIT_CHARS)}\n... diff truncated by EasyCodex ...`;
}

function buildSnapshotDiff(relativePath: string, before: string | null, after: string | null): string {
  if (before === after) return '';
  const beforeLines = (before || '').split(/\r?\n/);
  const afterLines = (after || '').split(/\r?\n/);
  const lines = [
    `--- a/${relativePath}`,
    `+++ b/${relativePath}`,
  ];

  const maxLines = Math.max(beforeLines.length, afterLines.length);
  for (let i = 0; i < maxLines; i += 1) {
    const left = beforeLines[i];
    const right = afterLines[i];
    if (left === right) continue;
    if (left !== undefined) lines.push(`-${left}`);
    if (right !== undefined) lines.push(`+${right}`);
    if (lines.join('\n').length > FILE_DIFF_LIMIT_CHARS) {
      lines.push('... diff truncated by EasyCodex ...');
      break;
    }
  }

  return lines.join('\n');
}

function readGitDiff(agent: AgentInfo, relativePath: string): string {
  try {
    return execFileSync('git', ['-C', agent.cwd, 'diff', '--', relativePath], {
      encoding: 'utf8',
      maxBuffer: FILE_DIFF_LIMIT_CHARS * 4,
      windowsHide: true,
    }).trim();
  } catch {
    return '';
  }
}

function extractFilePathFromItem(item: Record<string, unknown> | undefined): string | null {
  return findStringField(item, [
    'path',
    'file',
    'filename',
    'filePath',
    'relativePath',
    'targetPath',
  ]);
}

function formatFileChangeText(relativePath: string, diff: string): string {
  const body = diff.trim() || 'EasyCodex detected a file change, but no textual diff was available.';
  return `${relativePath}\n${limitDiff(body)}`;
}

function safeJsonParseObject(value: unknown): Record<string, unknown> | null {
  if (!value) return null;
  if (typeof value === 'object' && !Array.isArray(value)) return value as Record<string, unknown>;
  if (typeof value !== 'string' || !value.trim()) return null;
  try {
    const parsed = JSON.parse(value) as unknown;
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? parsed as Record<string, unknown>
      : null;
  } catch {
    return null;
  }
}

function itemIdentifier(item: Record<string, unknown> | undefined): string {
  if (!item) return '';
  return String(item.id || item.itemId || item.call_id || item.callId || '');
}

function itemCallId(item: Record<string, unknown> | undefined): string {
  if (!item) return '';
  return String(item.call_id || item.callId || item.id || item.itemId || '');
}

function itemToolName(item: Record<string, unknown> | undefined): string {
  if (!item) return '';
  return String(item.name || item.toolName || item.tool || item.namespace || '');
}

function itemArguments(item: Record<string, unknown> | undefined): Record<string, unknown> | null {
  if (!item) return null;
  return safeJsonParseObject(item.arguments ?? item.input ?? item.params);
}

function compactSingleLine(value: string, max = 140): string {
  const normalized = value.replace(/\s+/g, ' ').trim();
  if (!normalized) return '';
  return normalized.length > max ? `${normalized.slice(0, max - 3)}...` : normalized;
}

function uniqueValues(values: string[]): string[] {
  return Array.from(new Set(values.map((value) => value.trim()).filter(Boolean)));
}

function extractPatchText(args: Record<string, unknown> | null, item: Record<string, unknown>): string {
  const candidates = [
    args?.patch,
    args?.input,
    args?.command,
    item.patch,
    item.input,
    item.arguments,
  ];
  for (const candidate of candidates) {
    if (typeof candidate === 'string' && candidate.includes('*** Begin Patch')) {
      return candidate.trim();
    }
  }
  return '';
}

function patchTargetFiles(patchText: string): string[] {
  const files: string[] = [];
  for (const rawLine of patchText.split(/\r?\n/)) {
    const line = rawLine.trim();
    const patchHeader = line.match(/^\*\*\* (?:Add|Update|Delete) File:\s+(.+)$/);
    if (patchHeader?.[1]) {
      files.push(normalizeFilePath(patchHeader[1]));
      continue;
    }
    const moveHeader = line.match(/^\*\*\* Move to:\s+(.+)$/);
    if (moveHeader?.[1]) {
      files.push(normalizeFilePath(moveHeader[1]));
      continue;
    }
    const gitHeader = line.match(/^diff --git a\/(.+?) b\/(.+)$/);
    if (gitHeader?.[2]) {
      files.push(normalizeFilePath(gitHeader[2]));
    }
  }
  return uniqueValues(files);
}

function formatApplyPatchText(args: Record<string, unknown> | null, item: Record<string, unknown>): string {
  const patchText = extractPatchText(args, item);
  if (!patchText) return 'apply_patch';
  const files = patchTargetFiles(patchText);
  const fileSummary = files.length
    ? `Files:\n${files.map((filePath) => `- ${filePath}`).join('\n')}`
    : 'Files: detected after patch applies';
  return limitDiff(`apply_patch\n${fileSummary}\n\n${patchText}`);
}

function formatToolCallText(item: Record<string, unknown>): string {
  const name = itemToolName(item);
  const args = itemArguments(item);
  if (name === 'shell_command') {
    const command = typeof args?.command === 'string' ? args.command : '';
    return command || 'shell_command';
  }
  if (name === 'apply_patch') return formatApplyPatchText(args, item);
  if (name) {
    const argText = args ? JSON.stringify(args) : '';
    return compactSingleLine(`${name}${argText ? ` ${argText}` : ''}`);
  }
  return compactSingleLine(String(item.text || item.command || item.input || item.arguments || 'Tool call'));
}

function extractToolOutputText(item: Record<string, unknown>): string {
  const raw = item.output ?? item.result ?? item.text ?? '';
  const parsed = safeJsonParseObject(raw);
  if (typeof parsed?.output === 'string') return parsed.output;
  if (typeof raw === 'string') return raw;
  return valueToText(raw);
}

function isPatchToolName(name: string): boolean {
  const normalized = name.toLowerCase();
  return normalized === 'apply_patch' || normalized.includes('patch');
}

function formatPatchApplyEnd(payload: Record<string, unknown>): string {
  const changes = payload.changes;
  if (changes && typeof changes === 'object' && !Array.isArray(changes)) {
    const lines: string[] = [];
    for (const [filePath, change] of Object.entries(changes as Record<string, unknown>)) {
      const record = change && typeof change === 'object' ? change as Record<string, unknown> : {};
      const diff = typeof record.unified_diff === 'string' ? record.unified_diff : '';
      lines.push(`${normalizeFilePath(filePath)}\n${diff.trim() || String(record.type || 'changed')}`.trim());
    }
    if (lines.length) return limitDiff(lines.join('\n\n'));
  }
  const stdout = typeof payload.stdout === 'string' ? payload.stdout.trim() : '';
  return stdout || 'Files changed by apply_patch.';
}

function formatJsonDetail(value: unknown, max = 2000): string {
  if (value === null || value === undefined) return '';
  if (typeof value === 'string') return compactSingleLine(value, max);
  try {
    return compactSingleLine(JSON.stringify(value), max);
  } catch {
    return compactSingleLine(String(value), max);
  }
}

function formatDuration(value: unknown): string {
  if (typeof value !== 'number' || Number.isNaN(value)) return '';
  if (value < 1000) return `${Math.round(value)}ms`;
  return `${(value / 1000).toFixed(1)}s`;
}

function formatFileUpdateChanges(changes: unknown): string {
  if (!changes) return '';
  if (Array.isArray(changes)) {
    return changes
      .map((change) => {
        if (!change || typeof change !== 'object') return '';
        const record = change as Record<string, unknown>;
        const filePath = normalizeFilePath(valueToText(record.path));
        const kind = valueToText(record.kind);
        const diff = valueToText(record.diff);
        return [filePath, kind ? `status: ${kind}` : '', diff].filter(Boolean).join('\n');
      })
      .filter(Boolean)
      .join('\n\n');
  }
  if (typeof changes === 'object') {
    const lines: string[] = [];
    for (const [filePath, rawChange] of Object.entries(changes as Record<string, unknown>)) {
      const record = rawChange && typeof rawChange === 'object' ? rawChange as Record<string, unknown> : {};
      const diff = valueToText(record.unified_diff ?? record.diff ?? record.content);
      const kind = valueToText(record.type ?? record.kind);
      lines.push([normalizeFilePath(filePath), kind ? `status: ${kind}` : '', diff].filter(Boolean).join('\n'));
    }
    return lines.filter(Boolean).join('\n\n');
  }
  return valueToText(changes);
}

function formatReasoningItem(item: Record<string, unknown>): string {
  const summary = Array.isArray(item.summary) ? item.summary.map(valueToText).filter(Boolean) : [];
  const content = Array.isArray(item.content) ? item.content.map(valueToText).filter(Boolean) : [];
  return [...summary, ...content].join('\n').trim() || valueToText(item.text) || 'Thinking...';
}

function formatCommandExecutionItem(item: Record<string, unknown>): string {
  const command = valueToText(item.command);
  const cwd = valueToText(item.cwd);
  const status = valueToText(item.status);
  const output = valueToText(item.aggregatedOutput ?? item.aggregated_output ?? item.output ?? item.stdout ?? item.stderr);
  const exitCode = typeof item.exitCode === 'number' ? `exit: ${item.exitCode}` : '';
  const duration = formatDuration(item.durationMs ?? item.duration_ms);
  const meta = [cwd ? `cwd: ${cwd}` : '', status ? `status: ${status}` : '', exitCode, duration ? `duration: ${duration}` : '']
    .filter(Boolean)
    .join('\n');
  return [command, meta, output].filter(Boolean).join('\n\n') || 'Shell command.';
}

function formatFileChangeItem(item: Record<string, unknown>): string {
  const status = valueToText(item.status);
  const body = formatFileUpdateChanges(item.changes)
    || valueToText(item.diff ?? item.patch ?? item.output ?? item.text);
  return [status ? `status: ${status}` : '', body].filter(Boolean).join('\n\n') || 'Files changed.';
}

function formatMcpToolCallItem(item: Record<string, unknown>): string {
  const server = valueToText(item.server);
  const tool = valueToText(item.tool ?? item.name);
  const status = valueToText(item.status);
  const args = formatJsonDetail(item.arguments ?? item.args ?? item.input);
  const result = formatJsonDetail(item.result);
  const error = valueToText((item.error as Record<string, unknown> | undefined)?.message ?? item.error);
  const duration = formatDuration(item.durationMs ?? item.duration_ms);
  const title = [server, tool].filter(Boolean).join('.') || 'MCP tool';
  const meta = [status ? `status: ${status}` : '', duration ? `duration: ${duration}` : ''].filter(Boolean).join('\n');
  return [title, args ? `args: ${args}` : '', meta, error ? `error: ${error}` : '', result ? `result: ${result}` : '']
    .filter(Boolean)
    .join('\n\n');
}

function isSubAgentToolItem(item: Record<string, unknown>): boolean {
  const type = valueToText(item.type).toLowerCase();
  const namespace = valueToText(item.namespace).toLowerCase();
  const tool = itemToolName(item).toLowerCase();
  return type === 'collabagenttoolcall'
    || namespace.includes('agent')
    || ['spawn_agent', 'wait_agent', 'send_input', 'close_agent', 'resume_agent'].includes(tool);
}

function formatDynamicToolCallItem(item: Record<string, unknown>): string {
  const namespace = valueToText(item.namespace);
  const tool = valueToText(item.tool ?? item.name);
  const status = valueToText(item.status);
  const args = formatJsonDetail(item.arguments ?? item.args ?? item.input);
  const content = formatJsonDetail(item.contentItems ?? item.content ?? item.output ?? item.result);
  const success = typeof item.success === 'boolean' ? `success: ${item.success}` : '';
  const duration = formatDuration(item.durationMs ?? item.duration_ms);
  const title = [namespace, tool].filter(Boolean).join('.') || 'Tool call';
  return [title, args ? `args: ${args}` : '', [status ? `status: ${status}` : '', success, duration ? `duration: ${duration}` : ''].filter(Boolean).join('\n'), content]
    .filter(Boolean)
    .join('\n\n');
}

function formatWebSearchItem(item: Record<string, unknown>): string {
  const query = valueToText(item.query);
  const action = formatJsonDetail(item.action);
  return ['web_search', query ? `query: ${query}` : '', action ? `action: ${action}` : ''].filter(Boolean).join('\n');
}

function formatPlanSteps(plan: unknown): string {
  if (!Array.isArray(plan)) return '';
  return plan
    .map((step) => {
      if (!step || typeof step !== 'object') return '';
      const record = step as Record<string, unknown>;
      const text = valueToText(record.step ?? record.text);
      const status = valueToText(record.status);
      if (!text) return '';
      const normalizedStatus = status.trim().toLowerCase();
      const checkbox = ['completed', 'complete', 'done', 'finished'].includes(normalizedStatus) ? '[x]' : '[ ]';
      const statusLabel = status ? ` **${status}**` : '';
      return `- ${checkbox} ${text}${statusLabel}`;
    })
    .filter(Boolean)
    .join('\n');
}

function formatExecCommandBegin(payload: Record<string, unknown>): string {
  const command = valueToText(payload.command ?? payload.cmd ?? payload.text);
  const cwd = valueToText(payload.cwd);
  if (command && cwd) return `${command}\n\ncwd: ${cwd}`;
  return command || 'Shell command started.';
}

function formatMcpToolCallBegin(payload: Record<string, unknown>): string {
  const server = valueToText(payload.server ?? payload.server_name ?? payload.serverName);
  const tool = valueToText(payload.tool ?? payload.tool_name ?? payload.toolName ?? payload.name);
  const args = payload.arguments ?? payload.args ?? payload.input;
  const argText = typeof args === 'string'
    ? compactSingleLine(args, 220)
    : args && typeof args === 'object'
      ? compactSingleLine(JSON.stringify(args), 220)
      : '';
  const label = [server, tool].filter(Boolean).join('.');
  return compactSingleLine(`${label || 'MCP tool'}${argText ? ` ${argText}` : ''}`, 260);
}

function appendUniqueAgentMessage(agent: AgentInfo, text: string, type: string) {
  if (!text.trim()) return;
  const last = [...agent.messages].reverse().find((message) => message.role === 'agent' && message.type === type);
  if (last?.text.trim() === text.trim()) return;
  agent.messages.push({ role: 'agent', type, text, timestamp: Date.now() });
}

function normalizedToolItem(agent: AgentInfo, item: Record<string, unknown>): Record<string, unknown> | null {
  const type = String(item.type || '');
  if (type === 'userMessage') {
    return { ...item, type: 'userMessage', text: contentItemsToText(item.content) || valueToText(item.text) };
  }

  if (type === 'agentMessage') {
    const text = valueToText(item.text ?? item.message ?? item.content);
    const citation = item.memoryCitation ? `\n\nmemory: ${formatJsonDetail(item.memoryCitation)}` : '';
    return { ...item, type: 'agentMessage', text: `${text}${citation}`.trim() };
  }

  if (type === 'plan') {
    const planText = [valueToText(item.explanation), formatPlanSteps(item.plan ?? item.steps)]
      .filter(Boolean)
      .join('\n\n');
    return { ...item, type: 'plan', text: planText || valueToText(item.text) || 'Plan updated.' };
  }

  if (type === 'reasoning') {
    return { ...item, type: 'reasoning', text: formatReasoningItem(item) };
  }

  if (type === 'commandExecution') {
    const text = formatCommandExecutionItem(item);
    const completed = String(item.status || '').toLowerCase() !== 'inprogress';
    return {
      ...item,
      type: completed && valueToText(item.aggregatedOutput ?? item.output) ? 'commandOutput' : 'command',
      command: valueToText(item.command) || text,
      output: text,
      text,
    };
  }

  if (type === 'fileChange') {
    const text = formatFileChangeItem(item);
    return { ...item, type: 'fileChange', text, diff: text };
  }

  if (type === 'mcpToolCall') {
    const text = formatMcpToolCallItem(item);
    const completed = String(item.status || '').toLowerCase() !== 'inprogress';
    return { ...item, type: completed ? 'commandOutput' : 'command', command: text, output: text, text };
  }

  if (type === 'dynamicToolCall' || type === 'collabAgentToolCall') {
    const text = formatDynamicToolCallItem(item);
    const completed = String(item.status || '').toLowerCase() !== 'inprogress';
    if (isSubAgentToolItem(item)) {
      return { ...item, type: completed ? 'subAgentOutput' : 'subAgent', command: text, output: text, text };
    }
    return { ...item, type: completed ? 'commandOutput' : 'command', command: text, output: text, text };
  }

  if (type === 'webSearch') {
    const text = formatWebSearchItem(item);
    return { ...item, type: 'command', command: text, text };
  }

  if (type === 'imageView' || type === 'imageGeneration' || type === 'enteredReviewMode' || type === 'exitedReviewMode' || type === 'contextCompaction') {
    return { ...item, type: 'status', text: formatJsonDetail(item) || type };
  }

  if (type === 'function_call' || type === 'custom_tool_call' || type === 'web_search_call') {
    const id = itemIdentifier(item) || itemCallId(item);
    const name = itemToolName(item);
    const text = formatToolCallText(item);
    if (id) agent.toolCalls.set(itemCallId(item) || id, { name, text });
    return { ...item, id, type: 'command', command: text };
  }

  if (type === 'function_call_output' || type === 'custom_tool_call_output') {
    const id = itemIdentifier(item) || itemCallId(item);
    const call = agent.toolCalls.get(itemCallId(item));
    const output = extractToolOutputText(item);
    if (call && isPatchToolName(call.name)) {
      return { ...item, id, type: 'fileChange', text: output || call.text || 'Files changed by apply_patch.' };
    }
    return { ...item, id, type: 'commandOutput', output };
  }

  return null;
}

function normalizeThreadSummary(
  thread: Record<string, unknown>,
  projectRoot: string | null = null,
  pinnedThreadIds: Set<string> = codexPinnedThreadIds(),
  stoppedThreadIds: Set<string> = new Set(),
  projectlessThreadIds: Set<string> = new Set(),
): CodexThreadSummary {
  const id = typeof thread.id === 'string' ? thread.id : '';
  const cwd = usablePathString(thread.cwd);
  const projectless = Boolean(id && projectlessThreadIds.has(id));
  const createdAt = toTimestampMs(thread.createdAt);
  const runtime = readCodexSessionRuntimeState(thread.path);
  const stoppedOverride = Boolean(id && stoppedThreadIds.has(id));
  const updatedAt = toTimestampMs(thread.updatedAt, createdAt);
  const queuedFollowUps = codexQueuedFollowUpsForThread(id);
  const pinned = Boolean(id && pinnedThreadIds.has(id));
  return {
    id,
    name: typeof thread.name === 'string' ? thread.name : null,
    preview: typeof thread.preview === 'string' ? thread.preview : '',
    cwd,
    projectRoot: projectless ? null : (projectRoot || codexDesktopProjectRootForCwd(cwd)),
    projectless,
    path: typeof thread.path === 'string' ? thread.path : '',
    source: typeof thread.source === 'string' ? thread.source : null,
    createdAt,
    updatedAt: Math.max(updatedAt, runtime?.updatedAt || 0),
    status: stoppedOverride
      ? '可恢复'
      : runtime
        ? (runtime.running ? 'working' : '可恢复')
        : extractThreadStatus(thread.status),
    pinned,
    activityLabel: stoppedOverride ? null : (runtime?.running ? runtime.activityLabel : null),
    queuedFollowUpCount: queuedFollowUps.length,
    queuedFollowUps,
  };
}

function shouldShowActiveCodexThread(summary: CodexThreadSummary): boolean {
  return summary.pinned || isActiveThreadStatus(summary.status) || summary.queuedFollowUpCount > 0;
}

function byUpdatedDesc(left: CodexThreadSummary, right: CodexThreadSummary): number {
  return right.updatedAt - left.updatedAt;
}

function uniqueSummariesById(items: CodexThreadSummary[]): CodexThreadSummary[] {
  const seen = new Set<string>();
  const result: CodexThreadSummary[] = [];
  for (const item of items) {
    if (!item.id || seen.has(item.id)) continue;
    seen.add(item.id);
    result.push(item);
  }
  return result;
}

export class SessionOrchestrator {
  private agents = new Map<string, AgentInfo>();
  private broadcast: BroadcastFn;
  private capabilities: RuntimeCapabilities;
  private stoppedCodexThreads = new Map<string, number>();

  constructor(broadcast: BroadcastFn) {
    this.broadcast = broadcast;
    this.capabilities = this.detectRuntimeCapabilities();
    void this.restoreAgentsFromDisk();
  }

  getRuntimeCapabilities(): RuntimeCapabilities {
    return this.capabilities;
  }

  private detectRuntimeCapabilities(): RuntimeCapabilities {
    const baseUrlKeys = [
      'OPENAI_BASE_URL',
      'OPENAI_API_BASE',
      'OPENAI_API_BASE_URL',
      'CODEX_BASE_URL',
      'CODEX_API_BASE_URL',
    ];
    const forcedCompatible = parseBooleanEnv(process.env.EASY_CODEX_COMPATIBLE_API);
    const hasCustomBaseUrl = baseUrlKeys.some((key) => Boolean(process.env[key]?.trim()));
    if (forcedCompatible || hasCustomBaseUrl) {
      return {
        providerMode: 'compatible',
        supportsServiceTier: false,
        supportsReasoningEffort: false,
        reason: forcedCompatible ? 'EASY_CODEX_COMPATIBLE_API is enabled' : 'custom OpenAI-compatible API base URL detected',
      };
    }
    return {
      providerMode: 'official',
      supportsServiceTier: true,
      supportsReasoningEffort: true,
      reason: 'official Codex runtime',
    };
  }

  private markCompatibleMode(reason: string) {
    if (this.capabilities.providerMode === 'compatible') return;
    this.capabilities = {
      providerMode: 'compatible',
      supportsServiceTier: false,
      supportsReasoningEffort: false,
      reason,
    };
    console.warn(`[runtime] Switched to OpenAI-compatible API mode: ${reason}`);
  }

  private shouldRetryWithoutCodexOnlyParams(message = ''): boolean {
    const normalized = message.toLowerCase();
    return normalized.includes('eager agent not found')
      || normalized.includes('service tier')
      || normalized.includes('servicetier')
      || normalized.includes('unsupported parameter')
      || normalized.includes('unknown parameter');
  }

  private isReasoningEffortError(message = ''): boolean {
    return message.toLowerCase().includes('effort');
  }

  private findAgentByCodexThreadId(threadId: string, exceptAgentId?: string): AgentInfo | null {
    const normalized = threadId.trim();
    if (!normalized) return null;
    for (const agent of this.agents.values()) {
      if (agent.id === exceptAgentId) continue;
      if (agent.codexThreadId === normalized || agent.threadId === normalized) return agent;
    }
    return null;
  }

  async createAgent(
    name: string,
    model: string,
    cwd: string,
    approvalPolicy = 'never',
    systemPrompt = '',
    agentId?: string,
    options?: {
      serviceTier?: string;
      reasoningEffort?: string;
      codexThreadId?: string;
    },
  ): Promise<Omit<AgentInfo, 'process' | 'buffer' | 'pendingResponses' | 'pendingAgentRequests' | 'messageItemIds' | 'fileSnapshots' | 'toolCalls' | 'turnQueue' | 'queueDraining'>> {
    const id = agentId || uuid();
    const requestedCodexThreadId = options?.codexThreadId?.trim() || undefined;
    const existingThreadAgent = requestedCodexThreadId ? this.findAgentByCodexThreadId(requestedCodexThreadId) : null;
    if (existingThreadAgent) {
      console.log(`[agents] Reusing running agent ${existingThreadAgent.id} for Codex thread ${requestedCodexThreadId}`);
      return this.serialize(existingThreadAgent);
    }
    if (this.agents.has(id)) {
      throw new Error(`Agent ${id} is already running`);
    }
    console.log(`\n${BG_BLUE}${WHITE}${BOLD} NEW AGENT ${RESET} ${CYAN}${name}${RESET} (${DIM}${id.slice(0, 8)}${RESET})`);
    console.log(`  ${DIM}Model: ${model} | CWD: ${cwd}${RESET}`);

    const proc = spawnCodexAppServer(cwd);

    const agent: AgentInfo = {
      id,
      name,
      model,
      cwd,
      approvalPolicy,
      serviceTier: normalizeServiceTier(options?.serviceTier),
      reasoningEffort: options?.reasoningEffort || 'medium',
      systemPrompt,
      status: 'initializing',
      createdAt: Date.now(),
      updatedAt: Date.now(),
      threadId: null,
      currentTurnId: null,
      codexThreadId: requestedCodexThreadId || null,
      codexPath: null,
      source: null,
      messages: [],
      messageItemIds: new Map(),
      fileSnapshots: new Map(),
      toolCalls: new Map(),
      turnQueue: [],
      queueDraining: false,
      process: proc,
      buffer: '',
      pendingResponses: new Map(),
      pendingAgentRequests: new Map(),
    };

    this.agents.set(id, agent);
    this.persistAgentsToDisk();

    // Handle stdout (JSON-RPC messages)
    proc.stdout!.on('data', (chunk: Buffer) => {
      agent.buffer += chunk.toString();
      const lines = agent.buffer.split('\n');
      agent.buffer = lines.pop() || '';
      for (const line of lines) {
        if (line.trim()) this.handleMessage(agent, line.trim());
      }
    });

    // Handle stderr
    proc.stderr!.on('data', (chunk: Buffer) => {
      const text = chunk.toString().trim();
      if (text) {
        console.log(`  ${RED}[${name}] stderr:${RESET} ${text}`);
        this.broadcast(id, 'agent/stderr', { text, timestamp: Date.now() });
      }
    });

    proc.on('error', (err) => {
      console.error(`  ${RED}[${name}] Process error:${RESET}`, err);
      agent.status = 'error';
      agent.currentTurnId = null;
      this.rejectPending(agent, `codex app-server failed to start: ${err.message}`);
      this.agents.delete(id);
      this.persistAgentsToDisk();
      this.broadcast(id, 'turn/failed', { error: { message: err.message } });
    });

    proc.on('exit', (code) => {
      console.log(`  ${YELLOW}[${name}] Process exited with code ${code}${RESET}`);
      agent.status = 'stopped';
      this.markCodexThreadStopped(agent);
      this.rejectPending(agent, `codex app-server exited with code ${code}`);
      this.agents.delete(id);
      this.persistAgentsToDisk();
      this.broadcast(id, 'agent/stopped', { code, codexThreadId: agent.codexThreadId || agent.threadId });
      if (code !== 0 && code !== null) {
        notifyMobileClients({
          title: `${name} — Error`,
          body: `Agent crashed with exit code ${code}.`,
          subtitle: 'Tap to open',
          kind: 'agent_crashed',
          agentName: name,
          exitCode: code,
          agentId: id,
          categoryId: 'agent-error',
          priority: 'high',
          severity: 'error',
        }).catch(() => {});
      }
    });

    try {
      // Initialize handshake
      const initRes = await this.sendRequest(agent, codexInitializeCall('easycodex-relay', '1.0.0'));
      if (initRes.error) {
        throw new Error(`Initialize failed: ${initRes.error.message}`);
      }
      console.log(`  ${GREEN}[${name}] Initialized${RESET}`);

      // Send initialized notification
      this.write(agent, codexInitializedEvent());

      const threadRes = options?.codexThreadId
        ? await this.sendRequest(
          agent,
          codexThreadResumeCall(options.codexThreadId, {
            model,
            cwd,
            approvalPolicy,
              serviceTier: codexServiceTierParam(agent.serviceTier),
              includeServiceTier: this.capabilities.supportsServiceTier,
            }),
          )
        : await this.sendRequest(
          agent,
          codexThreadStartCall(
            model,
            cwd,
            approvalPolicy,
            codexServiceTierParam(agent.serviceTier),
            this.capabilities.supportsServiceTier,
          ),
        );

      if (threadRes.error) {
        if (this.shouldRetryWithoutCodexOnlyParams(threadRes.error.message)) {
          this.markCompatibleMode(threadRes.error.message);
          const retryRes = options?.codexThreadId
            ? await this.sendRequest(agent, codexThreadResumeCall(options.codexThreadId, {
              model,
              cwd,
              approvalPolicy,
              includeServiceTier: false,
            }))
            : await this.sendRequest(agent, codexThreadStartCall(model, cwd, approvalPolicy, undefined, false));
          if (retryRes.error) {
            throw new Error(`${options?.codexThreadId ? 'Thread resume' : 'Thread start'} failed: ${retryRes.error.message}`);
          }
          Object.assign(threadRes, retryRes);
        } else {
          throw new Error(`${options?.codexThreadId ? 'Thread resume' : 'Thread start'} failed: ${threadRes.error.message}`);
        }
      }
      const threadData = threadRes.result as Record<string, unknown>;
      const thread = threadData.thread as Record<string, unknown> | undefined;
      agent.threadId = (thread?.id as string) || (threadData.threadId as string) || null;
      agent.codexThreadId = agent.threadId;
      this.clearCodexThreadStopped(agent.threadId);
      if (agent.threadId) {
        const duplicate = this.findAgentByCodexThreadId(agent.threadId, agent.id);
        if (duplicate) {
          throw new Error(`Codex thread ${agent.threadId} is already attached to running agent ${duplicate.id}`);
        }
      }
      agent.codexPath = typeof thread?.path === 'string' ? thread.path : null;
      agent.source = typeof thread?.source === 'string' ? thread.source : null;
      agent.cwd = (typeof threadData.cwd === 'string' ? threadData.cwd : cwd) || cwd;
      agent.model = (typeof threadData.model === 'string' ? threadData.model : model) || model;
      agent.approvalPolicy = (typeof threadData.approvalPolicy === 'string' ? threadData.approvalPolicy : approvalPolicy) || approvalPolicy;
      agent.serviceTier = normalizeServiceTier(
        typeof threadData.serviceTier === 'string' ? threadData.serviceTier : agent.serviceTier,
      );
      agent.reasoningEffort = options?.reasoningEffort
        || (typeof threadData.reasoningEffort === 'string' ? threadData.reasoningEffort : agent.reasoningEffort)
        || 'medium';
      if (options?.codexThreadId) {
        agent.messages = turnsToMessages(thread);
        agent.messageItemIds.clear();
      }
      agent.status = 'ready';
      console.log(`  ${GREEN}[${name}] Thread started: ${agent.threadId?.slice(0, 8)}...${RESET}\n`);

      return this.serialize(agent);
    } catch (err) {
      this.agents.delete(id);
      this.persistAgentsToDisk();
      try { agent.process.kill(); } catch {}
      throw err;
    }
  }

  async sendMessage(agentId: string, text: string): Promise<void> {
    const agent = this.agents.get(agentId);
    if (!agent) throw new Error('Agent not found');
    if (!agent.threadId) throw new Error('No active thread');

    console.log(`\n${BLUE}${BOLD}[${agent.name}]${RESET} ${BOLD}User:${RESET} ${text.slice(0, 100)}${text.length > 100 ? '...' : ''}`);

    const timestamp = Date.now();
    agent.messages.push({ role: 'user', type: 'user', text, timestamp });
    if (agent.status === 'working' || agent.currentTurnId || agent.turnQueue.length > 0) {
      agent.turnQueue.push({ text, timestamp });
      this.broadcast(agent.id, 'turn/queued', {
        position: agent.turnQueue.length,
        queueLength: agent.turnQueue.length,
        timestamp,
      });
      return;
    }

    await this.startTurn(agent, text);
  }

  private async startTurn(agent: AgentInfo, text: string): Promise<void> {
    const threadId = agent.threadId;
    if (!threadId) throw new Error('No active thread');
    agent.status = 'working';
    const promptText = agent.systemPrompt?.trim()
      ? `${agent.systemPrompt.trim()}\n\n${text}`
      : text;
    const turnReq = codexTurnStartCall(threadId, promptText, {
      model: agent.model,
      effort: agent.reasoningEffort,
      serviceTier: codexServiceTierParam(agent.serviceTier),
      includeEffort: this.capabilities.supportsReasoningEffort,
      includeServiceTier: this.capabilities.supportsServiceTier,
      approvalPolicy: agent.approvalPolicy,
      cwd: agent.cwd,
    });
    try {
      let res = await this.sendRequest(agent, turnReq);
      if (res.error) {
        const message = res.error.message || 'Failed to start turn';
        if (this.shouldRetryWithoutCodexOnlyParams(message)) {
          this.markCompatibleMode(message);
          res = await this.sendRequest(agent, codexTurnStartCall(threadId, promptText, {
            model: agent.model,
            effort: this.isReasoningEffortError(message) ? undefined : agent.reasoningEffort,
            approvalPolicy: agent.approvalPolicy,
            cwd: agent.cwd,
            includeEffort: !this.isReasoningEffortError(message),
            includeServiceTier: false,
          }));
        }
      }
      if (res.error) {
        throw new Error(res.error.message || 'Failed to start turn');
      }
      if (res.result) {
        const turnData = res.result as Record<string, unknown>;
        const turn = turnData.turn as Record<string, unknown> | undefined;
        agent.currentTurnId = (turn?.id as string) || (turnData.turnId as string) || null;
      }
    } catch (err) {
      agent.status = 'error';
      agent.currentTurnId = null;
      throw err;
    }
  }

  private drainTurnQueue(agent: AgentInfo) {
    if (agent.queueDraining || agent.status === 'working' || agent.currentTurnId || agent.turnQueue.length === 0) return;
    const next = agent.turnQueue.shift();
    if (!next) return;
    agent.queueDraining = true;
    this.broadcast(agent.id, 'turn/dequeued', {
      queueLength: agent.turnQueue.length,
      timestamp: Date.now(),
    });
    this.startTurn(agent, next.text)
      .catch((err) => {
        agent.status = 'error';
        agent.currentTurnId = null;
        const message = err instanceof Error ? err.message : String(err);
        this.broadcast(agent.id, 'turn/failed', { error: { message } });
      })
      .finally(() => {
        agent.queueDraining = false;
        if (agent.status !== 'working' && !agent.currentTurnId) this.drainTurnQueue(agent);
      });
  }

  async interruptAgent(agentId: string): Promise<void> {
    const agent = this.agents.get(agentId);
    if (!agent) throw new Error('Agent not found');
    if (!agent.threadId || !agent.currentTurnId) return;

    console.log(`  ${YELLOW}[${agent.name}] Interrupting...${RESET}`);
    this.write(agent, codexTurnInterruptCall(agent.threadId, agent.currentTurnId));
  }

  respondAgentRequest(agentId: string, requestId: string, approved: boolean, reason = ''): void {
    const agent = this.agents.get(agentId);
    if (!agent) throw new Error('Agent not found');
    const request = agent.pendingAgentRequests.get(requestId);
    if (!request) throw new Error('Agent request not found or already handled');
    agent.pendingAgentRequests.delete(requestId);
    this.write(agent, JSON.stringify({
      id: request.id,
      result: {
        approved,
        decision: approved ? 'approved' : 'denied',
        reason,
      },
    }));
    this.broadcast(agent.id, 'agent/request_resolved', {
      requestId,
      approved,
      reason,
      timestamp: Date.now(),
    });
  }

  respondAgentUserInput(agentId: string, requestId: string, answers: Record<string, unknown>): void {
    const agent = this.agents.get(agentId);
    if (!agent) throw new Error('Agent not found');
    const request = agent.pendingAgentRequests.get(requestId);
    if (!request) throw new Error('Agent request not found or already handled');
    agent.pendingAgentRequests.delete(requestId);

    const formattedAnswers: Record<string, { answers: string[] }> = {};
    for (const [questionId, value] of Object.entries(answers || {})) {
      const values = Array.isArray(value) ? value : [value];
      const cleanAnswers = values
        .map((entry) => String(entry || '').trim())
        .filter(Boolean);
      if (cleanAnswers.length > 0) formattedAnswers[questionId] = { answers: cleanAnswers };
    }

    this.write(agent, JSON.stringify({
      id: request.id,
      result: {
        answers: formattedAnswers,
      },
    }));
    this.broadcast(agent.id, 'agent/request_resolved', {
      requestId,
      approved: true,
      reason: 'Answered from EasyCodex mobile',
      timestamp: Date.now(),
    });
  }

  stopAgent(agentId: string): void {
    const agent = this.agents.get(agentId);
    if (!agent) throw new Error('Agent not found');

    console.log(`  ${RED}[${agent.name}] Stopping...${RESET}`);
    this.markCodexThreadStopped(agent);
    agent.process.kill();
    agent.status = 'stopped';
    this.agents.delete(agentId);
    this.persistAgentsToDisk();
  }

  async archiveCodexThread(threadId: string, agentId?: string): Promise<void> {
    const normalizedThreadId = threadId.trim();
    if (!normalizedThreadId) throw new Error('threadId is required');

    const agent = (agentId && this.agents.get(agentId))
      || Array.from(this.agents.values()).find((entry) => entry.codexThreadId === normalizedThreadId || entry.threadId === normalizedThreadId);
    if (agent) {
      try { agent.process.kill(); } catch {}
      agent.status = 'stopped';
      this.agents.delete(agent.id);
      this.clearCodexThreadStopped(normalizedThreadId);
      this.persistAgentsToDisk();
    }

    const response = await this.sendOneOffRequest(codexThreadArchiveCall(normalizedThreadId));
    if (response.error) throw new Error(response.error.message);
  }

  updateModel(agentId: string, model: string): void {
    const agent = this.agents.get(agentId);
    if (!agent) throw new Error('Agent not found');
    agent.model = model;
    console.log(`  ${MAGENTA}[${agent.name}] Model updated to: ${model}${RESET}`);
    this.persistAgentsToDisk();
  }

  updateConfig(
    agentId: string,
    config: {
      model?: string;
      cwd?: string;
      approvalPolicy?: string;
      systemPrompt?: string;
      serviceTier?: string;
      reasoningEffort?: string;
    },
  ): void {
    const agent = this.agents.get(agentId);
    if (!agent) throw new Error('Agent not found');
    if (typeof config.model === 'string' && config.model.trim()) {
      agent.model = config.model.trim();
    }
    if (typeof config.cwd === 'string' && config.cwd.trim()) {
      agent.cwd = config.cwd.trim();
    }
    if (typeof config.approvalPolicy === 'string' && config.approvalPolicy.trim()) {
      agent.approvalPolicy = config.approvalPolicy.trim();
    }
    if (typeof config.serviceTier === 'string' && config.serviceTier.trim()) {
      agent.serviceTier = normalizeServiceTier(config.serviceTier);
    }
    if (typeof config.reasoningEffort === 'string' && config.reasoningEffort.trim()) {
      agent.reasoningEffort = config.reasoningEffort.trim();
    }
    if (typeof config.systemPrompt === 'string') {
      agent.systemPrompt = config.systemPrompt;
    }
    this.persistAgentsToDisk();
  }

  private async readPinnedCodexThreadSummary(threadId: string): Promise<Record<string, unknown> | null> {
    const response = await this.sendOneOffRequest(codexThreadReadCall(threadId, false));
    if (response.error) return null;
    const result = response.result as Record<string, unknown>;
    const thread = result?.thread as Record<string, unknown> | undefined;
    return thread && typeof thread === 'object' && !Array.isArray(thread) ? thread : null;
  }

  private markCodexThreadStopped(agent: AgentInfo): void {
    const threadId = (agent.codexThreadId || agent.threadId || '').trim();
    if (threadId) this.stoppedCodexThreads.set(threadId, Date.now());
  }

  private clearCodexThreadStopped(threadId: string | null | undefined): void {
    const normalized = (threadId || '').trim();
    if (normalized) this.stoppedCodexThreads.delete(normalized);
  }

  private stoppedCodexThreadIds(): Set<string> {
    const now = Date.now();
    for (const [threadId, stoppedAt] of this.stoppedCodexThreads) {
      if (now - stoppedAt > STOPPED_THREAD_OVERRIDE_TTL_MS) {
        this.stoppedCodexThreads.delete(threadId);
      }
    }
    return new Set(this.stoppedCodexThreads.keys());
  }

  async listCodexThreads(params: { limit?: number; cursor?: string; cwd?: string; all?: boolean; activeOnly?: boolean } = {}) {
    const cwdFilter = params.cwd ? path.resolve(params.cwd) : null;
    const visibleWorkspaceRoots = cwdFilter ? [] : codexDesktopVisibleWorkspaceRoots();
    const workspaceRootHints = cwdFilter ? new Map<string, string>() : codexThreadWorkspaceRootHints();
    const projectlessThreadIds = cwdFilter ? new Set<string>() : codexProjectlessThreadIds();
    const archivedThreadIds = codexArchivedThreadIds();
    const pinnedThreadIds = codexPinnedThreadIds();
    const stoppedThreadIds = this.stoppedCodexThreadIds();
    const allData: Record<string, unknown>[] = [];
    let nextCursor: string | null = typeof params.cursor === 'string' && params.cursor.trim() ? params.cursor : null;
    let page = 0;

    do {
      const response = await this.sendOneOffRequest(
        codexThreadListCall({ limit: params.limit, cursor: nextCursor || undefined, cwd: params.cwd }),
        params.cwd,
      );
      if (response.error) throw new Error(response.error.message);
      const result = response.result as Record<string, unknown>;
      const data = Array.isArray(result?.data) ? result.data : [];
      allData.push(...data.filter((entry): entry is Record<string, unknown> => !!entry && typeof entry === 'object'));
      nextCursor = typeof result?.nextCursor === 'string' && result.nextCursor.trim()
        ? result.nextCursor
        : null;
      page += 1;
    } while (params.all === true && nextCursor && page < 25);

    const threadById = new Map<string, Record<string, unknown>>();
    for (const entry of allData) {
      const id = usableString(entry.id);
      if (id && !threadById.has(id)) threadById.set(id, entry);
    }

    const pinnedThreads: CodexThreadSummary[] = [];
    for (const pinnedThreadId of pinnedThreadIds) {
      const thread = threadById.get(pinnedThreadId) || await this.readPinnedCodexThreadSummary(pinnedThreadId);
      if (!thread) continue;
      pinnedThreads.push(normalizeThreadSummary(
        thread,
        codexThreadProjectRoot(thread, visibleWorkspaceRoots, workspaceRootHints, projectlessThreadIds),
        pinnedThreadIds,
        stoppedThreadIds,
        projectlessThreadIds,
      ));
    }

    const projectThreads = allData
      .filter((entry) => shouldShowCodexThread(entry, archivedThreadIds))
      .filter((entry) => {
        const id = usableString(entry.id);
        if (id && pinnedThreadIds.has(id)) return false;
        return shouldShowCodexThreadInDesktopWorkspace(entry, visibleWorkspaceRoots, workspaceRootHints)
          || isCodexConversationThread(entry, workspaceRootHints, projectlessThreadIds);
      })
      .map((entry) => normalizeThreadSummary(
        entry,
        codexThreadProjectRoot(entry, visibleWorkspaceRoots, workspaceRootHints, projectlessThreadIds),
        pinnedThreadIds,
        stoppedThreadIds,
        projectlessThreadIds,
      ))
      .filter((entry) => !entry.projectRoot || !isNestedCodexWorktree(entry.cwd, entry.projectRoot))
      .filter((entry) => params.activeOnly !== true || shouldShowActiveCodexThread(entry))
      .filter((entry) => !cwdFilter || (entry.cwd.trim() && isWithinBase(cwdFilter, entry.cwd)))
      .sort(byUpdatedDesc);

    const data = uniqueSummariesById([...pinnedThreads, ...projectThreads]);
    return {
      projectRoots: visibleWorkspaceRoots,
      pinnedThreadIds: pinnedThreads.map((entry) => entry.id),
      projectThreadIds: projectThreads.map((entry) => entry.id),
      visibleThreadIds: data.map((entry) => entry.id),
      pinnedThreads,
      projectThreads,
      data,
      nextCursor,
    } satisfies CodexSidebarSnapshot;
  }

  private async readCodexThreadTurns(threadId: string): Promise<Record<string, unknown>[] | null> {
    const turns: Record<string, unknown>[] = [];
    let cursor: string | null = null;
    let page = 0;

    do {
      const response = await this.sendOneOffRequest(codexThreadTurnsListCall(threadId, {
        limit: MOBILE_THREAD_TURN_PAGE_LIMIT,
        cursor: cursor || undefined,
        sortDirection: 'asc',
      }));
      if (response.error) return null;
      const result = response.result as Record<string, unknown>;
      const data = Array.isArray(result?.data) ? result.data : [];
      turns.push(...data.filter((entry): entry is Record<string, unknown> => !!entry && typeof entry === 'object'));
      cursor = typeof result?.nextCursor === 'string' && result.nextCursor.trim()
        ? result.nextCursor
        : null;
      page += 1;
    } while (cursor && page < MOBILE_THREAD_TURN_MAX_PAGES);

    return turns;
  }

  async readCodexThread(threadId: string): Promise<CodexThreadDetail> {
    const pinnedThreadIds = codexPinnedThreadIds();
    if (codexArchivedThreadIds().has(threadId.trim()) && !pinnedThreadIds.has(threadId.trim())) {
      throw new Error('Thread is archived');
    }
    let response = await this.sendOneOffRequest(codexThreadReadCall(threadId, false));
    if (response.error) throw new Error(response.error.message);
    const result = response.result as Record<string, unknown>;
    let thread = result?.thread as Record<string, unknown> | undefined;
    if (!thread) throw new Error('Thread not found');
    let turns = await this.readCodexThreadTurns(threadId);
    if (turns === null) {
      response = await this.sendOneOffRequest(codexThreadReadCall(threadId, true));
      if (response.error) throw new Error(response.error.message);
      const fallbackResult = response.result as Record<string, unknown>;
      const fallbackThread = fallbackResult?.thread as Record<string, unknown> | undefined;
      if (!fallbackThread) throw new Error('Thread not found');
      thread = fallbackThread;
      turns = Array.isArray(fallbackThread.turns)
        ? fallbackThread.turns.filter((entry): entry is Record<string, unknown> => !!entry && typeof entry === 'object')
        : [];
    }
    const messageSource = {
      ...result,
      ...thread,
      entries: (thread as Record<string, unknown>).entries ?? result.entries ?? result.items ?? result.events,
      turns: turns.length > 0 ? turns : (thread as Record<string, unknown>).turns ?? result.turns,
    };
    const messages = mergeMessageHistory(turnsToMessages(messageSource), readSessionMessages(thread.path))
      .sort((a, b) => a.timestamp - b.timestamp)
      .map(summarizeMessageForMobile);
    const projectlessThreadIds = codexProjectlessThreadIds();
    const summary = normalizeThreadSummary(
      thread,
      codexThreadProjectRoot(thread, codexDesktopVisibleWorkspaceRoots(), codexThreadWorkspaceRootHints(), projectlessThreadIds),
      pinnedThreadIds,
      this.stoppedCodexThreadIds(),
      projectlessThreadIds,
    );
    const model = usableString(result?.model) || usableString(thread.model);
    const approvalPolicy = usableString(result?.approvalPolicy) || usableString(thread.approvalPolicy) || 'never';
    const serviceTier = normalizeServiceTier(usableString(result?.serviceTier) || usableString(thread.serviceTier));
    const reasoningEffort = usableString(result?.reasoningEffort) || usableString(thread.reasoningEffort) || 'medium';
    const status = summary.status;
    const queueLabel = summary.queuedFollowUpCount > 0 ? `已排队 ${summary.queuedFollowUpCount} 个后续任务` : null;
    const activityLabel = queueLabel || (status === 'working' ? (summary.activityLabel || inferThreadActivity(messages)) : null);
    return {
      ...summary,
      status,
      model,
      approvalPolicy,
      serviceTier,
      reasoningEffort,
      activityLabel,
      messages,
    };
  }

  async listCodexModels(includeHidden = true): Promise<CodexModelInfo[]> {
    const response = await this.sendOneOffRequest(codexModelListCall(includeHidden));
    if (response.error) throw new Error(response.error.message);
    const result = response.result as Record<string, unknown>;
    const data = Array.isArray(result?.data) ? result.data : [];
    return data
      .filter((entry): entry is Record<string, unknown> => !!entry && typeof entry === 'object')
      .map((entry) => {
        const id = usableString(entry.id);
        const model = usableString(entry.model) || id;
        return {
          id,
          model,
          displayName: usableString(entry.displayName) || model,
          description: usableString(entry.description),
          hidden: Boolean(entry.hidden),
          defaultReasoningEffort: usableString(entry.defaultReasoningEffort) || 'medium',
          supportedReasoningEfforts: Array.isArray(entry.supportedReasoningEfforts)
            ? entry.supportedReasoningEfforts
              .filter((item): item is Record<string, unknown> => !!item && typeof item === 'object')
              .map((item) => ({
                reasoningEffort: usableString(item.reasoningEffort),
                description: usableString(item.description),
              }))
            : [],
          additionalSpeedTiers: Array.isArray(entry.additionalSpeedTiers)
            ? Array.from(new Set(entry.additionalSpeedTiers.map(normalizeServiceTier).filter((tier) => tier !== DEFAULT_SERVICE_TIER)))
            : [],
          isDefault: Boolean(entry.isDefault),
          supportsPersonality: Boolean(entry.supportsPersonality),
          inputModalities: Array.isArray(entry.inputModalities)
            ? entry.inputModalities.map(usableString).filter(Boolean)
            : [],
        };
      })
      .filter((entry) => entry.model);
  }

  listAgents() {
    return Array.from(this.agents.values()).map((a) => this.serialize(a));
  }

  async listVisibleAgents() {
    const agents = this.listAgents();
    if (!agents.some((agent) => typeof agent.codexThreadId === 'string' && agent.codexThreadId.trim())) {
      return agents;
    }

    try {
      const visibleThreads = await this.listCodexThreads({ all: true });
      const visibleThreadIds = new Set(visibleThreads.data.map((thread) => thread.id).filter(Boolean));
      const visibleAgents = agents.filter((agent) => {
        if (!agent.codexThreadId?.trim()) return true;
        return visibleThreadIds.has(agent.codexThreadId);
      });
      const visibleAgentIds = new Set(visibleAgents.map((agent) => agent.id));
      const hiddenAgents = agents.filter((agent) => !visibleAgentIds.has(agent.id));
      for (const hiddenAgent of hiddenAgents) {
        const runningAgent = this.agents.get(hiddenAgent.id);
        if (!runningAgent) continue;
        console.log(`[agents] Hiding archived or non-visible Codex thread ${hiddenAgent.codexThreadId} (${hiddenAgent.name})`);
        try { runningAgent.process.kill(); } catch {}
        runningAgent.status = 'stopped';
        this.agents.delete(hiddenAgent.id);
      }
      if (hiddenAgents.length > 0) this.persistAgentsToDisk();
      return visibleAgents;
    } catch (err) {
      console.warn('[agents] Failed to filter agents by Codex thread state:', err);
      return agents;
    }
  }

  getAgent(agentId: string) {
    const agent = this.agents.get(agentId);
    return agent ? this.serialize(agent) : null;
  }

  private serialize(agent: AgentInfo) {
    const messageUpdatedAt = Math.max(0, ...agent.messages.map((message) => message.timestamp || 0));
    const pendingUpdatedAt = Math.max(0, ...Array.from(agent.pendingAgentRequests.values()).map((request) => request.timestamp || 0));
    const updatedAt = Math.max(agent.updatedAt, messageUpdatedAt, pendingUpdatedAt);
    return {
      id: agent.id,
      name: agent.name,
      model: agent.model,
      cwd: agent.cwd,
      projectRoot: codexDesktopProjectRootForCwd(agent.cwd),
      approvalPolicy: agent.approvalPolicy,
      serviceTier: agent.serviceTier,
      reasoningEffort: agent.reasoningEffort,
      systemPrompt: agent.systemPrompt,
      status: agent.status,
      createdAt: agent.createdAt,
      updatedAt,
      threadId: agent.threadId,
      currentTurnId: agent.currentTurnId,
      activity: this.activityLabel(agent),
      activityLabel: this.activityLabel(agent),
      codexThreadId: agent.codexThreadId,
      codexPath: agent.codexPath,
      source: agent.source,
      pendingRequests: Array.from(agent.pendingAgentRequests.values()).map((request) => ({
        requestId: String(request.id),
        method: request.method,
        params: request.params,
        text: valueToText(request.params.message ?? request.params.reason ?? request.params.command ?? request.params.tool ?? request.params.item) || request.method,
        timestamp: request.timestamp,
      })),
      messages: agent.messages.map(summarizeMessageForMobile),
    };
  }

  private activityLabel(agent: AgentInfo): string | null {
    if (agent.turnQueue.length > 0 && agent.status === 'working') {
      return `已排队 ${agent.turnQueue.length} 个后续任务`;
    }
    if (agent.status !== 'working' && !agent.currentTurnId) return null;
    const last = [...agent.messages].reverse().find((message) => message.role === 'agent');
    switch (last?.type) {
      case 'thinking':
        return '正在思考中，推理内容持续返回';
      case 'command':
        return '正在运行命令，等待执行结果';
      case 'command_output':
        return '正在读取命令输出';
      case 'file_change':
        return '正在修改文件，改动内容持续更新';
      case 'plan':
        return '正在规划步骤，准备继续执行';
      case 'agent':
        return '正在生成回复';
      default:
        return '正在运行中，AI 正在接手任务';
    }
  }

  private write(agent: AgentInfo, data: string) {
    agent.process.stdin!.write(data + '\n');
  }

  private rejectPending(agent: AgentInfo, message: string) {
    for (const resolve of agent.pendingResponses.values()) {
      resolve({ error: { code: -1, message } });
    }
    agent.pendingResponses.clear();
    agent.pendingAgentRequests.clear();
  }

  private appendItemDelta(agent: AgentInfo, itemId: string, delta: string, type: string) {
    if (!itemId || !delta) return;
    const existingIndex = agent.messageItemIds.get(itemId);
    if (typeof existingIndex === 'number' && agent.messages[existingIndex]) {
      const existing = agent.messages[existingIndex];
      existing.text = `${existing.text || ''}${delta}`;
      existing.type = type;
      return;
    }
    agent.messageItemIds.set(itemId, agent.messages.length);
    agent.messages.push({
      role: 'agent',
      type,
      text: delta,
      timestamp: Date.now(),
      _itemId: itemId,
    });
  }

  private finalizeItemMessage(agent: AgentInfo, itemId: string, text: string, type: string) {
    if (!itemId || !text) return;
    const existingIndex = agent.messageItemIds.get(itemId);
    if (typeof existingIndex === 'number' && agent.messages[existingIndex]) {
      const existing = agent.messages[existingIndex];
      existing.text = text;
      existing.type = type;
      return;
    }
    agent.messageItemIds.set(itemId, agent.messages.length);
    agent.messages.push({
      role: 'agent',
      type,
      text,
      timestamp: Date.now(),
      _itemId: itemId,
    });
  }

  private captureFileSnapshot(agent: AgentInfo, itemId: string, item: Record<string, unknown>) {
    const filePath = extractFilePathFromItem(item);
    if (!itemId || !filePath) return;
    const resolved = resolveAgentFile(agent, filePath);
    if (!resolved) return;
    agent.fileSnapshots.set(itemId, readTextSnapshot(resolved.absolute));
  }

  private buildFileChangeMessage(agent: AgentInfo, itemId: string, item: Record<string, unknown>): string {
    const filePath = extractFilePathFromItem(item);
    if (!filePath) return '';
    const resolved = resolveAgentFile(agent, filePath);
    if (!resolved) return filePath;

    const gitDiff = readGitDiff(agent, resolved.relative);
    if (gitDiff) return formatFileChangeText(resolved.relative, gitDiff);

    const before = agent.fileSnapshots.get(itemId);
    const after = readTextSnapshot(resolved.absolute);
    const snapshotDiff = buildSnapshotDiff(
      resolved.relative,
      before?.content ?? null,
      after.content,
    );
    return formatFileChangeText(resolved.relative, snapshotDiff);
  }

  private persistAgentsToDisk() {
    try {
      fs.mkdirSync(path.dirname(AGENTS_STATE_PATH), { recursive: true });
      const payload: PersistedAgent[] = Array.from(this.agents.values())
        .map((agent) => ({
          id: agent.id,
          name: agent.name,
          model: agent.model,
          cwd: agent.cwd,
          approvalPolicy: agent.approvalPolicy,
          serviceTier: agent.serviceTier,
          reasoningEffort: agent.reasoningEffort,
          systemPrompt: agent.systemPrompt,
          codexThreadId: agent.codexThreadId || undefined,
        }));
      fs.writeFileSync(AGENTS_STATE_PATH, JSON.stringify(payload, null, 2), 'utf8');
    } catch (err) {
      console.warn('[agents] Failed to persist agent state:', err);
    }
  }

  private async restoreAgentsFromDisk() {
    try {
      if (!fs.existsSync(AGENTS_STATE_PATH)) return;
      const raw = fs.readFileSync(AGENTS_STATE_PATH, 'utf8');
      const parsed = JSON.parse(raw) as PersistedAgent[];
      if (!Array.isArray(parsed) || parsed.length === 0) return;
      console.log(`${DIM}[agents] Restoring ${parsed.length} saved agent(s) from ${AGENTS_STATE_PATH}${RESET}`);
      for (const entry of parsed) {
        try {
          await this.createAgent(
            entry.name,
            entry.model,
            entry.cwd,
            entry.approvalPolicy || 'never',
            entry.systemPrompt || '',
            entry.id,
            {
              serviceTier: entry.serviceTier,
              reasoningEffort: entry.reasoningEffort,
              codexThreadId: entry.codexThreadId,
            },
          );
        } catch (err) {
          console.warn(`[agents] Failed to restore ${entry.name} (${entry.id}):`, err);
        }
      }
    } catch (err) {
      console.warn('[agents] Failed to restore saved agents:', err);
    }
  }

  private sendRequest(agent: AgentInfo, request: string): Promise<RpcReply> {
    return new Promise((resolve) => {
      const parsed = JSON.parse(request);
      const id = parsed.id;
      agent.pendingResponses.set(id, resolve);
      this.write(agent, request);

      // Timeout after 30s
      setTimeout(() => {
        if (agent.pendingResponses.has(id)) {
          agent.pendingResponses.delete(id);
          resolve({ error: { code: -1, message: 'Request timed out' } });
        }
      }, 30000);
    });
  }

  private sendOneOffRequest(request: string, cwd?: string): Promise<RpcReply> {
    return new Promise((resolve, reject) => {
      const proc = spawnCodexAppServer(cwd || process.cwd());

      let buffer = '';
      const pending = new Map<number | string, (res: RpcReply) => void>();
      let settled = false;

      const finish = (value: RpcReply | Error, isError = false) => {
        if (settled) return;
        settled = true;
        try { proc.kill(); } catch {}
        if (isError) reject(value);
        else resolve(value as RpcReply);
      };

      proc.stdout!.on('data', (chunk: Buffer) => {
        buffer += chunk.toString();
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';
        for (const line of lines) {
          if (!line.trim()) continue;
          const msg = parseRpcFrame(line.trim());
          if (!msg || !isRpcReply(msg)) continue;
          const cb = pending.get(msg.id!);
          if (cb) {
            pending.delete(msg.id!);
            cb(msg);
          }
        }
      });

      proc.stderr!.on('data', (chunk: Buffer) => {
        const text = chunk.toString().trim();
        if (text) {
          console.log(`  ${RED}[codex-meta] stderr:${RESET} ${text}`);
        }
      });

      proc.on('error', (err) => finish(err, true));
      proc.on('exit', (code) => {
        if (!settled && code !== 0) {
          finish(new Error(`codex app-server exited with code ${code}`), true);
        }
      });

      const send = (raw: string) =>
        new Promise<RpcReply>((resolveResponse) => {
          const parsed = JSON.parse(raw);
          const id = parsed.id;
          pending.set(id, resolveResponse);
          proc.stdin!.write(raw + '\n');
          setTimeout(() => {
            if (pending.has(id)) {
              pending.delete(id);
              resolveResponse({ error: { code: -1, message: 'Request timed out' } });
            }
          }, 30000);
        });

      (async () => {
        const initRes = await send(codexInitializeCall('easycodex-relay-meta', '1.0.0'));
        if (initRes.error) {
          finish(new Error(`Initialize failed: ${initRes.error.message}`), true);
          return;
        }
        proc.stdin!.write(codexInitializedEvent() + '\n');
        const result = await send(request);
        finish(result);
      })().catch((err) => finish(err instanceof Error ? err : new Error(String(err)), true));
    });
  }

  private ingestCompletedTurnItems(agent: AgentInfo, turn: Record<string, unknown> | undefined) {
    const items = Array.isArray(turn?.items) ? turn.items : [];
    const turnId = typeof turn?.id === 'string' ? turn.id : agent.currentTurnId || '';
    for (const rawItem of items) {
      if (!rawItem || typeof rawItem !== 'object') continue;
      const normalized = normalizedToolItem(agent, rawItem as Record<string, unknown>);
      if (!normalized) continue;
      const itemId = itemIdentifier(normalized);
      const type = String(normalized.type || '');
      const text = valueToText(normalized.text ?? normalized.output ?? normalized.command);
      if (!itemId || !text || type === 'userMessage') continue;

      if (type === 'agentMessage') {
        this.finalizeItemMessage(agent, itemId, text, 'agent');
      } else if (type === 'reasoning') {
        this.finalizeItemMessage(agent, itemId, text, 'thinking');
      } else if (type === 'plan') {
        this.finalizeItemMessage(agent, itemId, text, 'plan');
      } else if (type === 'command') {
        this.finalizeItemMessage(agent, itemId, text, 'command');
      } else if (type === 'commandOutput') {
        this.finalizeItemMessage(agent, itemId, text, 'command_output');
      } else if (type === 'subAgent' || type === 'subAgentOutput') {
        this.finalizeItemMessage(agent, itemId, text, 'sub_agent');
      } else if (type === 'fileChange') {
        this.finalizeItemMessage(agent, itemId, text, 'file_change');
      } else if (type === 'status') {
        this.finalizeItemMessage(agent, itemId, text, 'status');
      } else {
        this.finalizeItemMessage(agent, itemId, text, 'agent');
      }

      this.broadcast(agent.id, 'item/completed', {
        threadId: agent.threadId,
        turnId,
        item: normalized,
      });
    }
  }

  private handleMessage(agent: AgentInfo, line: string) {
    const msg = parseRpcFrame(line);
    if (!msg) return;

    if (isRpcReply(msg)) {
      const cb = agent.pendingResponses.get(msg.id!);
      if (cb) {
        agent.pendingResponses.delete(msg.id!);
        cb(msg);
        return;
      }
      if ('method' in msg && typeof msg.method === 'string') {
        const request = msg as RpcReply & { method: string; params?: Record<string, unknown> };
        this.handleAgentRequest(agent, msg.id!, request.method, request.params || {});
      }
      return;
    }

    if (isRpcEvent(msg)) {
      this.handleNotification(agent, msg.method, msg.params);
    }
  }

  private handleAgentRequest(agent: AgentInfo, id: number | string, method: string, params: Record<string, unknown>) {
    const requestId = String(id);
    agent.pendingAgentRequests.set(requestId, {
      id,
      method,
      params,
      timestamp: Date.now(),
    });
    const text = method === USER_INPUT_REQUEST_METHOD
      ? userInputRequestText(params)
      : valueToText(params.message ?? params.reason ?? params.command ?? params.tool ?? params.item) || method;
    this.finalizeItemMessage(agent, `request_${requestId}`, text, 'status');
    this.broadcast(agent.id, 'agent/requested', {
      requestId,
      method,
      params,
      text,
      timestamp: Date.now(),
    });
    notifyMobileClients({
      title: method === USER_INPUT_REQUEST_METHOD ? `${agent.name} has a question` : `${agent.name} needs confirmation`,
      body: text,
      subtitle: 'Tap to open',
      kind: method === USER_INPUT_REQUEST_METHOD ? 'agent_question' : 'agent_request',
      agentName: agent.name,
      agentId: agent.id,
      categoryId: 'agent-alert',
      priority: 'high',
      severity: 'info',
    }).catch(() => {});
  }

  private handleNotification(agent: AgentInfo, method: string, params: Record<string, unknown>) {
    const tag = `${CYAN}[${agent.name}]${RESET}`;
    const p = params || {};

    try {
      switch (method) {
        case 'turn/started': {
          agent.status = 'working';
          const turn = p.turn as Record<string, unknown> | undefined;
          agent.currentTurnId = (turn?.id as string) || (p.turnId as string) || agent.currentTurnId;
          console.log(`${tag} ${BOLD}Turn started${RESET}`);
          this.broadcast(agent.id, 'turn/started', p);
          notifyMobileClients({
            title: `${agent.name} is working`,
            body: 'Agent started processing your request.',
            subtitle: 'Tap to open',
            kind: 'turn_started',
            agentName: agent.name,
            agentId: agent.id,
            categoryId: 'agent-working',
            priority: 'default',
            severity: 'info',
          }).catch(() => {});
          break;
        }

        case 'turn/completed': {
          agent.status = 'ready';
          const completedTurn = p.turn as Record<string, unknown> | undefined;
          this.ingestCompletedTurnItems(agent, completedTurn);
          agent.currentTurnId = null;
          console.log(`${tag} ${GREEN}${BOLD}Turn completed${RESET}`);
          this.broadcast(agent.id, 'turn/completed', p);
          // Send mobile notification so it arrives even if app is backgrounded/killed
          // Include last agent message as preview
          const lastAgentMsg = [...agent.messages].reverse().find(
            (m) => m.role === 'agent' && m.type === 'agent',
          );
          const preview = lastAgentMsg?.text
            ? lastAgentMsg.text.slice(0, 120).replace(/\s+/g, ' ').trim()
            : '';
          notifyMobileClients({
            title: `${agent.name} finished`,
            body: preview || `${agent.name} completed the task.`,
            subtitle: 'Hold to reply',
            kind: 'turn_completed',
            agentName: agent.name,
            preview,
            agentId: agent.id,
            categoryId: 'thread-reply',
            severity: 'info',
          }).catch(() => {});
          this.drainTurnQueue(agent);
          break;
        }

        case 'turn/failed': {
          agent.status = 'error';
          agent.currentTurnId = null;
          const errorMessage = typeof (p.error as Record<string, unknown> | undefined)?.message === 'string'
            ? String((p.error as Record<string, unknown>).message)
            : 'Agent turn failed.';
          this.broadcast(agent.id, 'turn/failed', p);
          notifyMobileClients({
            title: `${agent.name} — Error`,
            body: errorMessage,
            subtitle: 'Tap to open',
            kind: 'turn_failed',
            agentName: agent.name,
            errorMessage,
            agentId: agent.id,
            categoryId: 'agent-error',
            priority: 'high',
            severity: 'error',
          }).catch(() => {});
          break;
        }

        case 'item/started': {
          const item = p.item as Record<string, unknown> | undefined;
          if (item) {
            const normalizedTool = normalizedToolItem(agent, item);
            if (normalizedTool) p.item = normalizedTool;
            const activeItem = (p.item as Record<string, unknown> | undefined) || item;
            const type = activeItem.type as string || '';
            if (type === 'reasoning') {
              console.log(`${tag} ${YELLOW}Thinking...${RESET}`);
            } else if (type === 'command' || type === 'localShellCommand') {
              const cmd = (activeItem.command || activeItem.text || '') as string;
              console.log(`${tag} ${MAGENTA}$ ${cmd}${RESET}`);
            } else if (type === 'fileChange' || type === 'codeChange') {
              const path = (activeItem.path || activeItem.file || '') as string;
              console.log(`${tag} ${BLUE}File: ${path}${RESET}`);
              this.captureFileSnapshot(agent, (activeItem.id as string) || '', activeItem);
              notifyMobileClients({
                title: `${agent.name} — File changed`,
                body: path || 'Agent modified a file.',
                subtitle: 'Hold to follow up',
                kind: 'file_changed',
                agentName: agent.name,
                path,
                agentId: agent.id,
                categoryId: 'file-change',
                priority: 'default',
                severity: 'info',
              }).catch(() => {});
            }
          }
          this.broadcast(agent.id, 'item/started', p);
          break;
        }

        case 'item/agentMessage/delta': {
          const delta = p.delta as string || '';
          const itemId = (p.itemId as string) || ((p.item as Record<string, unknown> | undefined)?.id as string) || '';
          this.appendItemDelta(agent, itemId, delta, 'agent');
          if (delta) process.stdout.write(`${DIM}${delta}${RESET}`);
          this.broadcast(agent.id, 'item/agentMessage/delta', p);
          break;
        }

        case 'item/reasoning/delta': {
          const delta = p.delta as string || '';
          const itemId = (p.itemId as string) || ((p.item as Record<string, unknown> | undefined)?.id as string) || '';
          this.appendItemDelta(agent, itemId, delta, 'thinking');
          if (delta) process.stdout.write(`${YELLOW}${DIM}${delta}${RESET}`);
          this.broadcast(agent.id, 'item/reasoning/delta', p);
          break;
        }

        case 'item/reasoning/textDelta':
        case 'item/reasoning/summaryTextDelta': {
          const delta = p.delta as string || '';
          const itemId = (p.itemId as string) || '';
          this.appendItemDelta(agent, itemId, delta, 'thinking');
          if (delta) process.stdout.write(`${YELLOW}${DIM}${delta}${RESET}`);
          this.broadcast(agent.id, 'item/reasoning/delta', { ...p, itemId, delta });
          break;
        }

        case 'item/reasoning/summaryPartAdded': {
          const itemId = (p.itemId as string) || '';
          const text = valueToText(p.text ?? p.summary ?? p.part ?? p.content);
          if (text) this.appendItemDelta(agent, itemId, text, 'thinking');
          this.broadcast(agent.id, 'item/reasoning/delta', { ...p, itemId, delta: text });
          break;
        }

        case 'item/commandOutput/delta': {
          const delta = p.delta as string || '';
          const itemId = (p.itemId as string) || ((p.item as Record<string, unknown> | undefined)?.id as string) || '';
          this.appendItemDelta(agent, itemId, delta, 'command_output');
          if (delta) process.stdout.write(`${GREEN}${DIM}${delta}${RESET}`);
          this.broadcast(agent.id, 'item/commandOutput/delta', p);
          break;
        }

        case 'item/commandExecution/outputDelta': {
          const delta = p.delta as string || '';
          const itemId = (p.itemId as string) || '';
          this.appendItemDelta(agent, itemId, delta, 'command_output');
          if (delta) process.stdout.write(`${GREEN}${DIM}${delta}${RESET}`);
          this.broadcast(agent.id, 'item/commandOutput/delta', { ...p, itemId, delta });
          break;
        }

        case 'item/fileChange/outputDelta': {
          const delta = p.delta as string || '';
          const itemId = (p.itemId as string) || '';
          this.appendItemDelta(agent, itemId, delta, 'file_change');
          this.broadcast(agent.id, 'item/fileChange/delta', { ...p, itemId, delta });
          break;
        }

        case 'item/fileChange/patchUpdated': {
          const itemId = (p.itemId as string) || '';
          const text = formatFileUpdateChanges(p.changes);
          if (text) this.finalizeItemMessage(agent, itemId, text, 'file_change');
          this.broadcast(agent.id, 'item/completed', {
            ...p,
            item: { id: itemId || `file_${Date.now()}`, type: 'fileChange', text, changes: p.changes },
          });
          break;
        }

        case 'item/plan/delta': {
          const delta = p.delta as string || '';
          const itemId = (p.itemId as string) || '';
          this.appendItemDelta(agent, itemId, delta, 'plan');
          this.broadcast(agent.id, 'item/plan/delta', { ...p, itemId, delta });
          break;
        }

        case 'item/mcpToolCall/progress': {
          const message = valueToText(p.message);
          const itemId = (p.itemId as string) || '';
          if (message) this.appendItemDelta(agent, itemId, `${message}\n`, 'command_output');
          this.broadcast(agent.id, 'item/commandOutput/delta', { ...p, itemId, delta: message ? `${message}\n` : '' });
          break;
        }

        case 'item/completed': {
          const item = p.item as Record<string, unknown> | undefined;
          if (item) {
            const normalizedTool = normalizedToolItem(agent, item);
            if (normalizedTool) p.item = normalizedTool;
            const activeItem = (p.item as Record<string, unknown> | undefined) || item;
            const type = activeItem.type as string || '';
            const itemId = itemIdentifier(activeItem);
            if (type === 'agentMessage') {
              this.finalizeItemMessage(agent, itemId, (activeItem.text as string) || '', 'agent');
              console.log(`\n${tag} ${GREEN}Message complete${RESET}`);
            } else if (type === 'commandOutput' || type === 'localShellOutput') {
              this.finalizeItemMessage(agent, itemId, (activeItem.output as string) || (activeItem.text as string) || '', 'command_output');
              console.log(`\n${tag} ${GREEN}Output complete${RESET}`);
            } else if (type === 'subAgent' || type === 'subAgentOutput') {
              this.finalizeItemMessage(agent, itemId, (activeItem.output as string) || (activeItem.text as string) || '', 'sub_agent');
            } else if (type === 'reasoning') {
              this.finalizeItemMessage(agent, itemId, (activeItem.text as string) || '', 'thinking');
            } else if (type === 'command' || type === 'localShellCommand') {
              this.finalizeItemMessage(agent, itemId, (activeItem.command as string) || (activeItem.text as string) || '', 'command');
            } else if (type === 'fileChange' || type === 'codeChange') {
              const text = (activeItem.text as string)
                || this.buildFileChangeMessage(agent, itemId, activeItem)
                || (activeItem.path as string)
                || (activeItem.file as string)
                || '';
              this.finalizeItemMessage(agent, itemId, text, 'file_change');
              p.item = {
                ...activeItem,
                text,
                path: extractFilePathFromItem(activeItem) || activeItem.path || activeItem.file,
              };
              agent.fileSnapshots.delete(itemId);
            }
          }
          this.broadcast(agent.id, 'item/completed', p);
          break;
        }

        case 'rawResponseItem/completed': {
          const item = ((p.item && typeof p.item === 'object') ? p.item : p) as Record<string, unknown>;
          const normalizedTool = normalizedToolItem(agent, item);
          if (normalizedTool) {
            const itemId = itemIdentifier(normalizedTool);
            const type = String(normalizedTool.type || '');
            if (type === 'command') {
              this.finalizeItemMessage(agent, itemId, String(normalizedTool.command || normalizedTool.text || ''), 'command');
            } else if (type === 'commandOutput') {
              this.finalizeItemMessage(agent, itemId, String(normalizedTool.output || normalizedTool.text || ''), 'command_output');
            } else if (type === 'subAgent' || type === 'subAgentOutput') {
              this.finalizeItemMessage(agent, itemId, String(normalizedTool.output || normalizedTool.text || ''), 'sub_agent');
            } else if (type === 'fileChange') {
              this.finalizeItemMessage(agent, itemId, String(normalizedTool.text || ''), 'file_change');
            }
            this.broadcast(agent.id, 'item/completed', { item: normalizedTool });
          }
          break;
        }

        case 'turn/diff/updated': {
          const turnId = (p.turnId as string) || agent.currentTurnId || '';
          const text = valueToText(p.diff);
          if (text) {
            const itemId = turnId ? `turn_diff_${turnId}` : `turn_diff_${Date.now()}`;
            this.finalizeItemMessage(agent, itemId, text, 'file_change');
            this.broadcast(agent.id, 'item/completed', {
              item: { id: itemId, type: 'fileChange', text },
            });
          }
          break;
        }

        case 'turn/plan/updated': {
          const turnId = (p.turnId as string) || agent.currentTurnId || '';
          const text = [valueToText(p.explanation), formatPlanSteps(p.plan)].filter(Boolean).join('\n\n');
          if (text) {
            const itemId = turnId ? `plan_${turnId}` : `plan_${Date.now()}`;
            this.finalizeItemMessage(agent, itemId, text, 'plan');
            this.broadcast(agent.id, 'item/completed', {
              item: { id: itemId, type: 'plan', text },
            });
          }
          break;
        }

        case 'thread/tokenUsage/updated': {
          this.broadcast(agent.id, 'thread/tokenUsage/updated', p);
          break;
        }

        case 'response_item': {
          const item = ((p.payload && typeof p.payload === 'object') ? p.payload : p) as Record<string, unknown>;
          const normalizedTool = normalizedToolItem(agent, item);
          if (normalizedTool) {
            const itemId = itemIdentifier(normalizedTool);
            const type = String(normalizedTool.type || '');
            if (type === 'command') {
              this.finalizeItemMessage(agent, itemId, String(normalizedTool.command || normalizedTool.text || ''), 'command');
            } else if (type === 'commandOutput') {
              this.finalizeItemMessage(agent, itemId, String(normalizedTool.output || normalizedTool.text || ''), 'command_output');
            } else if (type === 'subAgent' || type === 'subAgentOutput') {
              this.finalizeItemMessage(agent, itemId, String(normalizedTool.output || normalizedTool.text || ''), 'sub_agent');
            } else if (type === 'fileChange') {
              this.finalizeItemMessage(agent, itemId, String(normalizedTool.text || ''), 'file_change');
            }
            this.broadcast(agent.id, 'item/completed', { item: normalizedTool });
            break;
          }

          if (item.type === 'message') {
            const role = item.role === 'user' ? 'user' : 'agent';
            const text = valueToText(item.content ?? item.message ?? item.text);
            if (text) {
              agent.messages.push({ role, type: role === 'user' ? 'user' : 'agent', text, timestamp: Date.now() });
              this.broadcast(agent.id, 'item/completed', {
                item: { id: itemIdentifier(item) || `message_${Date.now()}`, type: role === 'user' ? 'userMessage' : 'agentMessage', text },
              });
            }
          }
          break;
        }

        case 'event_msg': {
          const payload = ((p.payload && typeof p.payload === 'object') ? p.payload : p) as Record<string, unknown>;
          const type = String(payload.type || '');
          if (type === 'task_started') {
            agent.status = 'working';
            agent.currentTurnId = (payload.turn_id as string) || (payload.turnId as string) || agent.currentTurnId;
            this.broadcast(agent.id, 'turn/started', {
              turnId: agent.currentTurnId,
              event: payload,
            });
            this.broadcast(agent.id, 'item/started', {
              item: {
                id: agent.currentTurnId || `thinking_${Date.now()}`,
                type: 'reasoning',
                text: 'Thinking...',
              },
            });
            break;
          }

          if (type === 'exec_command_begin') {
            const itemId = itemCallId(payload) || `exec_${Date.now()}`;
            const command = formatExecCommandBegin(payload);
            this.finalizeItemMessage(agent, itemId, command, 'command');
            this.broadcast(agent.id, 'item/started', {
              item: { id: itemId, type: 'command', command },
            });
            break;
          }

          if (type === 'mcp_tool_call_begin') {
            const itemId = itemCallId(payload) || `mcp_${Date.now()}`;
            const command = formatMcpToolCallBegin(payload);
            this.finalizeItemMessage(agent, itemId, command, 'command');
            this.broadcast(agent.id, 'item/started', {
              item: { id: itemId, type: 'command', command },
            });
            break;
          }

          if (type === 'patch_apply_begin') {
            const itemId = itemCallId(payload) || `patch_${Date.now()}`;
            this.finalizeItemMessage(agent, itemId, 'apply_patch', 'command');
            this.broadcast(agent.id, 'item/started', {
              item: { id: itemId, type: 'command', command: 'apply_patch' },
            });
            break;
          }

          if (type === 'patch_apply_end') {
            const itemId = itemCallId(payload) || `patch_${Date.now()}`;
            const text = formatPatchApplyEnd(payload);
            this.finalizeItemMessage(agent, itemId, text, 'file_change');
            this.broadcast(agent.id, 'item/completed', {
              item: { id: itemId, type: 'fileChange', text },
            });
            break;
          }

          if (type === 'exec_command_end' || type === 'mcp_tool_call_end') {
            const itemId = itemCallId(payload) || `output_${Date.now()}`;
            const output = valueToText(payload.aggregated_output ?? payload.output ?? payload.stdout ?? payload.stderr);
            if (output) {
              this.finalizeItemMessage(agent, itemId, output, 'command_output');
              this.broadcast(agent.id, 'item/completed', {
                item: { id: itemId, type: 'commandOutput', output },
              });
            }
            break;
          }

          if (type === 'error') {
            agent.status = 'error';
            const text = valueToText(payload.message ?? payload.error) || 'Agent turn failed.';
            appendUniqueAgentMessage(agent, text, 'status');
            this.broadcast(agent.id, 'item/completed', {
              item: { id: itemCallId(payload) || `error_${Date.now()}`, type: 'status', text },
            });
            this.broadcast(agent.id, 'turn/failed', { error: { message: text } });
            break;
          }

          if (type === 'turn_aborted') {
            agent.status = 'ready';
            agent.currentTurnId = null;
            const reason = valueToText(payload.reason);
            const text = reason ? `Turn aborted: ${reason}` : 'Turn aborted.';
            appendUniqueAgentMessage(agent, text, 'status');
            this.broadcast(agent.id, 'item/completed', {
              item: { id: itemCallId(payload) || `aborted_${Date.now()}`, type: 'status', text },
            });
            this.broadcast(agent.id, 'turn/completed', { event: payload });
            this.drainTurnQueue(agent);
            break;
          }

          if (type === 'task_complete') {
            agent.status = 'ready';
            agent.currentTurnId = null;
            const text = valueToText(payload.last_agent_message);
            if (text) {
              appendUniqueAgentMessage(agent, text, 'agent');
              this.broadcast(agent.id, 'item/completed', {
                item: { id: itemCallId(payload) || `agent_${Date.now()}`, type: 'agentMessage', text },
              });
            }
            this.broadcast(agent.id, 'turn/completed', { event: payload });
            this.drainTurnQueue(agent);
            break;
          }

          if (type === 'agent_message') {
            const text = valueToText(payload.message ?? payload.text);
            if (text) {
              const itemId = `agent_${Date.now()}`;
              this.finalizeItemMessage(agent, itemId, text, 'agent');
              this.broadcast(agent.id, 'item/completed', {
                item: { id: itemId, type: 'agentMessage', text },
              });
            }
            break;
          }

          this.broadcast(agent.id, method, p);
          break;
        }

        default: {
          // Forward any other notifications
          console.log(`${tag} ${DIM}${method}${RESET}`);
          this.broadcast(agent.id, method, p);
          break;
        }
      }
    } catch (err) {
      console.error(`${RED}[${agent.name}] Error handling ${method}:${RESET}`, err);
      // Still try to broadcast even if logging failed
      try { this.broadcast(agent.id, method, p); } catch {}
    }
  }
}
